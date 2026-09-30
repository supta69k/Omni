package com.example.omni.ui.sleep

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface

/**
 * The delete confirmation — a small hand-built sheet so removing a night is a deliberate second tap.
 *
 * A sheet rather than a Material3 `AlertDialog` for the entry sheet's own reason: a dialog hoists its
 * content into a separate window, outside [com.example.omni.ui.DesignFrame]'s density, which would leave
 * this panel measured in raw pixels while every other surface on the page is artboard-scaled
 * (`UI_ARCHITECTURE.md` §6 rule 11). It is the entry sheet's surface, shadow and grab handle with two
 * buttons instead of a form — so it adds no new vocabulary.
 *
 * @param date the night awaiting confirmation, or `null` when the sheet is closed. The scaffold slides it
 *   away on `null`, so the date passed to [onConfirm] is captured while it is still non-null.
 */
@Composable
internal fun SleepConfirmSheet(
    date: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OmniSheetScaffold(
        visible = date != null,
        onDismiss = onCancel,
        modifier = modifier,
    ) {
        // Captured while non-null: the scaffold keeps rendering this content through the slide-out after
        // `date` goes null, and the buttons must still know which night they were about to remove.
        val target = date ?: return@OmniSheetScaffold
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
                .clip(SheetShape)
                .background(OmniSheetSurface)
                .sheetNoRipple(onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = SheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(GrabTop))
            Image(
                painter = painterResource(R.drawable.ic_hosp_grab),
                contentDescription = "Close",
                modifier = Modifier.width(59.dp).height(5.dp).sheetNoRipple(onClick = onCancel),
            )

            Spacer(Modifier.height(HeadingTop))
            Text(
                text = "Delete this night?",
                style = HomeType.SectionTitle,
                color = OmniSectionTitle,
                maxLines = 1,
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "This removes the record and clears the hours it added to your dashboard. It " +
                    "can't be undone.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniFootnote,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(ButtonsTop))
            // Delete is the red commitment; Cancel is the quiet way out, wider gap between them than a
            // toolbar so the destructive button is not the one a thumb lands on by default.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .clip(RoundedCornerShape(ButtonCorner))
                    .background(OmniAlertRed)
                    .pressEffect()
                    .clickable { onConfirm(target) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Delete", style = HomeType.HeroButton, color = OmniOnInk, maxLines = 1)
            }

            Spacer(Modifier.height(CancelTop))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .clip(RoundedCornerShape(ButtonCorner))
                    .pressEffect()
                    .clickable(onClick = onCancel),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Keep it", style = HomeType.HeroButton, color = OmniInk, maxLines = 1)
            }

            Spacer(Modifier.height(SheetBottom))
        }
    }
}

// ---- Geometry -----------------------------------------------------------------------------------

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp
private val HeadingTop = 22.dp
private val ButtonsTop = 22.dp
private val CancelTop = 8.dp
private val SheetBottom = 20.dp

private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp
