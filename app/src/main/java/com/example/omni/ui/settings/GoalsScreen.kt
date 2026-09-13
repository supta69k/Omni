package com.example.omni.ui.settings

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.HealthGoals
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniSetToggleKnob
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType

/**
 * Daily Goals — the page behind Settings' "Daily Goals" row.
 *
 * **This frame does not exist in Figma.** `UI_ARCHITECTURE.md` §6 rule 11 therefore applies, and the
 * page is assembled from parts the design already owns: the back row and the page gutter are
 * [SavedEmergenciesScreen]'s, each goal sits in the settings card's #F5F5F5 tray at radius 8 with
 * [SettingsType]'s own two-line row inside it, the reading is the step card's [HomeType.StepsValue] and
 * [HomeType.StepsUnit] pair, the `−`/`+` circles are `SleepEntrySheet`'s stepper, and the button is the
 * auth pages' 50 x 20 ink commitment. No new type, no new colour.
 *
 * **One divergence from that stepper** (§6 rule 9): `SleepEntrySheet` draws its buttons in #F5F5F5 on a
 * white sheet, and here the card underneath is *already* #F5F5F5 — the same fill would make them
 * disappear. They are white instead, which is [OmniSetToggleKnob]: the settings page's own answer to
 * "a control sitting on the grey tray", already drawn twice on the page this one opens from.
 *
 * Steppers rather than text fields, for `SleepEntrySheet`'s reasons: no keyboard over the value, no
 * decimal separator that changes by locale, and no rule for what "abc" means. The range each one moves
 * through is `User.kt`'s, so a goal can never be set to something a card cannot draw.
 *
 * Nothing is written until Save. Forty taps from 10,000 steps to 30,000 would otherwise be forty
 * writes, and the value under the thumb would be the account's real goal at every intermediate number.
 *
 * There is no bottom bar, like the guide detail and the composer: this is a page inside Settings, and
 * both the back button and the system back return there.
 */
@Composable
fun GoalsScreen(
    state: GoalsUiState = GoalsUiState(loading = false),
    onBack: () -> Unit = {},
    onStep: (GoalKind, Boolean) -> Unit = { _, _ -> },
    onSave: () -> Unit = {},
    onDiscard: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = PagePadding),
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
                        contentDescription = "Back to settings",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f),
                    )
                }

                Text(
                    text = "Daily Goals",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(IntroTop))

            Text(
                text = "These are the numbers every card measures your day against. Changing one " +
                    "re-reads today against the new target — nothing you have already logged is " +
                    "rewritten.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )

            Spacer(Modifier.height(ListTop))

            if (state.loading) {
                Text(
                    text = "Loading your goals…",
                    style = SettingsType.RowSubtitle,
                    color = OmniFeedHint,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    state.rows.forEach { row ->
                        GoalCard(row = row, onStep = onStep)
                    }
                }

                Spacer(Modifier.height(SaveTop))

                SaveButton(state = state, onSave = onSave)

                // Offered only while there is something to discard, so the page never carries a link
                // that would do nothing (`UI_ARCHITECTURE.md` §6 rule 10).
                if (state.dirty && !state.saving) {
                    Spacer(Modifier.height(DiscardTop))
                    Text(
                        text = "Discard changes",
                        style = SettingsType.RowAction,
                        color = OmniSetRowSubtitle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(onClick = onDiscard),
                    )
                }
            }

            Spacer(Modifier.height(ContentBottomGap))
        }
    }
}

/**
 * One goal — the settings row's two lines, with the stepper on its own line underneath.
 *
 * The stepper is below the label rather than beside it because "50,000 steps" at
 * [HomeType.StepsValue]'s 21sp needs more of the 355dp card than a title column would leave it. Pushed
 * to the row's two ends with the reading centred, the widest value the ranges allow still has ~100dp of
 * clear space either side, so stepping never shuffles the two buttons sideways
 * (`UI_ARCHITECTURE.md` §6 rule 8).
 */
@Composable
private fun GoalCard(
    row: GoalRow,
    onStep: (GoalKind, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner))
            .background(OmniSetCardSurface)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
    ) {
        Text(
            text = row.title,
            style = SettingsType.RowTitle,
            color = OmniSetRowTitle,
            maxLines = 1,
        )
        Text(
            text = row.subtitle,
            style = SettingsType.RowSubtitle,
            color = OmniSetRowSubtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(StepperTop))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                glyph = "−",
                enabled = row.canDecrease,
                label = "Lower the ${row.title.lowercase()} goal",
                onClick = { onStep(row.kind, false) },
            )

            // The reading and its unit sit on **one baseline**, not in one bottom-aligned box.
            //
            // They were bottom-aligned, and the two do not mean the same thing here: `StepsUnit` is
            // `StepsValue.copy(fontSize = 12.sp)`, so it keeps the 21sp run's 33.29sp line height and
            // a 12sp glyph floats inside a box built for a much larger one. Matching the boxes' feet
            // therefore left the unit's own feet well below the number's — "glasses" sagging under the
            // "13". `alignByBaseline` matches the lines the two are actually written on and lets the
            // boxes fall where they must, which is what the dashboard gets for free by drawing this
            // pair as one annotated string (see `HomeBento`).
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = row.value,
                    style = HomeType.StepsValue,
                    color = OmniCardInk,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                // A measured gap rather than the space character it used to lead with: at 12sp that
                // space was barely 3dp, and it scaled with the *unit's* size rather than with the
                // number it has to stand clear of.
                Spacer(Modifier.width(ValueUnitGap))
                Text(
                    text = row.unit,
                    style = HomeType.StepsUnit,
                    color = OmniFootnote,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
            }

            StepButton(
                glyph = "+",
                enabled = row.canIncrease,
                label = "Raise the ${row.title.lowercase()} goal",
                onClick = { onStep(row.kind, true) },
            )
        }
    }
}

/**
 * One end of a stepper — `SleepEntrySheet`'s circle, inverted onto white so it reads on the grey card.
 *
 * [label] rather than a `contentDescription`: the glyph is the whole of the artwork, and a screen
 * reader announcing "minus" would not say which of five goals it is about. `onClickLabel` puts the
 * sentence on the action, where it belongs.
 */
@Composable
private fun StepButton(
    glyph: String,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(StepButtonSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) OmniSetToggleKnob else OmniSetCardSurface)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = HomeType.SectionTitle,
            color = if (enabled) OmniCardInk else OmniFeedHint,
            maxLines = 1,
        )
    }
}

/**
 * The one thing on the page that writes.
 *
 * Three states rather than two, because "there is nothing to save" and "this button is broken" must not
 * look the same: with no pending edit it says **Saved** in the log-out button's disabled grey, and the
 * moment a stepper moves it becomes a live **Save Goals** in ink. Firestore echoing the write back is
 * what turns it grey again, so the label is reporting the account's real state rather than a flag this
 * screen set for itself.
 */
@Composable
private fun SaveButton(
    state: GoalsUiState,
    onSave: () -> Unit,
) {
    val canSave = state.dirty && !state.saving
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(if (canSave) OmniInk else OmniFeedHint)
            .clickable(enabled = canSave, onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                state.saving -> "Saving…"
                state.dirty -> "Save Goals"
                else -> "Saved"
            },
            style = HomeType.HeroButton,
            color = OmniOnInk,
            maxLines = 1,
        )
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame, so every number is borrowed: the gutter, the back row and the button are
// SavedEmergenciesScreen's to the dp, and the card's own padding is the contact row's.

private val PagePadding = 16.dp

private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val IntroTop = 18.dp
private val ListTop = 24.dp
private val RowGap = 12.dp
private val RowCorner = 8.dp
private val RowPaddingH = 14.dp
private val RowPaddingV = 12.dp

/** The gap between a goal's label and its stepper — the settings groups' own 12/18 rhythm, tighter end. */
private val StepperTop = 12.dp

/**
 * 42, not `SleepEntrySheet`'s 48.
 *
 * That sheet has one stepper on it and room to spare; this page has five stacked, and six dp each is
 * thirty dp of scroll. 42 still clears the 40dp minimum this app uses for its own back button.
 */
private val StepButtonSize = 42.dp

/**
 * Between the number and its unit — a quarter of the number's own 21sp, near enough.
 *
 * Wide enough that "13" and "glasses" read as two things rather than as one long word, narrow enough
 * that they still read as one reading rather than as two columns.
 */
private val ValueUnitGap = 5.dp

private val SaveTop = 28.dp
private val DiscardTop = 14.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Breathing room so the button clears the gesture area — there is no bottom bar on this page. */
private val ContentBottomGap = 32.dp

@DevicePreviews
@Composable
private fun GoalsScreenPreview() {
    OmniTheme { GoalsScreen() }
}

@DevicePreviews
@Composable
private fun GoalsScreenDirtyPreview() {
    OmniTheme {
        GoalsScreen(
            state = GoalsUiState(
                goals = HealthGoals(water = 10, steps = 12_500),
                loading = false,
            ),
        )
    }
}
