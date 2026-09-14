package com.example.omni.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.theme.OmniScrim

/**
 * The shared envelope for every bottom sheet in the app.
 *
 * Five sheets ([com.example.omni.ui.home.SleepEntrySheet],
 * [com.example.omni.ui.feed.CommentsSheet], [com.example.omni.ui.feed.CreateStorySheet],
 * [com.example.omni.ui.nutrition.MealEntrySheet], [com.example.omni.ui.nutrition.MonthPickerSheet])
 * share an identical outer: a `Box(fillMaxSize)` that holds a fading scrim plus a vertically
 * sliding panel anchored at `BottomCenter`. This composable owns both.
 *
 * What lives *inside* the panel — the column with its shadow, surface, paddings and grab handle —
 * is each sheet's own work and is passed in as [content]. That keeps the panel's portrait
 * pixel-identity exactly what it was: the slide animation, the centring alignment and the scrim
 * are unchanged, so what comes out at the bottom is identical to what the inline version drew.
 *
 * **Landscape.** The panel is capped at [DesignFrameWidth] and centred, which is inert in portrait
 * (the frame is already 415 wide) and turns a sheet into a centred card in landscape rather than
 * a full-width block that would be too wide to look like a sheet. The slide stays vertical so the
 * reveal reads the same as in portrait; the recenter button's slot on SOS is a separate,
 * sheet-specific decision (see [com.example.omni.ui.sos.SosScreen]'s HospitalSheet, which is not a
 * candidate for this scaffold).
 *
 * @param visible whether the sheet is shown; the scrim and the slide are both animated together.
 * @param onDismiss fires when the scrim is tapped. Sheets that need to clear the keyboard before
 *   dismissing should wrap this in their own lambda and pass the wrapper — the scaffold is
 *   unaware of focus or IME.
 * @param content the panel itself. This is rendered inside the slide and (in landscape) capped and
 *   centred; in portrait it slides from the bottom edge exactly as before.
 */
@Composable
fun OmniSheetScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The scrim is its own layer so it fades while the sheet slides, and it swallows the taps
        // meant for the page underneath (a sleep card on Home, a feed post on Feed, etc.).
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SheetMillis)),
            exit = fadeOut(tween(SheetMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OmniScrim)
                    .sheetNoRipple(onClick = onDismiss),
            )
        }

        // The sliding panel. In landscape the panel is capped at the artboard width and centred,
        // so a sheet reads as a card rather than a full-width block on a wide viewport. Portrait
        // is inert (the frame is already 415 wide, so the cap never bites).
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(SheetMillis)) { it } + fadeIn(tween(SheetMillis)),
            exit = slideOutVertically(tween(SheetMillis)) { it } + fadeOut(tween(SheetMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (LocalDesignWindow.current.isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = DesignFrameWidth),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    content()
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    content()
                }
            }
        }
    }
}

/** The sheet slide-fade duration, in ms. Shared by every sheet the scaffold hosts. */
internal const val SheetMillis = 280

/**
 * A `clickable` that does not paint a ripple. Used by the scrim (which is dismiss-on-tap and
 * should not advertise it) and by the panel surface (which swallows taps that would otherwise
 * fall through to the scrim).
 *
 * Lives here so the five sheets do not each redeclare the same private extension.
 */
@Composable
internal fun Modifier.sheetNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)