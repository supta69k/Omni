package com.example.omni.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniTheme

/**
 * Compose a post — Phase 8's new-post screen (BACKEND_PLAN §11).
 *
 * Text-only for this phase: the plan's image upload (compress to 1080px, Storage
 * `posts/{uid}/{postId}.jpg`) arrives with the Storage wiring, and a composer that cannot yet attach
 * a photo is honest about it by simply not offering to. Everything visual is borrowed: the header is
 * the guide screens' back row, the field is the comments sheet's composer scaled up to a page, and
 * the button is the auth pages' ink commitment.
 *
 * No bottom bar, like the guide detail: this is a page inside the feed, and the back button plus the
 * system back both return there.
 */
@Composable
fun ComposePostScreen(
    onPost: (String) -> Unit,
    onBack: () -> Unit,
    isPosting: Boolean = false,
) {
    var draft by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = ScreenPadding),
        ) {
            Spacer(Modifier.height(HeaderTopGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BackRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(BackButtonSize)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_set_arrow_right),
                        contentDescription = "Back to the feed",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f),
                    )
                }

                Text(
                    text = "New Post",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(FieldTop))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .clip(RoundedCornerShape(FieldCorner))
                    .background(OmniFieldSurface),
                contentAlignment = Alignment.TopStart,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MaxPostLength) },
                    textStyle = FeedType.PostBody.copy(color = OmniInk),
                    cursorBrush = SolidColor(OmniInk),
                    decorationBox = { inner ->
                        // Both children in one Box so the hint sits behind the caret rather than
                        // beside it. The zero-width wrapper this replaced measured the field itself
                        // at 0x0, which drew the typed text nowhere.
                        Box(Modifier.fillMaxSize()) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Share a health tip, a meal, or something you learned…",
                                    style = FeedType.PostBody,
                                    color = OmniFeedHint,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = FieldPadding, vertical = FieldPadding),
                )
            }

            Text(
                text = "${draft.length} / $MaxPostLength",
                style = FeedType.Meta12,
                color = OmniFeedHint,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = CounterTop),
            )

            // A fixed gap, not `weight(1f)`. A scrolling Column measures its children against an
            // infinite height, so weights resolve to zero and the button silently sat against the
            // counter instead of the bottom of the page.
            Spacer(Modifier.height(ButtonTop))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .clip(RoundedCornerShape(ButtonCorner))
                    .background(
                        if (draft.isBlank() || isPosting) OmniFeedHint else OmniInk,
                    )
                    .clickable(enabled = draft.isNotBlank() && !isPosting) {
                        onPost(draft)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isPosting) "Posting…" else "Post",
                    style = HomeType.HeroButton,
                    color = OmniOnInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
        }
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
private val ScreenPadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val FieldTop = 24.dp
private val FieldHeight = 280.dp
private val FieldCorner = 8.dp
private val FieldPadding = 15.dp
private val CounterTop = 6.dp
private val ButtonTop = 28.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Breathing room below the button so it clears the gesture area. */
private val ContentBottomGap = 24.dp

/** The rules' own post-body ceiling (`body.size() <= 2000`). */
private const val MaxPostLength = 2000

@DevicePreviews
@Composable
private fun ComposePostScreenPreview() {
    OmniTheme {
        ComposePostScreen(onPost = {}, onBack = {})
    }
}
