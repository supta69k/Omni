package com.example.omni.ui.feed

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Post
import com.example.omni.data.model.Profession
import com.example.omni.data.model.compactCount
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniTabScaffold
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedPostBody
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniFeedVerified
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNutriChipCarbs
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSosTrack
import com.example.omni.ui.theme.OmniTabActiveSurface
import com.example.omni.ui.theme.OmniTabShadow
import com.example.omni.ui.theme.OmniTheme

/**
 * The community feed — Figma frame `iPhone 14 & 15 Pro - 28` (node 124:15).
 *
 * The artboard is 415 x 1553, so like the dashboard it is a scrolling page with two viewport-anchored
 * pieces:
 *
 *  - `Header` (node 124:152) is 415 x 177 and marked `sticky top-0` in the source. It is taller than
 *    the dashboard's 101 because the search row lives inside it, so the whole thing — greeting row
 *    *and* search pill — stays put while the posts scroll under it.
 *  - `Bottom Navbar` sits at y=1135 in page space but is positioned `top-[calc(50%+392px)]`, i.e.
 *    viewport-relative, and is plainly a floating bar; it is pinned above the system navigation bar.
 *
 * The vertical map of the scrolling content is: story strip at y=186 (h 105), the segmented control
 * at y=315 (h 39), "Recently Post" at y=377 (h 20), the first post at y=420 (h 487) and the second at
 * y=938 (h 485). Each gap constant below is that map's arithmetic.
 *
 * The page is a `LazyColumn` rather than the scrolling `Column` the frame implies, because the frame
 * only has to hold two posts and this has to hold a feed. A plain column composes every post it owns
 * at once: twenty posts meant twenty post trees measured on the first frame and twenty Coil requests
 * racing each other for the network, all so the reader could look at the two that fit on screen. The
 * geometry is untouched — the leading and trailing `Spacer`s became `contentPadding`, the design's 31
 * between posts is each item's own top padding, and the masthead's 23 is still 23.
 */
@Composable
fun FeedScreen(
    header: OmniHeaderState = OmniHeaderState(),
    state: FeedUiState = FeedUiState(),
    stories: StoriesUiState = StoriesUiState(),
    myUid: String? = null,
    myPhotoUrl: String? = null,
    onSegmentChange: (FeedSegment) -> Unit = {},
    onLike: (String) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
    onCloseComments: () -> Unit = {},
    /** Sends a comment on the open post; called back with `null` on success, a reason otherwise. */
    onSendComment: (String, (String?) -> Unit) -> Unit = { _, done -> done(null) },
    onCompose: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    /** Reposts [Post.id] — the pill's own action, distinct from share. */
    onRepost: (String) -> Unit = {},
    /** Opens the story viewer on the tile at [Int]. */
    onOpenStory: (Int) -> Unit = {},
    /**
     * Starts a story — the picker, then the story sheet. Deliberately *not* [onCompose]: a story is
     * its own entity in its own collection, and the share tile used to call the post composer, which
     * is why "add to your story" wrote a feed post.
     */
    onAddStory: () -> Unit = {},
    /** Opens the author's public profile — the avatar's tap, the Facebook convention. */
    onOpenProfile: (String) -> Unit = {},
    /**
     * Follows or unfollows the post's author. Handed [Post.authorId] — the person the row is about —
     * never the signed-in viewer, who is the write's subject rather than its target.
     */
    onFollowToggle: (String) -> Unit = {},
    /** What the search field found, and which of its four states it is in. */
    search: FeedSearchState = FeedSearchState(),
    onSearchQueryChange: (String) -> Unit = {},
    onSearchDismiss: () -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
) {
    val context = LocalContext.current

    val landscape = LocalDesignWindow.current.isLandscape

    DesignFrame {
        OmniTabScaffold(
            selected = OmniNavItem.Feed,
            onNavigate = onNavigate,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                // What used to be the leading and trailing `Spacer`s. As content padding they are
                // part of the scroll range rather than two items the list has to keep measured.
                //
                // The bottom clearance is the floating bar's, so it only applies where the bar is:
                // in landscape the navigation is a rail beside the list, and reserving 100dp under
                // the last post there is dead space no bar ever covers.
                contentPadding = PaddingValues(
                    top = HeaderHeight + StoryStripGap,
                    bottom = if (landscape) {
                        ContentBottomGap
                    } else {
                        OmniNavHeight + OmniNavBottomGap + ContentBottomGap
                    },
                ),
            ) {
                // One item, not five: the strip, the control and the "Recently Post" line always
                // enter and leave the viewport together, and splitting them would only give the list
                // four more things to measure.
                item(key = MastheadKey, contentType = MastheadKey) {
                    Column {
                        StoryStrip(
                            tiles = stories.tiles,
                            myUid = myUid,
                            myPhotoUrl = myPhotoUrl,
                            onOpenViewer = onOpenStory,
                            onAddStory = onAddStory,
                            onOpenProfile = onOpenProfile,
                        )

                        Spacer(Modifier.height(SegmentGap))

                        Box(Modifier.padding(start = ScreenPadding)) {
                            FeedSegments(
                                selected = state.segment,
                                onSelect = onSegmentChange,
                            )
                        }

                        Spacer(Modifier.height(RecentlyPostGap))

                        Text(
                            text = "Recently Post",
                            style = FeedType.Hint16,
                            color = OmniFeedHint,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = RecentlyPostPadding),
                        )

                        Spacer(Modifier.height(FirstPostGap))
                    }
                }

                if (state.posts.isEmpty()) {
                    // The honest empty state: a feed with nothing in it yet, rather than the design's
                    // two mock posts pretending to be data.
                    //
                    // Two sentences, because the two tabs are empty for different reasons and the
                    // fix is different too. Following no longer carries my own posts, so "share the
                    // first one" would be advice that does not change what this tab shows.
                    item(key = EmptyKey, contentType = EmptyKey) {
                        Text(
                            text = when (state.segment) {
                                FeedSegment.Discover -> "No posts yet. Share the first one!"
                                FeedSegment.Following ->
                                    "Nothing here yet. Follow someone and their posts land here."
                            },
                            style = FeedType.Hint16,
                            color = OmniFeedHint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = state.posts,
                        // The post id, so liking one post recomposes that post and not the whole
                        // feed, and so a page of older posts arriving does not re-create the ones
                        // already on screen. `contentType` lets the list reuse a scrolled-off post's
                        // layout nodes for the next one instead of building them again.
                        key = { _, post -> post.id },
                        contentType = { _, _ -> PostKey },
                    ) { index, post ->
                        Box(
                            modifier = Modifier.padding(
                                start = ScreenPadding,
                                end = ScreenPadding,
                                // Figma's 31 between posts, and nothing above the first — the
                                // masthead already ends on its own 23.
                                top = if (index == 0) 0.dp else PostSpacing,
                            ),
                        ) {
                            FeedPost(
                                post = post,
                                // My own posts draw my *current* photo rather than the one
                                // denormalised onto them when they were written. Setting a profile
                                // photo does not rewrite history, and a feed where my older posts
                                // still show a bare initial reads as the photo not having saved.
                                avatarUrl = post.authorPhotoUrl
                                    ?: myPhotoUrl.takeIf { post.authorId == myUid },
                                onLike = { onLike(post.id) },
                                onComment = { onOpenComments(post.id) },
                                onRepost = { onRepost(post.id) },
                                onOpenProfile = { onOpenProfile(post.authorId) },
                                // Drawn only on somebody else's post: `showFollow` is decided from
                                // the two uids, so a post of mine has no control at all rather than
                                // one that is hidden after the fact.
                                showFollow = post.authorId.isNotBlank() &&
                                    myUid != null &&
                                    post.authorId != myUid,
                                isFollowing = post.authorId in state.following,
                                onFollowToggle = { onFollowToggle(post.authorId) },
                                onShare = {
                                    // The Android share sheet, with the post's body as the text.
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${post.authorName} on omni:\n${post.body}",
                                        )
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, "Share post"),
                                    )
                                },
                            )
                        }
                    }

                    // Load-more stays a word rather than becoming an invisible scroll trigger. The
                    // list could surface the end signal now, which the old plain column could not,
                    // so this is a choice: a feed that grows under the thumb loses the reader's
                    // place, and the tap is what says "I am ready for more".
                    if (state.canLoadMore) {
                        item(key = LoadMoreKey, contentType = LoadMoreKey) {
                            Box(
                                modifier = Modifier
                                    .padding(
                                        start = ScreenPadding,
                                        end = ScreenPadding,
                                        top = PostSpacing,
                                    )
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(OmniFeedSurface)
                                    .clickable(onClick = onLoadMore)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Load more",
                                    style = FeedType.SegmentLabel,
                                    color = OmniCardInk,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Tapping the feed while the results are up closes them, the way every overlay on this
            // app behaves. Drawn under the header so the field itself stays reachable, and only while
            // there is something to dismiss — an invisible full-screen tap target the rest of the
            // time would eat every tap the feed has.
            if (search.isOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SearchScrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSearchDismiss,
                        ),
                )
            }

            FeedHeader(
                header = header,
                onNavigate = onNavigate,
                onCompose = onCompose,
                search = search,
                onSearchQueryChange = onSearchQueryChange,
                onSearchDismiss = onSearchDismiss,
                onOpenProfile = { uid ->
                    onSearchDismiss()
                    onOpenProfile(uid)
                },
            )
        }
        }

        // Sheet is sibling of scaffold so it overlays bar/rail (KDoc rule 11)
        CommentsSheet(
            visible = state.commentsOpenId != null,
            comments = state.comments,
            onDismiss = onCloseComments,
            onSend = onSendComment,
            onOpenProfile = onOpenProfile,
        )
    }
}

/**
 * The sticky white bar — Figma `Header` (node 124:152), 415 x 177.
 *
 * It is the dashboard's header with the search row bolted underneath, so it reuses
 * [OmniHeader] verbatim and only adds what is below it. The search row's own y is 119 in the frame,
 * measured from the same origin as the mock status bar; with the mock bar replaced by the real one
 * that becomes [SearchRowGap] below the greeting row.
 *
 * **The search pill is Figma's, now with a field in it.** Its 325 x 48, its 42 radius, its surface,
 * its 24dp glyph, the 19 it is inset by and the 6 between icon and text are all unchanged; the
 * placeholder run that used to be a `Text` is the placeholder of a [BasicTextField] with the same
 * style and colour, so the closed state renders pixel-for-pixel what it did before. Results drop
 * below the header as a panel over the feed (UI_ARCHITECTURE §6 rule 11: an overlay built from the
 * page's own parts, inside its own frame — never a Material surface outside it).
 */
@Composable
private fun FeedHeader(
    modifier: Modifier = Modifier,
    header: OmniHeaderState = OmniHeaderState(),
    onNavigate: (OmniNavItem) -> Unit = {},
    onCompose: () -> Unit = {},
    search: FeedSearchState = FeedSearchState(),
    onSearchQueryChange: (String) -> Unit = {},
    onSearchDismiss: () -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
) {
    Column(modifier = modifier.background(OmniBackground)) {
        OmniHeader(
            state = header,
            onProfileClick = { onNavigate(OmniNavItem.Setting) },
            onMessagesClick = { onNavigate(OmniNavItem.Messages) },
            onNotificationsClick = { onNavigate(OmniNavItem.Notifications) },
        )

        Spacer(Modifier.height(SearchRowGap))

        Row(
            modifier = Modifier
                .padding(start = ScreenPadding)
                .width(SearchRowWidth),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(325.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(42.dp))
                    .background(OmniFeedSurface),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.padding(start = 19.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_feed_search),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    BasicTextField(
                        value = search.query,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = FeedType.Hint16.copy(color = OmniCardInk),
                        cursorBrush = SolidColor(OmniInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // Placeholder and field in one box, centre-start — the project's own
                        // `decorationBox` shape. The hint keeps the exact style and colour the static
                        // `Text` had, so nothing about the resting pill moved.
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (search.query.isEmpty()) {
                                    Text(
                                        text = "Search here",
                                        style = FeedType.Hint16,
                                        color = OmniFeedHint,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // The clear affordance — the composer's own × chip, at the composer's own 28, so
                    // the touch target is a real one. Drawn only when there is something to clear,
                    // so the resting pill is exactly the design's.
                    if (search.isOpen) {
                        Box(
                            modifier = Modifier
                                .size(SearchClearSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSearchDismiss,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                style = FeedType.SegmentLabel,
                                color = OmniFeedTimestamp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(51.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(42.dp))
                    .background(OmniInk)
                    .clickable(onClick = onCompose),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_feed_add),
                    contentDescription = "New post",
                    modifier = Modifier
                        .offset(x = 0.5.dp)
                        .size(24.dp),
                )
            }
        }

        if (search.isOpen) {
            SearchResults(search = search, onOpenProfile = onOpenProfile)
        }

        Spacer(Modifier.height(SearchRowBottomGap))
    }
}

/**
 * What the search found, dropped under the field.
 *
 * Part of the header rather than a layer of its own, for two reasons. It scrolls with nothing — the
 * header is the one sticky piece of this page, and a results list that slid away under the feed
 * would be unreachable the moment a finger moved. And it is built from the feed's own parts inside
 * the page's `DesignFrame` (UI_ARCHITECTURE §6 rule 11) rather than a Material surface, which would
 * be measured outside this screen's density and come back the wrong size.
 *
 * Every state [FeedSearchState] distinguishes is drawn, because most of them look identical if you
 * only test `results.isEmpty()`: not yet asked, still working, nobody by that name, and the query
 * failed are different answers and the panel says which. The height is capped at
 * [SearchPanelMaxHeight] and the list scrolls inside it, so a full page of matches cannot push the
 * feed off the screen.
 */
@Composable
private fun SearchResults(search: FeedSearchState, onOpenProfile: (String) -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = ScreenPadding, top = SearchPanelGap)
            .width(SearchPanelWidth)
            .heightIn(max = SearchPanelMaxHeight)
            .clip(RoundedCornerShape(19.dp))
            .background(OmniFeedSurface),
    ) {
        when {
            search.error != null -> SearchNote(search.error)
            // Not yet a question. Saying "no one by that name" after one letter would be an answer
            // to something nobody asked — the query never left the phone.
            search.isTooShort -> SearchNote("Keep typing to find people.")
            search.isSearching && search.results.isEmpty() -> SearchNote("Searching…")
            search.results.isEmpty() && !search.isSearching -> SearchNote("No one by that name.")
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = SearchRowPadding),
            ) {
                itemsIndexed(search.results, key = { _, user -> user.uid }) { _, user ->
                    SearchResultRow(user = user, onClick = { onOpenProfile(user.uid) })
                }
            }
        }
    }
}

/** The panel's one-line answer when there is no list to show — same inset as a row. */
@Composable
private fun SearchNote(text: String) {
    Text(
        text = text,
        style = FeedType.Meta12,
        color = OmniFeedTimestamp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(horizontal = SearchRowInset, vertical = 18.dp),
    )
}

/**
 * One person found — the post row's author block, at the post row's own sizes.
 *
 * The tap carries **this row's** uid, which is the whole point of the control: a result is only
 * useful if it opens the person it names.
 */
@Composable
private fun SearchResultRow(user: com.example.omni.data.model.User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SearchRowInset, vertical = SearchRowPadding),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(SearchAvatarSize)
                .clip(CircleShape)
                .background(OmniBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (user.photoUrl != null) {
                coil3.compose.AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "${user.name}'s profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = user.name.take(1).uppercase(),
                    style = FeedType.AuthorName,
                    color = OmniFeedTimestamp,
                    maxLines = 1,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = user.name,
                    style = FeedType.AuthorName,
                    color = OmniCardInk,
                    maxLines = 1,
                    softWrap = false,
                )
                if (user.verified) {
                    Image(
                        painter = painterResource(R.drawable.ic_feed_badge_check),
                        contentDescription = "Verified",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                // The discipline when there is one, the feed's own subtitle otherwise — the same
                // two lines a post row shows, so a person reads the same here and there.
                text = when (user.profession) {
                    Profession.DOCTOR -> "Doctor"
                    Profession.NUTRITIONIST -> "Nutritionist"
                    null -> if (user.verified) "Verified by omni" else "On omni"
                },
                style = FeedType.Meta12,
                color = OmniFeedVerified,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The story rail — Figma `Recently Post Section` (node 124:52), now live.
 *
 * The first tile is the share-meal tile (the feed's own design), retargeted: with no story of mine
 * it starts one; with one it opens the viewer on my tile, and the plus badge over my avatar starts
 * another. Every other author with a live story gets a tile — their newest story's photo as the
 * cover, an accent ring while unseen, grey once watched — the convention every stories strip has
 * used since the convention settled.
 */
@Composable
private fun StoryStrip(
    tiles: List<StoryTileState>,
    myUid: String?,
    myPhotoUrl: String?,
    onOpenViewer: (Int) -> Unit,
    onAddStory: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
) {
    // My tile, if I have one, is always first in the list (the ViewModel sorts it there); its index
    // in the tiles list is what the share tile opens.
    val myTile = tiles.firstOrNull { it.authorId == myUid }
    val myTileIndex = tiles.indexOfFirst { it.authorId == myUid }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // My tile: the design's own share tile, now with two separate actions (§6). The avatar
        // opens the viewer when there is a story; the bottom "+ Add to your story" strip always
        // opens the upload flow.
        ShareMealTile(
            hasStory = myTile != null,
            photoUrl = myPhotoUrl,
            onOpenViewer = { onOpenViewer(myTileIndex) },
            onAddStory = onAddStory,
        )
        tiles.forEachIndexed { index, tile ->
            if (tile.authorId != myUid) {
                StoryTile(
                    tile = tile,
                    onClick = { onOpenViewer(index) },
                    onLongPress = { onOpenProfile(tile.authorId) },
                )
            }
        }
    }
}

/**
 * One author's tile — their newest story's photo under a ring that says seen or not.
 *
 * The brief asks for "tap a story → that user's profile" (§8). A long press keeps the short tap
 * free for what it has always done — open the viewer — and is the gesture every stories UI on the
 * platform has used for "go to this person's page" since the convention settled.
 */
@Composable
private fun StoryTile(
    tile: StoryTileState,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .width(93.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OmniFeedSurface)
            // The active-story ring (§8). A *gradient* sweep rather than a flat stroke: the palette's
            // lavender into the auth pages' carb violet, top-left to bottom-right, which is the
            // "polished, not a plain border" the brief asked for and reads as a ring even at 93dp.
            // A watched tile drops to a 1dp flat hint grey — the ring is the signal, so it has to be
            // visibly *different* rather than the same shape in another colour.
            .border(
                width = if (tile.unseen) UnseenRing else SeenRing,
                brush = if (tile.unseen) StoryRingBrush else SolidColor(OmniFeedHint),
                shape = RoundedCornerShape(10.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        if (tile.coverUrl != null) {
            coil3.compose.AsyncImage(
                model = tile.coverUrl,
                contentDescription = "${tile.authorName}'s story",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // The initial cover: an author with no loadable photo still gets a tile, and an initial
            // cannot fail to load — the same stand-in the feed's post rows use.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tile.authorName.take(1).uppercase(),
                    style = FeedType.AuthorName,
                    color = OmniFeedHint,
                    maxLines = 1,
                )
            }
        }

        // The name plate, on the photo's foot — the design's own caption slot.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(NamePlateScrim)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = tile.authorName,
                style = FeedType.StoryCaption,
                color = OmniOnInk,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The first tile — Figma `Meal Image and Text` (node 124:62), back to the design's own layout.
 *
 * The avatar sits at the top, the violet plus badge overlaps its foot, and **"Share Your healthy
 * meal"** wraps onto two centred lines underneath. That is the frame the design draws, and it is
 * what [FeedType.StoryCaption] was cut for — the style's own doc comment names this string.
 *
 * An earlier pass replaced all of it with a single-line "+ Add to your story" strip to give the
 * tile two unambiguous tap targets. It bought the targets by throwing the frame away: a caption
 * the design never wrote, a glyph moved out from under the avatar, and a 10sp line stretched flat
 * across a 93dp tile. The split was worth keeping; paying for it in Figma's layout was not.
 *
 * So the two actions stay, drawn as the design already draws them (§6):
 *
 * 1. **Avatar** (the top [SharePlusTop]dp): opens the viewer on my tile. With no story it is the
 *    placeholder asset and not a tap target — there is nothing to view yet.
 * 2. **Plus badge and caption** (everything below it): **always** opens the upload flow. They are
 *    one button, which is what the badge sitting on the caption's own column already looks like.
 *
 * The seam falls at the badge's top edge, so the badge belongs to "add" and the avatar above it to
 * "view" — which is the reading the design invites anyway.
 */
@Composable
private fun ShareMealTile(
    hasStory: Boolean,
    photoUrl: String?,
    onOpenViewer: () -> Unit,
    onAddStory: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(ShareTileWidth)
            .height(ShareTileHeight)
            .clip(RoundedCornerShape(ShareTileCorner))
            .background(OmniFeedSurface),
        contentAlignment = Alignment.Center,
    ) {
        // `Meal Image and Text` is 81 x 92 inside the 93 x 105 tile: 6 a side, 6.5 at the top.
        Box(
            modifier = Modifier
                .width(ShareInnerWidth)
                .height(ShareInnerHeight),
        ) {
            // The ring box is [ShareRingInset]dp larger than the avatar on every side and offset
            // back by the same amount, so the avatar lands on Figma's mark whether or not a ring is
            // drawn around it and the ring grows outwards into the tile's top margin.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = ShareAvatarNudge, y = -ShareRingInset)
                    .size(ShareRingSize)
                    // The same gradient ring the other tiles wear (§8), drawn round *my* avatar only
                    // while I have a live story. My own tile has no seen/unseen state —
                    // [StoriesViewModel] deliberately does not mark my tile watched — so here the ring
                    // means exactly one thing: "your story is up". It disappears on its own when the
                    // story expires, because `hasStory` is the presence of a tile on the rail and the
                    // rail only carries stories whose `expiresAt` is still ahead of now.
                    .let { base ->
                        if (hasStory) {
                            base.border(width = UnseenRing, brush = StoryRingBrush, shape = CircleShape)
                        } else {
                            base
                        }
                    }
                    .let { base ->
                        if (hasStory) base.clickable(onClick = onOpenViewer) else base
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (photoUrl != null) {
                    coil3.compose.AsyncImage(
                        model = photoUrl,
                        // Named only while it is a target. Decorative otherwise — the caption below
                        // already says what the tile is for.
                        contentDescription = if (hasStory) "Your story" else null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(ShareAvatarSize)
                            .clip(CircleShape)
                            // The placeholder asset carries this hairline in the PNG itself. A real
                            // photo has to be given it, or swapping one in would quietly drop a ring
                            // the design draws — and only while no story ring is already there.
                            .let { base ->
                                if (hasStory) {
                                    base
                                } else {
                                    base.border(SharePhotoRing, OmniFeedHint, CircleShape)
                                }
                            },
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.feed_story_share_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(ShareAvatarSize)
                            .clip(CircleShape),
                    )
                }
            }

            // The plus badge and the caption — one button, always live. Adding a new story is what
            // the user means down here, regardless of what is already on the rail. Declared after the
            // avatar so it wins the hit test where the two overlap, which is the badge's own footprint.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = SharePlusTop)
                    .width(ShareInnerWidth)
                    .clickable(onClick = onAddStory),
                verticalArrangement = Arrangement.spacedBy(ShareCaptionGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_feed_share_plus),
                    contentDescription = "Add to your story",
                    modifier = Modifier.size(SharePlusSize),
                )
                Text(
                    text = "Share Your healthy meal",
                    style = FeedType.StoryCaption,
                    color = OmniFeedVerified,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * "Discover" / "Following" — Figma `Component 3` (node 124:3), a 253 x 39 track.
 *
 * Unlike the dashboard's segmented control both labels are the same run here; only the colour and
 * the white pill behind the selected one change. Each label is given Figma's own 68 slot so the two
 * states occupy identical space and the track does not twitch as the selection moves.
 */
@Composable
private fun FeedSegments(selected: FeedSegment, onSelect: (FeedSegment) -> Unit) {
    Row(
        modifier = Modifier
            .width(253.dp)
            .height(39.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(OmniFeedSurface)
            .padding(start = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentTab("Discover", 125.dp, selected == FeedSegment.Discover) { onSelect(FeedSegment.Discover) }
        SegmentTab("Following", 106.dp, selected == FeedSegment.Following) { onSelect(FeedSegment.Following) }
    }
}

@Composable
private fun SegmentTab(label: String, width: Dp, selected: Boolean, onClick: () -> Unit) {
    val base = Modifier
        .width(width)
        .height(33.dp)

    if (selected) {
        Box(
            modifier = base
                // Figma's `0 0 6.6 2 rgba(190,190,190,.25)` is a spread glow, which Compose's
                // single-elevation shadow cannot express; 3dp is the closest visual match — the
                // same compromise the dashboard's tabs make.
                .shadow(3.dp, RoundedCornerShape(30.dp), ambientColor = OmniTabShadow, spotColor = OmniTabShadow)
                .clip(RoundedCornerShape(30.dp))
                .background(OmniTabActiveSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = FeedType.SegmentLabel,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Box(
            modifier = base.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = FeedType.SegmentLabel,
                color = OmniFeedHint,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * One post — Figma `Recently Post Container` (124:72) and `Posts Container` (124:112).
 *
 * The layout is unchanged from the design; what changed is where every value comes from: the author
 * row, the badge, the "Verified by omni" line, the timestamp, the body and the image are all fields
 * of the post now, and the three pills carry click handlers with the like reflecting its state.
 *
 * The verified badge draws only when the author is verified — the design's every-post badge was the
 * mock's simplification. The sub-line reads "Verified by omni" for verified professionals and the
 * author's own line otherwise ("Posted on omni"), which is the sentence the mock was standing in for.
 *
 * **Divergence from Figma, deliberate (UI_ARCHITECTURE §6 rule 9).** The design's author row has one
 * thing on its right — the timestamp — and the brief asks for a Follow control on the row as well.
 * The timestamp keeps its slot and the pill is stacked *under* it, end-aligned, which is the one
 * place on the row with free height: the row's height is set by the 49 avatar opposite, and
 * `18 (Meta12's line) + 5 + 26 = 49` fills it exactly. So the pill costs no height, no width the
 * design was using, and no change to the 17 below the row or the 31 between posts. On my own posts it
 * is not drawn and the row is Figma's, untouched.
 */
@Composable
private fun FeedPost(
    post: Post,
    /**
     * The author's avatar, which is [Post.authorPhotoUrl] for everyone else and the signed-in user's
     * *live* photo for their own posts — see the call site for why the denormalised copy is not
     * enough.
     */
    avatarUrl: String?,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onRepost: () -> Unit,
    onOpenProfile: () -> Unit,
    onShare: () -> Unit,
    /** `false` on my own posts and while signed out — the control is absent, not disabled. */
    showFollow: Boolean = false,
    /** Whether I already follow [Post.authorId], from the feed's one live follow set. */
    isFollowing: Boolean = false,
    onFollowToggle: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(19.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(49.dp)
                            .clip(RoundedCornerShape(35.7.dp))
                            .background(OmniFeedSurface)
                            // The avatar is the author's page — the Facebook convention the brief
                            // named. The clip stays first so the ripple stays inside the squircle.
                            .clickable(onClick = onOpenProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            coil3.compose.AsyncImage(
                                model = avatarUrl,
                                contentDescription = "${post.authorName}'s profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // The author's initial on the feed's grey — the placeholder that cannot
                            // fail to load, the same stand-in the comments sheet uses.
                            Text(
                                text = post.authorName.take(1).uppercase(),
                                style = FeedType.AuthorName,
                                color = OmniFeedTimestamp,
                                maxLines = 1,
                            )
                        }
                    }
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = post.authorName,
                                style = FeedType.AuthorName,
                                color = OmniCardInk,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (post.authorVerified) {
                                Image(
                                    painter = painterResource(R.drawable.ic_feed_badge_check),
                                    contentDescription = "Verified",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Text(
                            text = if (post.authorVerified) "Verified by omni" else "Posted on omni",
                            style = FeedType.Meta12,
                            color = OmniFeedVerified,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(FollowPillGap),
                ) {
                    Text(
                        text = relativeTimeOf(post.createdAt),
                        style = FeedType.Meta12,
                        color = OmniFeedTimestamp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (showFollow) {
                        FollowPill(following = isFollowing, onClick = onFollowToggle)
                    }
                }
            }

            Text(
                text = post.body,
                style = FeedType.PostBody,
                color = OmniFeedPostBody,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(19.dp)) {
            if (post.imageUrl != null) {
                coil3.compose.AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(255.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OmniFeedSurface),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ActionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionPill(
                    text = compactCount(post.likeCount),
                    icon = R.drawable.ic_feed_heart_add,
                    contentDescription = if (post.likedByMe) "Unlike" else "Like",
                    emphasized = post.likedByMe,
                    onClick = onLike,
                )
                ActionPill(
                    text = compactCount(post.commentCount),
                    icon = R.drawable.ic_feed_comment,
                    contentDescription = "Comment",
                    emphasized = false,
                    onClick = onComment,
                )
                ActionPill(
                    text = compactCount(post.repostCount),
                    icon = R.drawable.ic_feed_repost,
                    contentDescription = if (post.repostedByMe) "Reposted" else "Repost",
                    emphasized = post.repostedByMe,
                    onClick = onRepost,
                )

                // Share is pushed to the trailing edge and given the same pill as the other three, so
                // the row reads `Like | Comment | Repost | Share` rather than trailing off into a bare
                // icon the eye has to find. Weight rather than a fixed gap: it absorbs whatever width
                // the device has, which is what keeps the four pills from colliding on a narrow one.
                Spacer(Modifier.weight(1f))

                ActionPill(
                    text = null,
                    icon = R.drawable.ic_feed_share,
                    contentDescription = "Share",
                    emphasized = false,
                    onClick = onShare,
                )
            }
        }
    }
}

/**
 * The row's Follow control — the profile page's own Follow/Following pill, shrunk to fit the feed.
 *
 * Nothing new is invented: the colours, the shape and the two words are `ProfileScreen`'s, so the
 * same relationship reads the same wherever it is shown (and the brief's "follow state consistent
 * across Feed, Profile and Search" is a visual promise as well as a data one). Only the size differs
 * — 78 x 26 against the profile's full-width 46 — because it shares a row with a 49 avatar rather
 * than owning one.
 *
 * The width is fixed rather than wrapped so the row does not twitch as the label changes between
 * "Follow" and "Following"; 78 is the wider of the two labels at [FeedType.Meta12] plus the 12 a side
 * the profile's pill uses proportionally.
 */
@Composable
private fun FollowPill(following: Boolean, onClick: () -> Unit) {
    // Ink when there is something to do, the softer chip once it is done — the profile's exact pair.
    val fill by animateColorAsState(
        targetValue = if (following) OmniNutriChipCarbs else OmniInk,
        label = "followFill",
    )

    Box(
        modifier = Modifier
            .width(FollowPillWidth)
            .height(FollowPillHeight)
            .clip(RoundedCornerShape(FollowPillHeight / 2))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (following) "Following" else "Follow",
            style = FeedType.Meta12,
            color = OmniOnInk,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One button in a post's action row: a grey pill holding a 20dp icon and, unless it is Share, a count
 * beside it.
 *
 * **This is a deliberate divergence from Figma, and it is a fix rather than a restyle.**
 * `UI_ARCHITECTURE.md` §6 rule 9 — divergence is allowed, silent divergence is not.
 *
 * The design draws a fixed 86 x 39 pill. Painted literally, three of them plus a bare 20dp Share icon
 * measured 378dp inside a 383dp content width (86 x 3 = 258, + 30 x 2 = 60, + 40 + 20 = 378) — 5dp of
 * slack, and a pill carrying "0" put ~36dp of content into its 86. On the device this reads as
 * oversized, over-spaced pills that wrap off the right edge: "Like/Comment/Repost buttons visually
 * oversized or incorrectly proportioned, count not aligned with icon". The fixed width could not be
 * right for both "0" and "1.2k" anyway, which is why it is now a minimum instead of a width.
 *
 * So: [ActionHeight] stays 39 and the radius stays `ActionHeight / 2` — the design's own height, so the
 * row still measures what it always did vertically. The width becomes [ActionMinWidth] as a floor and
 * wraps above it, the two have [ActionIconGap] between them rather than 4, and the count now sits
 * **after** its icon reading left-to-right. The 0.5dp `offset` that nudged the pair off the pill's
 * centre is gone with it: §9 forbids padding out a misalignment with an offset, and once the Row is
 * genuinely centred it has nothing left to hide.
 *
 * [text] is `null` for Share, which has no count. It keeps the same pill — an equal-height button in
 * the row rather than a bare glyph — and [ActionMinWidth] keeps it from collapsing to icon-plus-padding
 * beside three wider siblings.
 *
 * [emphasized] is the liked/reposted state: the pill stays the design's grey (the design never drew an
 * active variant) but the count and icon tint to the palette's emphasis, and the fill inverts to ink —
 * the same inversion the bottom bar's selected pill makes, which the app has already taught the user to
 * read as "this one is on". That was added after users reported likes "not working" while the writes
 * were landing fine: a tint alone on a grey pill is invisible at arm's length.
 */
@Composable
private fun ActionPill(
    text: String?,
    icon: Int,
    contentDescription: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val fill by animateColorAsState(
        targetValue = if (emphasized) OmniInk else OmniFeedSurface,
        label = "pillFill",
    )
    val label by animateColorAsState(
        targetValue = if (emphasized) OmniBackground else OmniInk,
        label = "pillLabel",
    )

    Box(
        modifier = Modifier
            .widthIn(min = ActionMinWidth)
            .height(ActionHeight)
            .clip(RoundedCornerShape(ActionHeight / 2))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = ActionPaddingH),
        // The whole pill is the touch target: `clickable` sits above the padding, so the 39dp height
        // and the full width are tappable rather than just the glyph.
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ActionIconGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
                colorFilter = ColorFilter.tint(label),
            )
            if (text != null) {
                Text(
                    text = text,
                    style = FeedType.ActionValue,
                    color = label,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// ---- The post action row's own geometry ----------------------------------------------------------
//
// Figma draws four 86 x 39 pills 30 apart (`Post Card`'s action row). Painted literally that is
// 86 x 3 + 30 x 2 + 40 + 20 = 378dp inside the 383dp a post card actually has to spend — 5dp of slack
// for four buttons, which is the "oversized / incorrectly proportioned / clipped" the device showed.
// The height is the design's and is kept; the width is the part that could never be right for both
// "0" and "1.2k", so it becomes a floor the pill grows past instead. `UI_ARCHITECTURE.md` §6 rule 9:
// the divergence is recorded here and in [ActionPill]'s KDoc rather than made quietly.

/** Figma's pill height, unchanged — the row still measures what it always did vertically. */
private val ActionHeight = 39.dp

/**
 * The floor, not the width. 56 is the widest a pill gets from its own content at the low end —
 * 20 (icon) + 6 (gap) + ~14 ("99" at 16sp) + 8 x 2 (padding) = 56 — so the three counted pills sit
 * flush with their content and only stretch for a count wide enough to need it, and Share (which has
 * no count, so 36 of content) is held to the same button size rather than shrinking to a bare glyph.
 */
private val ActionMinWidth = 56.dp

/** Inside each pill. 8 rather than 12: the minimum width already supplies most of the breathing room. */
private val ActionPaddingH = 8.dp

/** Between an icon and the count beside it — 6, where the fixed-width pill used 4. */
private val ActionIconGap = 6.dp

/** Figma's icon size, unchanged. */
private val ActionIconSize = 20.dp

/**
 * Between the three counted pills. 8 where Figma has 30: the design's gap was sized for a row that
 * overflowed, and at 8 the four buttons plus the `weight(1f)` before Share fit any width from a 320dp
 * phone up with the leftover falling into the spacer rather than off the right edge.
 */
private val ActionGap = 8.dp

// ---- The story strip's own geometry ----------------------------------------------------------------
//
// The rail is Figma `Recently Post Section` (node 124:52): 93 x 105 tiles, 11 apart, bleeding off
// the artboard's right edge (the frame reports itself 405 wide at x=16 — the strip scrolls
// sideways rather than squeezing, which is also what lets the user reach the last tile).

// ---- The follow pill's own geometry ----------------------------------------------------------------
//
// Not Figma's — the design has no Follow control on a post row. The three values below are chosen to
// spend the height the author row already has: the row is 49 tall because the avatar is, and the
// timestamp only uses 18 of it. See [FeedPost]'s KDoc for the arithmetic.

/** 49 − 18 (Meta12's line) − 5 (the gap) — the pill fills the row's remaining height exactly. */
private val FollowPillHeight = 26.dp

/** "Following" at 12sp, plus the profile pill's side padding scaled to this one. */
private val FollowPillWidth = 78.dp

/** Between the timestamp and the pill under it. */
private val FollowPillGap = 5.dp

/** The unseen ring — the lavender accent, drawn 2dp thick around a tile not yet watched. */
private val UnseenRing = 2.dp

/** The seen ring — 1dp of the feed's own hint grey. */
private val SeenRing = 1.dp

/**
 * The active-story ring (§8) — a diagonal sweep of the palette's own two accents.
 *
 * `OmniAuthHeading` (#8D84F9, the auth pages' lavender) into `OmniNutriChipCarbs` (#B184E1, the
 * nutrition chips' violet) and back, top-left to bottom-right. Both are already in the palette, so
 * this invents no colour; the gradient is what makes it read as a *ring* rather than as a coloured
 * stroke, which is the "polished, not like a plain border" the brief asked for. The third stop
 * returns to the lavender so the sweep closes rather than ending abruptly at the bottom corner.
 *
 * A watched tile drops to a flat 1dp hint grey ([SeenRing]) — the ring's presence is the signal, so
 * the seen state has to be a visibly different shape rather than the same one in another colour.
 */
private val StoryRingBrush = Brush.linearGradient(
    listOf(OmniAuthHeading, OmniNutriChipCarbs, OmniAuthHeading),
)

// ---- The share-meal tile's own geometry -----------------------------------------------------------
//
// Figma's numbers off node 124:62, as they were originally measured: a 93 x 105 tile holding an
// 81 x 92 `Meal Image and Text` frame, a 46 avatar at its top, the plus badge overlapping the
// avatar's foot, and the caption 10dp under the badge. Only the story ring is not the design's —
// see [ShareRingSize].

/** Tile outer size — same as [StoryTile]'s, so the rail's spacing reads uniform. */
private val ShareTileWidth = 93.dp
private val ShareTileHeight = 105.dp

/** 10dp radius — same as the other tiles. */
private val ShareTileCorner = 10.dp

/** The inner frame: 6 a side of the 93, 6.5 top and bottom of the 105. */
private val ShareInnerWidth = 81.dp
private val ShareInnerHeight = 92.dp

/** The avatar's own size. */
private val ShareAvatarSize = 46.dp

/** Figma centres the avatar half a point right of the frame's own axis. */
private val ShareAvatarNudge = 0.5.dp

/**
 * 56 = 46 avatar + 2 × 2dp stroke + 2 × 3dp gap, so the ring reads as a ring and not as a rim.
 *
 * The ring itself is the §8 divergence, not Figma's — the design has no active-story state to draw.
 * It grows outwards into the tile's 6.5dp top margin rather than moving the avatar, which is what
 * [ShareRingInset] is for: 5 = (56 − 46) / 2, so the avatar stays on the design's mark either way.
 */
private val ShareRingSize = 56.dp
private val ShareRingInset = 5.dp

/** The hairline the placeholder PNG has baked in, redrawn for a real photo that does not. */
private val SharePhotoRing = 1.dp

/** The badge sits 38 down the frame, so its 18dp laps the avatar's last 8. */
private val SharePlusTop = 38.dp
private val SharePlusSize = 18.dp

/** Between the badge and the caption's first line. */
private val ShareCaptionGap = 10.dp

/** 45% black — the name plate's scrim over any photograph. */
private val NamePlateScrim = androidx.compose.ui.graphics.Color(0x73000000)

// ---- The search panel's own geometry -------------------------------------------------------------
//
// Also not Figma's: the design draws the pill closed and never opens it. Everything here is measured
// off the pill it hangs from rather than invented — see [SearchResults].

/** 20% black, the lightest scrim the app uses: the feed stays legible behind the results. */
private val SearchScrim = androidx.compose.ui.graphics.Color(0x33000000)

/** The composer's × chip, at the composer's own 28 — a real touch target inside the 48 pill. */
private val SearchClearSize = 28.dp

/** The pill's own width, so the panel's edges line up with the field's. */
private val SearchPanelWidth = 325.dp

/**
 * Four rows and a little — about half the viewport.
 *
 * Enough that a search feels answered without the panel becoming the page; past this the list
 * scrolls inside itself and the feed underneath keeps its place.
 */
private val SearchPanelMaxHeight = 320.dp

/** Between the field and the panel under it. */
private val SearchPanelGap = 10.dp

/** The panel's side inset — the post card's own 16, one notch in from the pill's 19. */
private val SearchRowInset = 16.dp

/** A row's own breathing room; doubled it is the gap between two names. */
private val SearchRowPadding = 10.dp

/** Smaller than the post row's 49: a result is an identity, not a post's byline. */
private val SearchAvatarSize = 40.dp

// ---- Geometry ----------------------------------------------------------------------------------
//
// Every value below comes from frame `iPhone 14 & 15 Pro - 28`, which is 415 x 1553. The vertical
// map is: header 0..177 (containing the greeting row at y=48.336 and the search row at y=119), the
// story strip at y=186, the segmented control at y=315, "Recently Post" at y=377, post one at y=420
// (h 487) and post two at y=938 (h 485).

/** The page's gutter, and the story strip's left inset. */
private val ScreenPadding = 16.dp

/** "Recently Post" sits at 19, three further in than everything else. Figma's own asymmetry. */
private val RecentlyPostPadding = 19.dp

/**
 * 119 − (13 + 11.336 + 24 + 36 + 16.664): from the bottom of the header row *as [OmniHeader] emits
 * it* — that composable already carries the design's own 16.664 tail — to the search row at y=119.
 */
private val SearchRowGap = 18.dp

/** 177 − (119 + 50): what is left below the search row inside the header. */
private val SearchRowBottomGap = 8.dp

/** Figma's own `Search and Add Container`: 379 wide at x=16, so 20 clear of the right edge. */
private val SearchRowWidth = 379.dp

/** The header's height below the real status bar: 177 − 24.336 (the mock bar we drop). */
private val HeaderHeight = 152.664.dp

/** 186 − 177 */
private val StoryStripGap = 9.dp

/** 315 − (186 + 105) */
private val SegmentGap = 24.dp

/** 377 − (315 + 39) */
private val RecentlyPostGap = 23.dp

/** 420 − (377 + 20) */
private val FirstPostGap = 23.dp

/** 938 − (420 + 487) */
private val PostSpacing = 31.dp

/** Breathing room so the last post can scroll clear of the floating bar. */
private val ContentBottomGap = 24.dp

// The feed list's item identities.
//
// Every one is a stable key, which is the whole reason the list is lazy: without them a post that
// moves — because a newer one arrived above it — is treated as a different post, so its `remember`ed
// state and its already-decoded image are thrown away and fetched again. They double as
// `contentType`s so the list only ever reuses a post's layout nodes for another post, never for the
// masthead.

private const val MastheadKey = "masthead"
private const val PostKey = "post"
private const val EmptyKey = "empty"
private const val LoadMoreKey = "load-more"

@DevicePreviews
@Composable
private fun FeedScreenPreview() {
    OmniTheme { FeedScreen() }
}
