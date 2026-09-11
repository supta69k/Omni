package com.example.omni.ui.feed

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Story
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.MediaRepository
import com.example.omni.data.repo.PostAuthor
import com.example.omni.data.repo.StoryRepository
import com.example.omni.data.repo.storyExpiryFromNow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One tile of the strip, flattened: who, their newest story's cover, and whether the viewer has
 * already seen this author's current story. Unseen authors draw the accent ring; seen ones draw
 * grey — the convention every stories UI has taught since 2016.
 */
data class StoryTileState(
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val authorVerified: Boolean,
    /** The newest story's image — the tile's cover, what the author last posted. */
    val coverUrl: String?,
    val storyCount: Int,
    val unseen: Boolean,
)

/**
 * Everything the strip and the viewer render.
 *
 * @property tiles the strip, grouped by author (one tile per author, newest story first) with the
 *   "your story" tile always first when the viewer has one.
 * @property viewerIndex the tile the full-screen viewer is on, or `null` when it is closed. An
 *   index into [tiles], not a story id, because next/previous move *between authors* — the FB/IG
 *   shape — and the stories within one author's tile are paged inside the viewer.
 * @property mine whether the viewer's own story exists — the strip's first tile.
 */
data class StoriesUiState(
    val tiles: List<StoryTileState> = emptyList(),
    val viewerIndex: Int? = null,
    /** The stories of the tile the viewer is on, oldest first — the playback order. */
    val viewerStories: List<Story> = emptyList(),
    /** Which of [viewerStories] is on screen. */
    val viewerStoryIndex: Int = 0,
    val mine: Boolean = false,
)

/**
 * The story strip and the full-screen viewer (BACKEND_PLAN's social surface).
 *
 * Kept apart from [FeedViewModel] deliberately: the strip is one listener over `stories` while the
 * feed pages `posts`, and mixing them would recompose the feed for every story arrival. The two
 * screens read like one surface to the user and stay two ViewModels to the code.
 *
 * Seen-state lives in memory only. Remembering it across installs would be a per-user document of
 * story ids that grows without bound and buys nothing for a term project: a reinstall showing
 * yesterday's stories as unseen is the honest reading anyway.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoriesViewModel(
    private val authRepository: AuthRepository,
    private val storyRepository: StoryRepository,
    private val mediaRepository: MediaRepository,
    private val followRepository: FollowRepository,
) : ViewModel() {

    /** The author ids whose stories this viewer has already opened this session. */
    private val seen = MutableStateFlow<Set<String>>(emptySet())

    private val viewerIndex = MutableStateFlow<Int?>(null)
    private val viewerStoryIndex = MutableStateFlow(0)

    /**
     * Who is signed in, as a flow rather than a getter.
     *
     * Both places below ask "is this tile mine", and both used to answer it by reading
     * `authRepository.currentUid` from inside a `combine` lambda. A lambda's imperative read is not
     * a dependency: the combine re-runs when the stories or the seen set change, and *not* when the
     * session does, so after a sign-out the strip kept calling the previous account's tile "Your
     * story" — with its delete affordance — until something else happened to re-trigger it. As a
     * flow it is an input, and changing it rebuilds the strip.
     */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * Whose stories this viewer is allowed to see on the rail: the people I follow, plus me.
     *
     * The chain the brief draws — *current user → following relationships → followed user ids →
     * their active stories → the rail* — is this flow followed by the filter in [grouped]. It is the
     * **same** `users/{me}/following` listener the feed's Follow pill and the Following tab read, so
     * following someone from a post row puts their story on the rail in the same frame the pill
     * turns; three screens cannot disagree about one relationship when they are reading one listener.
     *
     * Keyed on [uid] through [flatMapLatest] rather than combined with it: a sign-out must *cancel*
     * the previous account's following listener, not leave its last value retained beside a new uid —
     * which is exactly how the previous account's stories would survive a switch.
     *
     * My own uid is in the set deliberately. My story belongs on the rail (the strip draws it as the
     * share tile and skips it in the tile loop, so it appears once, not twice), and a story of mine
     * is not something I follow myself into.
     */
    private val visibleAuthors: Flow<Set<String>> = uid.flatMapLatest { me ->
        if (me == null) flowOf(emptySet())
        else followRepository.observeFollowing(me)
            .map { following -> following + me }
            // A following listener that a rules change or a dropped connection killed must not take
            // the whole rail down with it; an empty set is "nobody's stories yet", which is the
            // honest reading and what a brand-new account sees anyway.
            .catch { cause ->
                Log.w("Omni", "The following set could not be read", cause)
                emit(setOf(me))
            }
    }

    /**
     * The stories this viewer may see, grouped into one tile per author, newest author first.
     *
     * The filter is the whole of §4: `observeStories()` returns everyone's live stories, and the rail
     * shows only the authors in [visibleAuthors]. It is done here rather than in a Firestore query
     * because `whereIn` caps at 30 values — a viewer following more than thirty people would need the
     * query chunked into a listener per chunk, which is more listeners and more reads than filtering
     * one capped stream. The stream is already bounded by the repository's own limit and by the
     * 24-hour expiry, so this is a filter over a short list, not a download of the collection.
     */
    private val grouped: Flow<List<Pair<List<Story>, Boolean>>> =
        combine(
            storyRepository.observeStories(),
            seen,
            uid,
            visibleAuthors,
        ) { stories, seen, mine, visible ->
            stories
                .filter { it.authorId in visible }
                .groupBy { it.authorId }
                .map { (authorId, authorStories) ->
                    // Playback order inside a tile is oldest → newest; the strip itself is newest
                    // author first (the list arrives that way).
                    authorStories.sortedBy { it.createdAt } to (authorId !in seen && authorId != mine)
                }
                .sortedByDescending { (authorStories, _) -> authorStories.maxOf { it.createdAt } }
        }

    val uiState: StateFlow<StoriesUiState> =
        combine(grouped, viewerIndex, viewerStoryIndex, uid) { groups, openIndex, storyIndex, uid ->
            val tiles = groups.map { (authorStories, unseen) ->
                val head = authorStories.last()
                StoryTileState(
                    authorId = head.authorId,
                    authorName = if (head.authorId == uid) "Your story" else head.authorName,
                    authorPhotoUrl = head.authorPhotoUrl,
                    authorVerified = head.authorVerified,
                    coverUrl = head.imageUrl,
                    storyCount = authorStories.size,
                    unseen = unseen,
                )
            }
            val open = openIndex?.coerceIn(0, tiles.lastIndex.coerceAtLeast(0))
            val openStories = open?.let { groups.getOrNull(it)?.first }.orEmpty()
            StoriesUiState(
                tiles = tiles,
                viewerIndex = open,
                viewerStories = openStories,
                viewerStoryIndex = storyIndex.coerceIn(0, (openStories.size - 1).coerceAtLeast(0)),
                mine = tiles.any { it.authorId == uid },
            )
        }.catch { cause ->
            Log.w("Omni", "The story strip could not be read", cause)
            emit(StoriesUiState())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = StoriesUiState(),
        )

    /** Opens the full-screen viewer on the tile at [index]. */
    fun openViewer(index: Int) {
        val uid = authRepository.currentUid ?: return
        viewerIndex.value = index
        viewerStoryIndex.value = 0
        // Marking on open, not on close: the ring must go grey the moment the tile is entered, the
        // same beat every stories UI uses.
        uiState.value.tiles.getOrNull(index)?.let { tile ->
            if (tile.authorId != uid) seen.value = seen.value + tile.authorId
        }
    }

    /** Next story within the tile, then the next author; `false` when the strip is exhausted. */
    fun advance(): Boolean {
        val state = uiState.value
        val stories = state.viewerStories
        if (state.viewerStoryIndex < stories.lastIndex) {
            viewerStoryIndex.value = state.viewerStoryIndex + 1
            return true
        }
        val nextTile = (state.viewerIndex ?: 0) + 1
        if (nextTile > state.tiles.lastIndex) {
            viewerIndex.value = null
            return false
        }
        openViewer(nextTile)
        return true
    }

    /** Previous story within the tile, then the previous author's newest; stays put at the start. */
    fun rewind() {
        val state = uiState.value
        if (state.viewerStoryIndex > 0) {
            viewerStoryIndex.value = state.viewerStoryIndex - 1
            return
        }
        val previousTile = (state.viewerIndex ?: 0) - 1
        if (previousTile >= 0) {
            openViewer(previousTile)
            viewerStoryIndex.value = Int.MAX_VALUE // clamped onto the tile's last story by uiState
        }
    }

    /** Closes the viewer — the X, the system back, and the end of the last story all land here. */
    fun closeViewer() {
        viewerIndex.value = null
    }

    /**
     * Publishes a story: uploads [image] through Cloudinary, then writes the document with the
     * author's identity denormalised and the 24-hour expiry stamped.
     *
     * [onResult] carries the failure sentence on a failed upload (the screen shows it; nothing is
     * lost — the picker's photo stays picked, exactly as the post composer behaves).
     */
    fun createStory(author: PostAuthor, image: Uri, caption: String?, onResult: (String?) -> Unit) {
        val uid = authRepository.currentUid ?: return onResult("You are signed out.")
        viewModelScope.launch {
            val imageUrl = try {
                // The story's own folder — not the post uploader. Nothing about the upload decides
                // what this becomes; the write below does, and it writes a story.
                mediaRepository.uploadStoryImage(uid, image)
            } catch (cause: Exception) {
                Log.w("Omni", "Uploading the story image failed", cause)
                return@launch onResult("The photo could not be uploaded. Check your connection.")
            }
            try {
                storyRepository.createStory(
                    Story(
                        id = "",
                        authorId = uid,
                        authorName = author.name.ifBlank { "You" },
                        authorPhotoUrl = author.photoUrl,
                        authorVerified = author.verified,
                        imageUrl = imageUrl,
                        caption = caption?.takeIf { it.isNotBlank() },
                        expiresAt = storyExpiryFromNow(),
                    ),
                )
                onResult(null)
            } catch (cause: Exception) {
                Log.w("Omni", "Publishing the story failed", cause)
                onResult("The story could not be published. Try again.")
            }
        }
    }

    /** Deletes the viewer's own story currently on screen — the viewer's own trash affordance. */
    fun deleteMyStory() {
        val uid = authRepository.currentUid ?: return
        val story = uiState.value.viewerStories.getOrNull(uiState.value.viewerStoryIndex) ?: return
        if (story.authorId != uid) return
        viewModelScope.launch {
            try {
                storyRepository.deleteMine(uid, story.id)
                advance()
            } catch (cause: Exception) {
                Log.w("Omni", "Deleting the story failed", cause)
            }
        }
    }

    private companion object {
        /** Matches the feed's ViewModel: a rotation keeps the listener, a backgrounded app stops. */
        const val ListenerGraceMillis = 5_000L
    }
}
