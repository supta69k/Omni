package com.example.omni.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.AppNotification
import com.example.omni.data.model.NotificationType
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniHeroPink
import com.example.omni.ui.theme.OmniNutriChipCarbs
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniStepsCream
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.OmniWaterTeal
import com.example.omni.ui.theme.SettingsType

/**
 * The bell's inbox — the page behind the header's notification icon (BACKEND_PLAN §11 Phase 10).
 *
 * **This frame does not exist in Figma.** `UI_ARCHITECTURE.md` §6 rule 11 therefore applies, and every
 * part below is borrowed rather than invented: the back row is `SavedEmergenciesScreen`'s, the rows are
 * the healthcare card's #F5F5F5 tray at radius 8, the two lines inside them are `SettingScreen`'s own
 * [SettingsType] pair, the timestamp is the feed's #8D84F9 `Meta12`, and the "Mark all read" link is the
 * settings groups' `Edit`/`Change` link. The one new *shape* is the 8dp unread dot, which is the header
 * badge's own dot at the size the header already draws it.
 *
 * Unlike the guide detail and the composer this page keeps the bottom bar: it is reached from the
 * header, which is chrome the four tabs all share, so leaving by tapping a tab has to work. It is passed
 * `OmniNavItem.Notifications`, which is not one of the bar's four — so no pill lights, which is the
 * truth: the user is not standing on a tab.
 *
 * A row's whole surface is tappable only because the tap does something — it marks the item read
 * (§6 rule 10). Deep links are Phase 12's: [AppNotification.deeplink] is carried through the model and
 * ignored here rather than being wired to a destination that does not exist yet.
 */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState = PreviewNotifications,
    onBack: () -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
    onOpen: (AppNotification) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

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
                    .statusBarsPadding()
                    .padding(horizontal = PagePadding),
            ) {
                Spacer(Modifier.height(HeaderTopGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            contentDescription = "Back",
                            modifier = Modifier
                                .size(24.dp)
                                .scale(scaleX = -1f, scaleY = 1f),
                        )
                    }

                    Spacer(Modifier.width(BackRowGap))

                    // Weighted so the link keeps its width and the title is what gives way — the
                    // opposite would push "Mark all read" off the frame on a narrow phone.
                    Text(
                        text = "Notifications",
                        style = HomeType.SectionTitle,
                        color = OmniCardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // Absent, not disabled, when there is nothing unread: a link that would do nothing
                    // is not drawn at all (§6 rule 10).
                    if (state.hasUnread) {
                        Text(
                            text = "Mark all read",
                            style = SettingsType.RowAction,
                            color = OmniSetRowTitle,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clickable(onClick = onMarkAllRead),
                        )
                    }
                }

                Spacer(Modifier.height(ListTop))

                when {
                    state.loading -> Text(
                        text = "Loading…",
                        style = SettingsType.RowSubtitle,
                        color = OmniFeedHint,
                    )

                    state.items.isEmpty() -> Text(
                        text = "Nothing here yet. Replies to your posts, verification updates and " +
                            "your own SOS records all land on this page.",
                        style = HomeType.CardFootnoteWrapped,
                        color = OmniSetRowSubtitle,
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                        state.items.forEach { item ->
                            NotificationRow(item = item, onClick = { onOpen(item) })
                        }
                    }
                }

                Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
            }

            OmniBottomNav(
                selected = OmniNavItem.Notifications,
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
 * One item.
 *
 * Unread is carried twice on purpose — the tray is filled *and* a dot is drawn. Colour alone would be
 * the only signal for someone who cannot see the difference between #F5F5F5 and white, and a dot alone
 * is 8dp of a 415dp frame; together they read at a glance and survive a greyscale screenshot.
 *
 * The body is two lines and the title one, both ellipsised: the model clamps them on the way in, but a
 * 160-character body is still four lines of this type, and the row's height is what the list's rhythm
 * is built on (§6 rule 8).
 */
@Composable
private fun NotificationRow(
    item: AppNotification,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner))
            .background(if (item.read) Color.Transparent else OmniSetCardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(DotGap),
    ) {
        Box(
            modifier = Modifier
                .padding(top = DotTop)
                .size(DotSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (item.read) Color.Transparent else accentOf(item.type)),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = SettingsType.RowTitle,
                color = OmniSetRowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(TitleGap))

            Text(
                text = item.body,
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(TimeGap))

            Text(
                text = relativeTimeOf(item.createdAt),
                style = FeedType.Meta12,
                color = OmniFeedTimestamp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The dot's colour, which is the only thing [NotificationType] decides.
 *
 * Every value is already on a screen in this app — the SOS track's red, the settings toggles' purple and
 * pink, the water card's teal, the step card's cream. Nothing here is a new hue, and nothing is a new
 * *meaning* either: red is the one this app already spends on "urgent".
 */
private fun accentOf(type: NotificationType): Color = when (type) {
    NotificationType.SOS -> OmniAlertRed
    NotificationType.MESSAGE -> OmniWaterTeal
    NotificationType.VERIFICATION -> OmniStepsCream
    NotificationType.COMMENT -> OmniNutriChipCarbs
    NotificationType.LIKE -> OmniHeroPink
    NotificationType.FOLLOW -> OmniAuthHeading
    NotificationType.SYSTEM -> OmniFeedHint
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame. The gutter, the back row and the tray are `SavedEmergenciesScreen`'s to the dp; the
// dot is the header badge's; the vertical rhythm inside a row is the settings row's 2dp title/subtitle
// pair with one extra step under the body for the timestamp.

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val ListTop = 20.dp
private val RowGap = 10.dp
private val RowCorner = 8.dp
private val RowPaddingH = 14.dp
private val RowPaddingV = 12.dp
private val DotSize = 8.dp
private val DotGap = 10.dp

/** Nudged down to sit on the title's optical centre rather than on the top of its line box. */
private val DotTop = 6.dp

private val TitleGap = 2.dp
private val TimeGap = 6.dp

/** Clears the floating bar, matching every other page that keeps one. */
private val ContentBottomGap = 24.dp

/**
 * What the previews render, and the screen's own default — one unread and one read, because the
 * difference between those two rows is the whole design.
 */
val PreviewNotifications = NotificationsUiState(
    items = listOf(
        AppNotification(
            id = "preview-0",
            type = NotificationType.SOS,
            title = "SOS recorded",
            body = "Your alert was saved with your location. 2 contacts were opened in Messages.",
            read = false,
            createdAt = System.currentTimeMillis() - 4 * 60_000L,
        ),
        AppNotification(
            id = "preview-1",
            type = NotificationType.SYSTEM,
            title = "Welcome to Omni",
            body = "Track water, steps and meals, and keep the first-aid guides on your phone.",
            read = true,
            createdAt = System.currentTimeMillis() - 26 * 60 * 60_000L,
        ),
    ),
    loading = false,
)

@DevicePreviews
@Composable
private fun NotificationsScreenPreview() {
    OmniTheme { NotificationsScreen() }
}

@DevicePreviews
@Composable
private fun NotificationsScreenEmptyPreview() {
    OmniTheme { NotificationsScreen(state = NotificationsUiState(loading = false)) }
}
