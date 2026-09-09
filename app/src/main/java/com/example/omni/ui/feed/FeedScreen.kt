package com.example.omni.ui.feed

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Post
import com.example.omni.data.model.compactCount
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedPostBody
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniFeedVerified
import com.example.omni.ui.theme.OmniInk
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
 */
@Composable
fun FeedScreen(
    header: OmniHeaderState = OmniHeaderState(),
    state: FeedUiState = FeedUiState(),
    onSegmentChange: (FeedSegment) -> Unit = {},
    onLike: (String) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
    onCloseComments: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    onCompose: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
) {
    val context = LocalContext.current

    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(HeaderHeight + StoryStripGap))

                StoryStrip(onShare = onCompose)

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

                if (state.posts.isEmpty()) {
                    // The honest empty state: a feed with nothing in it yet, rather than the design's
                    // two mock posts pretending to be data.
                    Text(
                        text = "No posts yet. Share the first one!",
                        style = FeedType.Hint16,
                        color = OmniFeedHint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(PostSpacing),
                    ) {
                        state.posts.forEach { post ->
                            FeedPost(
                                post = post,
                                onLike = { onLike(post.id) },
                                onComment = { onOpenComments(post.id) },
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

                        // Load-more is a word rather than an invisible scroll trigger: the trigger
                        // needs the scroll's end signal, which a plain column here does not surface
                        // without restructuring the page — noted as the follow-up when paging is
                        // exercised in anger.
                        if (state.canLoadMore) {
                            Box(
                                modifier = Modifier
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

                Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
            }

            FeedHeader(header = header, onNavigate = onNavigate, onCompose = onCompose)

            // The comments sheet, hosted in the page's own root Box so it stays inside DesignFrame's
            // density — the same rule the sleep and meal sheets follow.
            CommentsSheet(
                visible = state.commentsOpenId != null,
                comments = state.comments,
                onDismiss = onCloseComments,
                onSend = onSendComment,
            )

            OmniBottomNav(
                selected = OmniNavItem.Feed,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OmniNavBottomGap),
                onSelect = onNavigate,
            )
        }
    }
}

/**
 * The sticky white bar — Figma `Header` (node 124:152), 415 x 177.
 *
 * It is the dashboard's header with the search row bolted underneath, so it reuses
 * [OmniHeader] verbatim and only adds what is below it. The search row's own y is 119 in the frame,
 * measured from the same origin as the mock status bar; with the mock bar replaced by the real one
 * that becomes [SearchRowGap] below the greeting row.
 */
@Composable
private fun FeedHeader(
    modifier: Modifier = Modifier,
    header: OmniHeaderState = OmniHeaderState(),
    onNavigate: (OmniNavItem) -> Unit = {},
    onCompose: () -> Unit = {},
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
                    modifier = Modifier.padding(start = 19.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_feed_search),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Search here",
                        style = FeedType.Hint16,
                        color = OmniFeedHint,
                        maxLines = 1,
                        softWrap = false,
                    )
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

        Spacer(Modifier.height(SearchRowBottomGap))
    }
}

/**
 * The story rail — Figma `Recently Post Section` (node 124:52).
 *
 * Four 93 x 105 tiles with an 11 gap. The frame reports itself 405 wide at x=16, which overruns the
 * 415 artboard by 6: the strip is meant to bleed off the right edge. It therefore scrolls sideways
 * rather than being squeezed to fit, which is also what lets the user reach the last tile.
 */
@Composable
private fun StoryStrip(onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ShareMealTile(onClick = onShare)
        StoryImages.forEach { image ->
            Image(
                painter = painterResource(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(93.dp)
                    .height(105.dp),
            )
        }
    }
}

/** The first tile — a 46 avatar with a plus button and caption stacked under it. */
@Composable
private fun ShareMealTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(93.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OmniFeedSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // `Meal Image and Text` is 81 x 92 inside the 93 x 105 tile: 6 a side, 6.5 at the top.
        Box(
            modifier = Modifier
                .width(81.dp)
                .height(92.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.feed_story_share_avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 0.5.dp)
                    .size(46.dp)
                    .clip(CircleShape),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 38.dp)
                    .width(81.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_feed_share_plus),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
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
 */
@Composable
private fun FeedPost(
    post: Post,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
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
                            .background(OmniFeedSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (post.authorPhotoUrl != null) {
                            coil3.compose.AsyncImage(
                                model = post.authorPhotoUrl,
                                contentDescription = null,
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

                Text(
                    text = relativeTimeOf(post.createdAt),
                    style = FeedType.Meta12,
                    color = OmniFeedTimestamp,
                    maxLines = 1,
                    softWrap = false,
                )
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
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
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
                        text = "0",
                        icon = R.drawable.ic_feed_repost,
                        contentDescription = "Repost",
                        emphasized = false,
                        onClick = onShare,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_feed_share),
                    contentDescription = "Share",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onShare),
                )
            }
        }
    }
}

/**
 * An 86 x 39 grey pill holding a count and its 20dp icon, 4 apart — now a button.
 *
 * [emphasized] is the liked state: the pill stays the design's grey (the design never drew a liked
 * variant) but the count and icon tint to the SOS red, the palette's one emphasis colour, so the
 * state is visible without inventing a new surface.
 */
@Composable
private fun ActionPill(
    text: String,
    icon: Int,
    contentDescription: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(86.dp)
            .height(39.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(OmniFeedSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.offset(x = 0.5.dp, y = 0.5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = text,
                style = FeedType.ActionValue,
                color = if (emphasized) OmniSosTrack else OmniInk,
                maxLines = 1,
                softWrap = false,
            )
            Image(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                colorFilter = if (emphasized) {
                    ColorFilter.tint(OmniSosTrack)
                } else {
                    null
                },
            )
        }
    }
}

private val StoryImages = listOf(
    R.drawable.feed_story_1,
    R.drawable.feed_story_2,
    R.drawable.feed_story_3,
)

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

@DevicePreviews
@Composable
private fun FeedScreenPreview() {
    OmniTheme { FeedScreen() }
}
