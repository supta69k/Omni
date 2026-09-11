package com.example.omni.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.data.model.Story
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniFeedVerified
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale

/**
 * The full-screen story viewer — the Facebook/Instagram shape the brief asked for.
 *
 * The image fills the screen; the chrome is overlaid: progress segments across the top (one per
 * story in the author's tile, the watched ones filled), the author row under them, the caption at
 * the foot, and tap zones — right third advances, left third rewinds, the X closes. Everything is
 * built from the app's own parts: the ring colours and type come from the palette, the avatar
 * treatment from the feed's post row, and the X from the settings chevron mirrored (the same trick
 * the guides screen uses).
 *
 * **No DesignFrame.** This screen is deliberately *not* on the 415dp artboard: a story is a
 * photograph at the device's full size, and scaling a full-bleed image by the artboard ratio would
 * crop every story the same wrong way on every phone. The one precedent is the camera/gallery
 * class of screens, which every design system exempts. All chrome still uses the palette's tokens.
 */
@Composable
fun StoryViewer(
    authorName: String,
    stories: List<Story>,
    storyIndex: Int,
    isMine: Boolean,
    onAdvance: () -> Unit,
    onRewind: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit = {},
    /**
     * Opens the story's author's public page (§8) — carried with **their** uid, taken from the story
     * on screen rather than from the session, so it is the person whose photograph is being looked at.
     * Drawn on their own story too: the settings link is elsewhere, and a name that is tappable for
     * everyone else and dead for me reads as a bug.
     */
    onOpenProfile: (String) -> Unit = {},
) {
    val story = stories.getOrNull(storyIndex)

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (story != null) {
            AsyncImage(
                model = story.imageUrl,
                contentDescription = "Story by $authorName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The chrome, top to bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Progress segments: one per story in this tile, the watched ones filled white, the
            // current one a filled-with-motion bar in the FB convention simplified to a fill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(stories.size) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (index <= storyIndex) OmniOnInk else StoryUnwatched),
                    )
                }
            }

            // The author row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Avatar and name in one tap target rather than two — the whole identity block opens
                // the person, which is the affordance every story UI teaches. Nested inside the
                // chrome row, so the 8dp gaps and the trailing weight are exactly what they were.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.noRipple(
                        onClick = {
                            val authorId = story?.authorId
                            if (!authorId.isNullOrBlank()) {
                                // The viewer is full-screen and outside the router's stack; leaving
                                // it open under the profile would strand the story on top.
                                onClose()
                                onOpenProfile(authorId)
                            }
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StoryUnwatched),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (story?.authorPhotoUrl != null) {
                            AsyncImage(
                                model = story.authorPhotoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = authorName.take(1).uppercase(),
                                style = FeedType.AuthorName,
                                color = OmniOnInk,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = authorName,
                        style = FeedType.AuthorName,
                        color = OmniOnInk,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isMine) {
                    // The author's own trash — drawn only on their own story, the only one the
                    // rules let them delete.
                    Image(
                        painter = painterResource(R.drawable.ic_feed_share_plus),
                        contentDescription = "Delete your story",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f)
                            .noRipple(onClick = onDelete),
                    )
                }
                // The X — the settings chevron rotated to its side, the guides screen's mirror
                // trick rotated once more. A glyph the font would have was not worth an asset.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .noRipple(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_set_arrow_right),
                        contentDescription = "Close",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f)
                            .scale(scaleX = 1f, scaleY = -1f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // The caption, over the image's foot with the app's own surface tint for legibility.
            if (!story?.caption.isNullOrBlank()) {
                Text(
                    // Smart-cast: a caption that is neither null nor blank cannot have come from a
                    // null story, so the safe call the compiler flagged here was already redundant.
                    text = story.caption,
                    style = FeedType.PostBody,
                    color = OmniOnInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(48.dp))
        }

        // The tap zones. Transparent, no ripple: the photograph is the button, the way every
        // stories UI has done it since the convention settled.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .noRipple(onClick = onRewind),
            )
            Box(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .noRipple(onClick = onAdvance),
            )
        }
    }
}

/** A tap with no ink — the tap zones and the close affordance are invisible buttons. */
@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** 40% white — the unwatched segments and the placeholder avatar, over any photograph. */
private val StoryUnwatched = Color(0x66FFFFFF)

@DevicePreviews
@Composable
private fun StoryViewerPreview() {
    OmniTheme {
        StoryViewer(
            authorName = "Dr.Ben",
            stories = listOf(
                Story(
                    id = "1",
                    authorId = "ben",
                    authorName = "Dr.Ben",
                    imageUrl = "https://picsum.photos/415/890",
                    caption = "Morning rounds.",
                ),
                Story(
                    id = "2",
                    authorId = "ben",
                    authorName = "Dr.Ben",
                    imageUrl = "https://picsum.photos/415/891",
                ),
            ),
            storyIndex = 0,
            isMine = false,
            onAdvance = {},
            onRewind = {},
            onClose = {},
        )
    }
}
