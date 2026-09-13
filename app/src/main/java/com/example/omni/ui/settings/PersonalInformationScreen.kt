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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType

/**
 * Personal Information — the page behind the first row of Settings' "Account Details" group.
 *
 * That group used to hold two rows both reading "Personal Information", and neither of them went
 * anywhere: the row called an `onAccountAction` callback that `MainActivity` never passed, so the
 * default empty lambda ran and the tap did nothing. This page and [SecurityScreen] are the two
 * destinations those rows now have, split the way the account itself is split — the profile document
 * here, the sign-in credentials there.
 *
 * **This frame does not exist in Figma**, so `UI_ARCHITECTURE.md` §6 rule 11 applies and every part is
 * one the design already owns: the back row, gutter and intro are [SavedEmergenciesScreen]'s, the fields
 * are its labelled #F5F5F5 boxes, the gender chips are the settings card's tray inverted onto ink the
 * way the log-out button is, and the commitment button is [GoalsScreen]'s three-state Save. No new type
 * and no new colour.
 *
 * There is no bottom bar, like the other pages inside Settings, and both the back button and the system
 * back return there.
 */
@Composable
fun PersonalInformationScreen(
    state: PersonalInfoUiState = PreviewPersonalInfo,
    onBack: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onDayChange: (String) -> Unit = {},
    onMonthChange: (String) -> Unit = {},
    onYearChange: (String) -> Unit = {},
    onGenderChange: (String) -> Unit = {},
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
                .imePadding()
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
                    text = "Personal Information",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(IntroTop))

            Text(
                text = "Your name is what the feed, your posts and anyone you message see. Your " +
                    "birthday and gender stay on your profile — nothing else in Omni reads them yet.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )

            Spacer(Modifier.height(ListTop))

            if (state.loading) {
                Text(
                    text = "Loading your details…",
                    style = SettingsType.RowSubtitle,
                    color = OmniFeedHint,
                )
            } else {
                AccountField(
                    label = "Name",
                    value = state.draft.name,
                    onValueChange = onNameChange,
                    placeholder = "Sayed Mahir",
                )

                Spacer(Modifier.height(FieldGap))

                BirthdayRow(
                    state = state,
                    onDayChange = onDayChange,
                    onMonthChange = onMonthChange,
                    onYearChange = onYearChange,
                )

                Spacer(Modifier.height(FieldGap))

                GenderRow(selected = state.draft.gender, onSelect = onGenderChange)

                if (state.error != null) {
                    Spacer(Modifier.height(NoticeTop))
                    Text(
                        text = state.error,
                        style = HomeType.CardFootnoteWrapped,
                        color = OmniAlertRed,
                    )
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
 * The birthday — three boxes in the order the label spells out.
 *
 * DD / MM / YYYY is written into the label rather than left to the boxes' order, because that order is
 * exactly the ambiguity a single date field would have had: a reader who expects MM first has to be told,
 * once, and then the three boxes cannot be misread. The year is twice as wide as the other two, which is
 * the proportion of what they hold.
 *
 * The "that isn't a date" line appears only once something has been typed — an empty birthday is a
 * legitimate answer, not an error, and greeting a page with a complaint about a field nobody has touched
 * is the thing §6 rule 10 is about.
 */
@Composable
private fun BirthdayRow(
    state: PersonalInfoUiState,
    onDayChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(
            text = "Date of birth  ·  DD / MM / YYYY",
            style = SettingsType.GroupLabel,
            color = OmniSetGroupLabel,
            maxLines = 1,
            softWrap = false,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(DateGap)) {
            DateBox(
                value = state.draft.day,
                onValueChange = onDayChange,
                placeholder = "DD",
                modifier = Modifier.weight(1f),
            )
            DateBox(
                value = state.draft.month,
                onValueChange = onMonthChange,
                placeholder = "MM",
                modifier = Modifier.weight(1f),
            )
            DateBox(
                value = state.draft.year,
                onValueChange = onYearChange,
                placeholder = "YYYY",
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(YearWeight),
            )
        }

        if (!state.draft.dobValid) {
            Text(
                text = "That isn't a date you could have been born on.",
                style = SettingsType.RowSubtitle,
                color = OmniAlertRed,
                maxLines = 1,
            )
        }
    }
}

/**
 * The gender chips — the settings tray, inverted for the chosen one.
 *
 * They wrap onto two rows of two rather than scrolling sideways: four short words fit the 415dp artboard
 * comfortably at two per row, and a horizontal scroller would hide options behind an edge with no
 * indication that there are any (`UI_ARCHITECTURE.md` §6 rule 8).
 */
@Composable
private fun GenderRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(
            text = "Gender",
            style = SettingsType.GroupLabel,
            color = OmniSetGroupLabel,
            maxLines = 1,
            softWrap = false,
        )

        GenderOptions.chunked(ChipsPerRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                row.forEach { option ->
                    GenderChip(
                        label = option,
                        chosen = selected.equals(option, ignoreCase = true),
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short final row at the same chip width as a full one, rather than stretching
                // two chips across the page.
                repeat(ChipsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Text(
            text = "Tap the chosen one again to clear it.",
            style = SettingsType.RowSubtitle,
            color = OmniSetRowSubtitle,
            maxLines = 1,
        )
    }
}

@Composable
private fun GenderChip(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(ChipHeight)
            .clip(RoundedCornerShape(ChipCorner))
            .background(if (chosen) OmniInk else OmniSetCardSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SettingsType.RowTitle,
            color = if (chosen) OmniOnInk else OmniSetRowTitle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The one thing on the page that writes — [GoalsScreen]'s button, with one extra reason to stay grey.
 *
 * Three labels rather than two, because "there is nothing to save" and "this button is broken" must not
 * look the same: with no pending edit it reads **Saved** in the log-out button's disabled grey, and an
 * edit turns it into a live **Save Changes** in ink. A half-typed birthday keeps it grey while still
 * reading "Save Changes", which is the honest pair — there *is* something to save, and it is not yet
 * something that can be.
 */
@Composable
private fun SaveButton(
    state: PersonalInfoUiState,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(if (state.canSave) OmniInk else OmniFeedHint)
            .clickable(enabled = state.canSave, onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                state.saving -> "Saving…"
                state.dirty -> "Save Changes"
                else -> "Saved"
            },
            style = HomeType.HeroButton,
            color = OmniOnInk,
            maxLines = 1,
        )
    }
}

/** One labelled field — [SavedEmergenciesScreen]'s box, to the dp. */
@Composable
private fun AccountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(
            text = label,
            style = SettingsType.GroupLabel,
            color = OmniSetGroupLabel,
            maxLines = 1,
            softWrap = false,
        )
        FieldBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
            imeAction = imeAction,
        )
    }
}

/** The same box without a label of its own — what the three date boxes are. */
@Composable
private fun DateBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
) {
    FieldBox(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
        imeAction = imeAction,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(RoundedCornerShape(FieldCorner))
            .background(OmniFieldSurface)
            .padding(horizontal = FieldPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FeedType.PostBody.copy(color = OmniInk, textAlign = textAlign),
            cursorBrush = SolidColor(OmniInk),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            decorationBox = { inner ->
                // Both children in one Box so the hint sits behind the caret rather than beside it.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (textAlign == TextAlign.Center) {
                        Alignment.Center
                    } else {
                        Alignment.CenterStart
                    },
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = FeedType.PostBody,
                            color = OmniPlaceholder,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame, so every number is borrowed: the gutter, back row, fields and button are
// SavedEmergenciesScreen's and GoalsScreen's to the dp.

private val PagePadding = 16.dp

private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val IntroTop = 18.dp
private val ListTop = 24.dp
private val FieldGap = 18.dp
private val LabelGap = 6.dp
private val FieldHeight = 46.dp
private val FieldCorner = 8.dp
private val FieldPadding = 14.dp
private val DateGap = 10.dp

/** The chip tray's own row: the settings card's radius, one line of `RowTitle` with room around it. */
private val ChipHeight = 42.dp
private val ChipCorner = 8.dp
private val ChipGap = 10.dp
private const val ChipsPerRow = 2

/** "YYYY" is twice the characters of "DD", so its box is twice the share of the row. */
private const val YearWeight = 2f

private val NoticeTop = 14.dp
private val SaveTop = 28.dp
private val DiscardTop = 14.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Breathing room so the button clears the gesture area — there is no bottom bar on this page. */
private val ContentBottomGap = 32.dp

/** What the previews render, and the screen's own default: a filled-in profile, which is the normal one. */
val PreviewPersonalInfo = PersonalInfoUiState(
    draft = PersonalDraft(
        name = "Sayed Mahir",
        day = "14",
        month = "03",
        year = "1999",
        gender = "Male",
    ),
    stored = PersonalDraft(
        name = "Sayed Mahir",
        day = "14",
        month = "03",
        year = "1999",
        gender = "Male",
    ),
    loading = false,
)

@DevicePreviews
@Composable
private fun PersonalInformationScreenPreview() {
    OmniTheme { PersonalInformationScreen() }
}

@DevicePreviews
@Composable
private fun PersonalInformationScreenEmptyPreview() {
    OmniTheme {
        PersonalInformationScreen(state = PersonalInfoUiState(loading = false))
    }
}
