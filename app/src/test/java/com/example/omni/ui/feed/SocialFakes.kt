package com.example.omni.ui.feed

import android.net.Uri
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Comment
import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.Post
import com.example.omni.data.model.Story
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FeedRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.MediaRepository
import com.example.omni.data.repo.PostAuthor
import com.example.omni.data.repo.StoryRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * The in-memory doubles the two social ViewModel tests share.
 *
 * They are written as *servers*, not as canned answers: one list of posts and one list of stories
 * that every query reads through, with each query applying the rule its Firestore counterpart
 * applies. That is what lets a test tell a real query from a filter over an already-loaded page —
 * put a followed person's post outside Discover's page limit and only a repository that actually
 * queries by author can find it. A fake that returned a fixed list per method would pass either way.
 */

/** Signed in or out on demand, with the session as its own flow — the `sessionUid` architecture. */
internal class FakeAuthRepository(uid: String?) : AuthRepository {

    private val state = MutableStateFlow(
        if (uid == null) AuthState.UNAUTHENTICATED else AuthState.AUTHENTICATED,
    )
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    private val session = MutableStateFlow(uid)
    override val sessionUid: StateFlow<String?> = session.asStateFlow()

    override val currentUid: String?
        get() = session.value

    fun signInAs(uid: String) {
        session.value = uid
        state.value = AuthState.AUTHENTICATED
    }

    override fun signOut() {
        session.value = null
        state.value = AuthState.UNAUTHENTICATED
    }

    override suspend fun signUp(name: String, email: String, password: String) = signInAs("uid-$email")
    override suspend fun signIn(email: String, password: String) = signInAs("uid-$email")
    override suspend fun sendPasswordReset(email: String) = Unit

    // Not exercised by the social tests — the account pages have their own fakes.
    override val currentEmail: String? get() = session.value?.let { "$it@omni.test" }
    override suspend fun changeEmail(currentPassword: String, newEmail: String) = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit

    // Email-verification / OTP surface — not exercised by the social or sleep tests, but part of the
    // interface, so the double has to name them.
    override suspend fun reloadCurrentUser() = Unit
    override val isEmailVerified: Boolean get() = true
    override suspend fun sendOtpCode(): Boolean = true
    override suspend fun verifyOtpCode(code: String): Boolean = true
}

/**
 * Every post in the fake world, queried the way Firestore queries them.
 *
 * [authorsQuerySilent] is the one deliberately unrealistic setting: it makes the Following query
 * never answer, which is the window a tab switch has to survive without showing the tab it left.
 */
internal class FakeFeedRepository : FeedRepository {

    val server = MutableStateFlow<List<Post>>(emptyList())

    /** What the Following query was last asked for — the assertion that it *is* a query. */
    var lastAuthorIds: Set<String>? = null
        private set

    var authorsQuerySilent = false

    private val comments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())

    private fun newestFirst(all: List<Post>) = all.sortedByDescending { it.createdAt }

    override fun observeFirstPage(uid: String, pageSize: Int): Flow<List<Post>> =
        server.map { all -> newestFirst(all).take(pageSize) }

    override fun observeByAuthor(authorId: String, limit: Int): Flow<List<Post>> =
        server.map { all -> newestFirst(all.filter { it.authorId == authorId }).take(limit) }

    override fun observeByAuthors(uid: String, authorIds: Set<String>, limit: Int): Flow<List<Post>> {
        lastAuthorIds = authorIds
        if (authorsQuerySilent) return emptyFlow()
        return server.map { all ->
            if (authorIds.isEmpty()) emptyList()
            else newestFirst(all.filter { it.authorId in authorIds }).take(limit)
        }
    }

    override suspend fun countByAuthor(authorId: String): Int =
        server.value.count { it.authorId == authorId }

    override suspend fun loadPage(uid: String, beforeCreatedAt: Long, pageSize: Int): List<Post> =
        newestFirst(server.value.filter { it.createdAt < beforeCreatedAt }).take(pageSize)

    val likes = mutableListOf<Pair<String, String>>()

    override suspend fun toggleLike(uid: String, postId: String) {
        likes += uid to postId
    }

    override fun observeComments(postId: String): Flow<List<Comment>> =
        comments.map { it[postId].orEmpty() }

    val addedComments = mutableListOf<Comment>()

    override suspend fun addComment(uid: String, postId: String, authorName: String, body: String) {
        val comment = Comment(
            id = "c${addedComments.size}",
            authorId = uid,
            authorName = authorName,
            body = body,
        )
        addedComments += comment
        comments.value = comments.value + (postId to (comments.value[postId].orEmpty() + comment))
    }

    fun putComment(postId: String, comment: Comment) {
        comments.value = comments.value + (postId to (comments.value[postId].orEmpty() + comment))
    }

    override suspend fun createPost(uid: String, author: PostAuthor, body: String, imageUrl: String?) {
        server.value = server.value + Post(
            id = "new-${server.value.size}",
            authorId = uid,
            authorName = author.name,
            body = body,
            imageUrl = imageUrl,
            createdAt = Long.MAX_VALUE / 2,
        )
    }

    override suspend fun repost(uid: String, postId: String, author: PostAuthor): Boolean = true
}

/** Follow relationships as one live set per viewer — the same collection all three screens read. */
internal class FakeFollowRepository : FollowRepository {

    private val sets = mutableMapOf<String, MutableStateFlow<Set<String>>>()

    val follows = mutableListOf<Pair<String, String>>()
    val unfollows = mutableListOf<Pair<String, String>>()

    /** Makes the next write throw, so a refusal can be told from a silent no-op. */
    var refuseNextWrite = false

    private fun stream(uid: String) = sets.getOrPut(uid) { MutableStateFlow(emptySet()) }

    fun set(uid: String, targets: Set<String>) {
        stream(uid).value = targets
    }

    override fun observeFollows(uid: String, target: String): Flow<Boolean> =
        stream(uid).map { target in it }

    override fun observeFollowing(uid: String): Flow<Set<String>> = stream(uid)

    override suspend fun followerCount(uid: String): Int = sets.count { uid in it.value.value }

    override suspend fun follow(uid: String, target: String) {
        if (refuseNextWrite) {
            refuseNextWrite = false
            throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions")
        }
        follows += uid to target
        stream(uid).value = stream(uid).value + target
    }

    override suspend fun unfollow(uid: String, target: String) {
        if (refuseNextWrite) {
            refuseNextWrite = false
            throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions")
        }
        unfollows += uid to target
        stream(uid).value = stream(uid).value - target
    }
}

/** Every story in the fake world, unfiltered — the rail's filtering is the ViewModel's job. */
internal class FakeStoryRepository : StoryRepository {

    val server = MutableStateFlow<List<Story>>(emptyList())

    override fun observeStories(): Flow<List<Story>> = server

    override suspend fun createStory(story: Story) {
        server.value = server.value + story.copy(id = "s${server.value.size}")
    }

    override suspend fun deleteMine(uid: String, storyId: String) {
        server.value = server.value.filterNot { it.id == storyId && it.authorId == uid }
    }
}

/** Profiles, plus the name search with its call log — how "one query per pause" is asserted. */
internal class FakeUserRepository : UserRepository {

    val people = mutableMapOf<String, User>()

    /** Every term that actually reached the repository, in order. */
    val searchCalls = mutableListOf<String>()

    var searchFails = false

    override fun observeUser(uid: String): Flow<User?> = MutableStateFlow(people[uid])

    override suspend fun getUser(uid: String): User? = people[uid]

    override fun observeProfessionals(): Flow<List<User>> = MutableStateFlow(emptyList())

    override suspend fun searchByName(query: String, limit: Int): List<User> {
        searchCalls += query
        if (searchFails) throw IllegalStateException("The search index is unavailable")
        // The byte-ordered prefix match Firestore's `startAt`/`endAt` performs, case-insensitively
        // here only because the fake has no index to be case-sensitive about.
        return people.values
            .filter { it.name.startsWith(query, ignoreCase = true) }
            .sortedBy { it.name }
            .take(limit)
    }

    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) = Unit
    override suspend fun updateGoals(uid: String, goals: HealthGoals) = Unit
}

/** Uploads are not what these tests are about; reaching one is a bug in the test. */
internal class FakeMediaRepository : MediaRepository {
    override suspend fun uploadPostImage(uid: String, image: Uri): String =
        error("No upload belongs in a feed-state test")

    override suspend fun uploadStoryImage(uid: String, image: Uri): String =
        error("No upload belongs in a story-rail test")

    override suspend fun uploadAvatar(uid: String, image: Uri): String =
        error("No upload belongs in these tests")
}
