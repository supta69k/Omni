package com.example.omni.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniBody
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniTheme

/**
 * The honest placeholder for a destination whose design does not exist yet.
 *
 * `UI_ARCHITECTURE.md` §2a rule 4: a destination that lands before its design gets a neutral stub
 * carrying its own tab — never a stand-in screen, and never a no-op tap. The header's messages and bell
 * icons are exactly that case: the Figma prototype contains no messages screen and no notifications
 * screen (BACKEND_PLAN §3.6), and they arrive in Phases 10 and 11.
 *
 * It borrows the dashboard's section title and body styles rather than inventing type, and shows the
 * bottom bar so the user is never stranded. Like `SettingScreen`, it passes a bar-absent
 * [OmniNavItem] as `selected`, so no pill lights — which is the truth: this is not one of the four
 * tabs.
 */
@Composable
fun ComingSoonScreen(
    title: String,
    detail: String,
    tab: OmniNavItem,
    onNavigate: (OmniNavItem) -> Unit = {},
) {
    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.width(ContentWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TitleGap),
            ) {
                Text(
                    text = title,
                    style = HomeType.SectionTitle,
                    color = OmniSectionTitle,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = detail,
                    style = HomeType.CardFootnoteWrapped,
                    color = OmniBody,
                    textAlign = TextAlign.Center,
                )
            }

            OmniBottomNav(
                selected = tab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OmniNavBottomGap),
                onSelect = onNavigate,
            )
        }
    }
}

/** Narrow enough that the sentence wraps into a readable column rather than a single long line. */
private val ContentWidth = 260.dp

private val TitleGap = 10.dp

@DevicePreviews
@Composable
private fun ComingSoonScreenPreview() {
    OmniTheme {
        ComingSoonScreen(
            title = "Messages",
            detail = "Direct messages with verified professionals are coming soon.",
            tab = OmniNavItem.Messages,
        )
    }
}
