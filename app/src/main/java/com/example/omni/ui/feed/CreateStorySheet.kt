package com.example.omni.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.Image
import com.example.omni.R
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniScrim
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface

/**
 * How a story gets made — the meal sheet's shape, retargeted at a photo.
 *
 * The flow is pick → caption (optional) → publish. The image is required before the publish button
 * lives: a text-only story is a post, and the strip has no use for one. The picked photo previews
 * in the feed's own image box (255 tall, 14 radius) so the composer and the strip share a visual
 * language, and the picker itself is a lambda into `MainActivity`, the composer's own reason: the
 * read grant a picker hands back is scoped to the process, and the Activity is what survives it.
 *
 * Uploads go through the same Cloudinary pipeline as post images — same preset, same compression —
 * under a `stories/{uid}` folder so the dashboard keeps the two surfaces apart.
 */
@Composable
internal fun CreateStorySheet(
    visible: Boolean,
    image: android.net.Uri?,
    isPublishing: Boolean,
    errorText: String?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onDismiss: () -> Unit,
    onPublish: (caption: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SheetMillis)),
            exit = fadeOut(tween(SheetMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OmniScrim)
                    .noRipple(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(SheetMillis)) { it } + fadeIn(tween(SheetMillis)),
            exit = slideOutVertically(tween(SheetMillis)) { it } + fadeOut(tween(SheetMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            StoryPanel(
                image = image,
                isPublishing = isPublishing,
                errorText = errorText,
                onPickImage = onPickImage,
                onRemoveImage = onRemoveImage,
                onDismiss = onDismiss,
                onPublish = onPublish,
            )
        }
    }
}

@Composable
private fun StoryPanel(
    image: android.net.Uri?,
    isPublishing: Boolean,
    errorText: String?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onDismiss: () -> Unit,
    onPublish: (String?) -> Unit,
) {
    var caption by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(SheetShape)
            .background(OmniSheetSurface)
            .noRipple(onClick = {})
            .navigationBarsPadding()
            .imePadding()
            // The sheet scrolls (§12). Its resting height is about 460dp — grab, heading, a 255dp
            // preview, the caption field and the publish button — and `imePadding` lifts the whole
            // thing by the keyboard's ~300dp the moment the caption is focused. On a 415 x 900
            // artboard that pushed the heading off the top and the publish button behind the IME,
            // which is the "visually broken" layout the brief describes. Scrolling is the fix that
            // does not shrink the preview: the column measures its children unbounded and clamps
            // itself to whatever height is left, so on a tall screen nothing moves at all.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GrabTop))
        Image(
            painter = painterResource(R.drawable.ic_hosp_grab),
            contentDescription = "Close",
            modifier = Modifier
                .width(59.dp)
                .height(5.dp)
                .noRipple(onClick = onDismiss),
        )

        Spacer(Modifier.height(HeadingTop))
        Text(
            text = "Add to your story",
            style = HomeType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(PhotoTop))

        if (image == null) {
            // The picker tile: the feed's image box as a dashed invitation. The border, not a
            // bitmap, is what says "empty" — the palette has no dashed token, and a grey box with
            // a camera line reads truer than inventing one.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PhotoHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OmniFeedSurface)
                    .border(1.dp, OmniFeedHint.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onPickImage),
                contentAlignment = Alignment.Center,
            ) {
                // Glyph over label, both centred on the tile's own axis (§12). The plus used to be
                // absent entirely and the label sat alone as a single non-wrapping line, so a wider
                // font scale clipped it at both gutters. Now it wraps to two centred lines instead,
                // and the whole 255dp tile is the touch target rather than the text's own bounds.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PickerGlyphGap),
                    modifier = Modifier.padding(horizontal = PickerLabelInset),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_feed_share_plus),
                        contentDescription = null,
                        modifier = Modifier.size(PickerGlyphSize),
                    )
                    Text(
                        text = "Tap to choose a photo",
                        style = FeedType.Hint16,
                        color = OmniFeedHint,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Box {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PhotoHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OmniFeedSurface),
                )
                // The remove pill, top-right of the preview — the composer's own convention.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(OmniInk)
                        .noRipple(onClick = onRemoveImage),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        style = HomeType.SectionTitle,
                        color = OmniOnInk,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(CaptionTop))

        // The caption: optional, one line, the comments composer's own field.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(OmniFieldSurface),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = caption,
                onValueChange = { caption = it.take(MaxCaptionLength) },
                singleLine = true,
                textStyle = FeedType.Hint16.copy(color = OmniInk),
                cursorBrush = SolidColor(OmniInk),
                // Placeholder and field in *one* box (§12), the fix [CommentsSheet] already carries.
                // They were siblings here too, with the field boxed at `width(0.dp)`: the hint drew,
                // so the caption row looked present, while the thing you type into had no width and
                // therefore no caret and no hit area.
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (caption.isEmpty()) {
                            Text(
                                text = "Add a caption… (optional)",
                                style = FeedType.Hint16,
                                color = OmniFeedHint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = FeedType.Meta12,
                color = OmniInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(PublishTop))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (image != null && !isPublishing) OmniInk else OmniFeedHint)
                .clickable(enabled = image != null && !isPublishing) { onPublish(caption) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isPublishing) "Publishing…" else "Share to your story",
                style = HomeType.HeroButton,
                color = OmniOnInk,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(SheetBottom))
    }
}

@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** Rounded at the top only, like the sleep, meal and comments sheets. */
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** The hospitals sheet's own gutter and handle inset, borrowed as the other sheets do. */
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp
private val HeadingTop = 22.dp
private val PhotoTop = 18.dp
private val PhotoHeight = 255.dp
private val CaptionTop = 14.dp
private val PublishTop = 18.dp
private val SheetBottom = 20.dp

/** The empty picker tile's glyph, the gap under it, and the inset its label wraps inside (§12). */
private val PickerGlyphSize = 20.dp
private val PickerGlyphGap = 10.dp
private val PickerLabelInset = 24.dp

/** A caption is a line, not a paragraph — the viewer draws it in one line over the photo. */
private const val MaxCaptionLength = 80

/** `SosScreen`'s own reveal timing, the same as every other sheet. */
private const val SheetMillis = 280
