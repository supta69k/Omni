package com.example.omni.ui.firstaid

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.GuideSeverity
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniCardSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniSosTrack
import com.example.omni.ui.theme.OmniStepsCream
import com.example.omni.ui.theme.OmniWaterTeal
import com.example.omni.ui.theme.OmniTheme

/**
 * The first-aid guide list — the screen Home's "Explore!" opens (Phase 7, BACKEND_PLAN §11).
 *
 * Invented UI in the sense `UI_ARCHITECTURE.md` §6 rule 11 means: Figma has no frame for it, so every
 * part is borrowed from a screen that does have one. The title and count line are the dashboard's
 * section title and footnote; the search field is the feed's search row re-pointed at the guide list
 * (the feed's is a label, this is the same 325-wide grey pill turned into a real field); each card is
 * an update card — the same #F5F5F5 surface, 8dp radius, 15dp gutter — carrying the severity pill the
 * SOS screen's track palette already owns. No new type, no new color, no new shape.
 *
 * The bar-absent [OmniNavItem.Home] is passed as `selected` — same honest read as `SettingScreen`:
 * this is not one of the four tabs, it is a page the hero card opens, so no pill lights.
 *
 * The field's arrow and the system back do the same thing, which is the point of the [BackHandler]:
 * without it the hardware gesture left the app from a page that plainly has a back button, and the two
 * ways out of the same screen disagreed.
 */
@Composable
fun GuidesScreen(
    state: GuidesUiState = GuidesUiState(),
    onQueryChange: (String) -> Unit = {},
    onOpenGuide: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
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
                    .padding(horizontal = ScreenPadding),
            ) {
                Spacer(Modifier.height(HeaderTopGap))

                GuideSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onBack = onBack,
                )

                Spacer(Modifier.height(TitleGap))

                Text(
                    text = "First Aid Guides",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )

                Spacer(Modifier.height(CountGap))

                Text(
                    text = "${state.guideCount} guides · ${state.totalSteps} steps · works offline",
                    style = HomeType.CardFootnote,
                    color = OmniInk,
                    maxLines = 1,
                )

                Spacer(Modifier.height(ListGap))

                if (state.matches.isEmpty()) {
                    // A search that matches nothing must say so. An empty gap under a filled field
                    // reads as a screen that broke rather than a word that isn't in any guide.
                    Text(
                        text = if (state.query.isBlank()) {
                            "The guides are still loading."
                        } else {
                            "No guide mentions “${state.query}”. Try a symptom — burn, choking, bleeding."
                        },
                        style = HomeType.CardFootnoteWrapped,
                        color = OmniInk,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
                        state.matches.forEach { card ->
                            GuideCard(card = card, onClick = { onOpenGuide(card.id) })
                        }
                    }
                }

                Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
            }

            OmniBottomNav(
                selected = OmniNavItem.Home,
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
 * The search row: a 40dp back button, then the feed's own search pill carrying a real field.
 *
 * The back button reuses the settings screen's chevron, mirrored — a "left" arrow is the same asset
 * as the settings row's "right" one, flipped, which is one fewer icon than inventing a new file for.
 */
@Composable
private fun GuideSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SearchRowGap),
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
                    .scaleMirror(),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(SearchHeight)
                .clip(RoundedCornerShape(SearchRadius))
                .background(OmniCardSurface),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(start = SearchIconStart, end = SearchIconEnd),
                horizontalArrangement = Arrangement.spacedBy(SearchIconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_feed_search),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                // Weighted, not `fillMaxWidth`: a Row hands an unweighted child the *whole* row
                // width, so the field measured as wide as the pill and ran out under its right edge.
                GuideQueryField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The search field itself. [BasicTextField] rather than Material's `OutlinedTextField` for the same
 * reason the sheets are hand-built: a Material text field draws its own underline, its own padding and
 * its own typography, none of which are the feed's, and unpicking them costs more than the twelve
 * lines of plain field here.
 */
@Composable
private fun GuideQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The focus is not auto-requested: this screen is opened to browse, and a keyboard jumping up
    // over the list on entry would hide the very cards the user came for.
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = FeedType.Hint16.copy(color = OmniInk),
        cursorBrush = SolidColor(OmniInk),
        decorationBox = { inner ->
            // Both children in one Box so the hint sits behind the caret rather than beside it. The
            // zero-width wrapper this replaced measured the field itself at 0x0, so anything typed
            // into the search row was drawn nowhere.
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search guides",
                        style = FeedType.Hint16,
                        color = SearchPlaceholderColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                inner()
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** One guide in the list — an update card's surface carrying a severity pill and a percent. */
@Composable
private fun GuideCard(card: GuideCardState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniCardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = CardPadding, vertical = CardVerticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(CardTitleGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Ellipsised and weighted so the dot survives a long title: without it the title
                // measured at its full intrinsic width and pushed the severity marker off the card
                // (UI_ARCHITECTURE.md §6 rule 8).
                Text(
                    text = card.title,
                    style = HomeType.CardTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SeverityPill(severity = card.severity)
            }
            Text(
                text = "${card.category} · ${card.stepCount} steps" +
                    if (card.percent > 0) " · ${card.percent}% read" else "",
                style = HomeType.Caption12,
                color = OmniInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "${card.percent}%",
            style = HomeType.CardValue,
            color = OmniCardInk,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The severity marker — a 8dp dot in the SOS palette rather than a pill, so the row's height stays the
 * update card's own and no new shape enters the app. Emergency borrows the SOS track's red, Urgent the
 * cream of the steps card, Minor the teal of the water card — the three accent fills the design owns.
 */
@Composable
private fun SeverityPill(severity: GuideSeverity) {
    Box(
        modifier = Modifier
            .padding(top = SeverityDotTop)
            .size(SeverityDotSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                when (severity) {
                    GuideSeverity.Emergency -> OmniSosTrack
                    GuideSeverity.Urgent -> OmniStepsCream
                    GuideSeverity.Minor -> OmniWaterTeal
                },
            ),
    )
}

/** The settings chevron flipped horizontally — a back arrow without a new asset. */
private fun Modifier.scaleMirror(): Modifier = this.scale(scaleX = -1f, scaleY = 1f)

// ---- Geometry ----------------------------------------------------------------------------------
//
// All borrowed: the gutter and gaps follow the dashboard's section rhythm; the search pill is the
// feed's own 48-high grey pill minus the trailing add button.

private val ScreenPadding = 16.dp
private val HeaderTopGap = 12.dp

/** 10 — the air the feed leaves between its header row and the content under it. */
private val SearchRowGap = 10.dp
private val BackButtonSize = 40.dp
private val SearchHeight = 48.dp
private val SearchRadius = 42.dp
private val SearchIconStart = 19.dp
private val SearchIconEnd = 16.dp
private val SearchIconGap = 6.dp
private val SearchPlaceholderColor = Color(0xFFABABAB)

private val TitleGap = 24.dp
private val CountGap = 4.dp
private val ListGap = 24.dp
private val CardGap = 10.dp
private val CardPadding = 15.dp
private val CardVerticalPadding = 14.dp
private val CardTitleGap = 4.dp
private val SeverityDotSize = 8.dp
private val SeverityDotTop = 6.dp

/** Breathing room so the last card scrolls clear of the floating bar. */
private val ContentBottomGap = 24.dp

@DevicePreviews
@Composable
private fun GuidesScreenPreview() {
    OmniTheme {
        GuidesScreen(
            state = GuidesUiState(
                matches = com.example.omni.data.repo.BundledGuideRepository.list().map {
                    GuideCardState(it.id, it.title, it.category, it.severity, it.steps.size, 0)
                },
                guideCount = com.example.omni.data.repo.BundledGuideRepository.list().size,
                totalSteps = com.example.omni.data.repo.BundledGuideRepository.totalSteps,
            ),
        )
    }
}
