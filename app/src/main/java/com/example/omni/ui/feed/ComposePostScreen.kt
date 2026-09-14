package com.example.omni.ui.feed

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAuthError
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniTheme

/**
 * Compose a post — Phase 8's new-post screen (BACKEND_PLAN §11), now with the photo.
 *
 * Everything visual is borrowed, per `UI_ARCHITECTURE.md` §6 rule 11: the header is the guide
 * screens' back row, the caption field is the comments sheet's composer scaled up to a page, the
 * attach tile is that same field surface with the nutrition screen's plus on it, and the button is
 * the auth pages' ink commitment. The picked photo is previewed in the feed's own image box — 255
 * tall, cropped, 14 rounded — so what the composer shows is what the post will show, including how
 * the crop treats a tall photo.
 *
 * The remove chip is the same plus turned 45°, which is the one piece of vocabulary the design does
 * not own an icon for. Tapping the photo itself replaces it; the chip removes it. Both are real
 * actions, so both are honest taps (§6 rule 10).
 *
 * **The picker is not opened here.** [onPickImage] is a lambda into `MainActivity` for the reason
 * §6 rule 10 gives for the step permission: the Activity is the only thing holding an
 * `ActivityResultRegistry`, and a screen that built its own launcher would also throw in
 * `@DevicePreviews`, where no registry owner is provided.
 *
 * No bottom bar, like the guide detail: this is a page inside the feed, and the back button plus the
 * system back both return there.
 *
 * @param image the picked photo, or `null` before one is picked.
 * @param errorText the last failure's sentence, shown above the button; `null` when nothing failed.
 *   The screen stays put on a failure rather than navigating, so the draft and the photo survive it.
 */
@Composable
fun ComposePostScreen(
    onPost: (String) -> Unit,
    onBack: () -> Unit,
    image: Uri? = null,
    onPickImage: () -> Unit = {},
    onRemoveImage: () -> Unit = {},
    errorText: String? = null,
    isPosting: Boolean = false,
) {
    var draft by remember { mutableStateOf("") }

    // A photo with no caption is a post. The field's own emptiness is no longer the whole story.
    val canPost = (draft.isNotBlank() || image != null) && !isPosting

    BackHandler(onBack = onBack)

    DesignFrame {
        // A reading-and-writing form: cap the page at the artboard width and centre it. Portrait
        // stays byte-identical (the frame is already 415 wide, so the cap and the centring Box are
        // both inert); landscape centres the composer in the wide viewport instead of stretching
        // every text input to the long edge. No orientation branch needed.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = DesignFrameWidth)
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

            Spacer(Modifier.height(PhotoTop))

            if (image == null) {
                AttachPhotoTile(enabled = !isPosting, onClick = onPickImage)
            } else {
                PickedPhoto(
                    image = image,
                    enabled = !isPosting,
                    onReplace = onPickImage,
                    onRemove = onRemoveImage,
                )
            }

            // The failure line. It sits directly above the button because that is what the user just
            // pressed, and it replaces the old behaviour of navigating back to the feed as though a
            // post that never landed had landed.
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = FeedType.Meta12,
                    color = OmniAuthError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ErrorTop),
                )
            }

            // A fixed gap, not `weight(1f)`. A scrolling Column measures its children against an
            // infinite height, so weights resolve to zero and the button silently sat against the
            // counter instead of the bottom of the page.
            Spacer(Modifier.height(ButtonTop))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .clip(RoundedCornerShape(ButtonCorner))
                    .background(if (canPost) OmniInk else OmniFeedHint)
                    .clickable(enabled = canPost) { onPost(draft) },
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
}

/** The empty state: the caption field's own surface, with the nutrition screen's plus on it. */
@Composable
private fun AttachPhotoTile(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AttachHeight)
            .clip(RoundedCornerShape(FieldCorner))
            .background(OmniFieldSurface)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_nutri_plus),
            contentDescription = null,
            modifier = Modifier.size(AttachIconSize),
        )
        Spacer(Modifier.width(AttachIconGap))
        Text(
            text = "Add a photo",
            style = FeedType.Hint16,
            color = OmniPlaceholder,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The picked photo in the feed's own image box, with the remove chip riding its top corner.
 *
 * `Crop` and 255 are copied from `FeedPost` on purpose: a preview at some other height would crop a
 * portrait photo differently from the post it is previewing, which is a preview that lies.
 */
@Composable
private fun PickedPhoto(
    image: Uri,
    enabled: Boolean,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = image,
            contentDescription = "The photo attached to this post",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(PhotoHeight)
                .clip(RoundedCornerShape(PhotoCorner))
                .background(OmniFeedSurface)
                .clickable(enabled = enabled, onClick = onReplace),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(RemoveInset)
                .shadow(
                    elevation = RemoveElevation,
                    shape = CircleShape,
                    ambientColor = OmniSheetShadow,
                    spotColor = OmniSheetShadow,
                )
                .clip(CircleShape)
                .background(OmniBackground)
                .clickable(enabled = enabled, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nutri_plus),
                contentDescription = "Remove the photo",
                modifier = Modifier
                    .size(RemoveSize)
                    .padding(RemoveIconInset)
                    // The design owns no close icon. Its plus, turned 45°, is one.
                    .rotate(45f),
            )
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

/** The photo block: a gap off the counter, then either the attach tile or the preview. */
private val PhotoTop = 18.dp
private val AttachHeight = 56.dp
private val AttachIconSize = 20.dp
private val AttachIconGap = 10.dp

/** `FeedPost`'s own image box, to the dp — see [PickedPhoto] for why it is copied and not chosen. */
private val PhotoHeight = 255.dp
private val PhotoCorner = 14.dp

private val RemoveInset = 10.dp
private val RemoveSize = 32.dp
private val RemoveIconInset = 8.dp
private val RemoveElevation = 4.dp

private val ErrorTop = 12.dp

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
