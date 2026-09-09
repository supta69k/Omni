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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.EmergencyContact
import com.example.omni.data.model.MaxEmergencyContacts
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
 * Saved emergency contacts — the page behind Settings' "Saved Emergencies" row (BACKEND_PLAN §11
 * Phase 9).
 *
 * **This frame does not exist in Figma.** `UI_ARCHITECTURE.md` §6 rule 11 therefore applies: it is
 * built entirely out of parts the design already owns, and nothing here is invented type or colour.
 * The back row is the guide pages' (a mirrored `ic_set_arrow_right` in a 40 hit target), the labels and
 * rows are `SettingScreen`'s own [SettingsType], the contact rows sit in the healthcare card's #F5F5F5
 * tray at radius 8, the fields are the post composer's field on the auth pages' #F5F5F5, and the save
 * button is the auth pages' 50 x 20 ink commitment. The only colour not already on a settings page is
 * [OmniAlertRed], which the SOS track and the auth error slot both use — the one place this app spends
 * red is "this is destructive or urgent", and removing a contact is the former.
 *
 * There is no bottom bar, like the guide detail and the composer: this is a page inside Settings, and
 * both the back button and the system back return there.
 *
 * Each row carries two text links rather than being tappable as a whole. A row that dialled on any tap
 * would open the phone app on grandmother's number every time a thumb brushed the list; "Call" and
 * "Remove" are the settings groups' own `Edit`/`Change` links, and they say which is which.
 */
@Composable
fun SavedEmergenciesScreen(
    state: EmergencyContactsUiState = PreviewState,
    onBack: () -> Unit = {},
    onOpenForm: () -> Unit = {},
    onCancelForm: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onPhoneChange: (String) -> Unit = {},
    onRelationChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCall: (String) -> Unit = {},
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
                    text = "Saved Emergencies",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(IntroTop))

            Text(
                text = "These are the people Omni texts when you hold the SOS slider — with your " +
                    "location, in your own messaging app, so you can see what goes out before it does.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )

            Spacer(Modifier.height(ListTop))

            when {
                state.loading -> Text(
                    text = "Loading your contacts…",
                    style = SettingsType.RowSubtitle,
                    color = OmniFeedHint,
                )

                state.contacts.isEmpty() -> Text(
                    text = "No contacts saved yet. Add the one person you would want called first.",
                    style = HomeType.CardFootnoteWrapped,
                    color = OmniSetRowSubtitle,
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    state.contacts.forEach { contact ->
                        ContactRow(
                            contact = contact,
                            onCall = { onCall(contact.phone) },
                            onDelete = { onDelete(contact.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(FormTop))

            if (state.formOpen) {
                AddContactForm(
                    state = state,
                    onNameChange = onNameChange,
                    onPhoneChange = onPhoneChange,
                    onRelationChange = onRelationChange,
                    onSave = onSave,
                    onCancel = onCancelForm,
                )
            } else {
                AddContactButton(
                    atCapacity = state.atCapacity,
                    onClick = onOpenForm,
                )
            }

            Spacer(Modifier.height(ContentBottomGap))
        }
    }
}

/**
 * One saved contact — the settings row's two lines in the healthcare card's tray.
 *
 * The second line joins the relation and the number with the nutrition page's own middle dot, and both
 * lines are ellipsised inside the row: a pasted number and a relation somebody typed in full are both
 * routinely longer than the column, and clipping mid-glyph would hide that anything was missing
 * (`UI_ARCHITECTURE.md` §6 rule 8).
 */
@Composable
private fun ContactRow(
    contact: EmergencyContact,
    onCall: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner))
            .background(OmniSetCardSurface)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = SettingsType.RowTitle,
                color = OmniSetRowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (contact.relation.isBlank()) {
                    contact.phone
                } else {
                    "${contact.relation} · ${contact.phone}"
                },
                style = SettingsType.RowSubtitle,
                color = OmniSetRowSubtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LinkGap)) {
            Text(
                text = "Call",
                style = SettingsType.RowAction,
                color = OmniSetRowTitle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable(onClick = onCall),
            )
            Text(
                text = "Remove",
                style = SettingsType.RowAction,
                color = OmniAlertRed,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable(onClick = onDelete),
            )
        }
    }
}

/**
 * The add form — three labelled fields and the auth pages' commitment button.
 *
 * Labels above the fields rather than the auth pages' leading icons: this app owns a mail, a lock and a
 * person mark, and nothing that means "relation". Borrowing the doctor icon for "Mum" would be worse
 * than a word.
 *
 * Only the number is required, which is what the model layer enforces on the way back in — a contact
 * with a name and no number cannot be texted, so the button stays inert until there is one.
 */
@Composable
private fun AddContactForm(
    state: EmergencyContactsUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onRelationChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
        ContactField(
            label = "Name",
            value = state.draft.name,
            onValueChange = onNameChange,
            placeholder = "Ammu",
        )
        ContactField(
            label = "Phone",
            value = state.draft.phone,
            onValueChange = onPhoneChange,
            placeholder = "+8801XXXXXXXXX",
            keyboardType = KeyboardType.Phone,
        )
        ContactField(
            label = "Relation",
            value = state.draft.relation,
            onValueChange = onRelationChange,
            placeholder = "Mother",
            imeAction = ImeAction.Done,
        )

        Spacer(Modifier.height(SaveTop))

        val canSave = state.draft.canSave && !state.saving
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
                text = if (state.saving) "Saving…" else "Save Contact",
                style = HomeType.HeroButton,
                color = OmniOnInk,
                maxLines = 1,
            )
        }

        Text(
            text = "Cancel",
            style = SettingsType.RowAction,
            color = OmniSetRowSubtitle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onCancel),
        )
    }
}

/** One labelled field — the post composer's #F5F5F5 box, one line high. */
@Composable
private fun ContactField(
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
        Box(
            modifier = Modifier
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
                textStyle = FeedType.PostBody.copy(color = OmniInk),
                cursorBrush = SolidColor(OmniInk),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                decorationBox = { inner ->
                    // Both children in one Box so the hint sits behind the caret rather than beside it.
                    Box(contentAlignment = Alignment.CenterStart) {
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
}

/**
 * "Add a Contact" — the log-out button's shape, because it is the same kind of thing: the one full-width
 * commitment at the bottom of a settings page.
 *
 * At the ceiling it becomes a sentence instead of a dead button. `UI_ARCHITECTURE.md` §6 rule 10 is the
 * reason: a button that cannot do anything must not look like one.
 */
@Composable
private fun AddContactButton(
    atCapacity: Boolean,
    onClick: () -> Unit,
) {
    if (atCapacity) {
        Text(
            text = "You've saved the maximum of $MaxEmergencyContacts contacts. Remove one to add " +
                "another.",
            style = HomeType.CardFootnoteWrapped,
            color = OmniSetRowSubtitle,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddButtonHeight)
            .clip(RoundedCornerShape(AddButtonCorner))
            .background(OmniInk)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Add a Contact",
            style = SettingsType.LogOut,
            color = OmniOnInk,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame, so every number below is borrowed rather than measured: the gutter and the button are
// SettingScreen's, the back row is ComposePostScreen's, and the vertical rhythm is the settings page's
// own 12/18 pair scaled to a page with fewer blocks on it.

/** SettingScreen's left gutter, mirrored — this page has no per-block right edge to honour. */
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
private val LinkGap = 14.dp
private val FormTop = 28.dp
private val FieldGap = 16.dp
private val LabelGap = 6.dp
private val FieldHeight = 46.dp
private val FieldCorner = 8.dp
private val FieldPadding = 14.dp
private val SaveTop = 6.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** The log-out button's, to the dp. */
private val AddButtonHeight = 46.dp
private val AddButtonCorner = 8.dp

/** Breathing room so the button clears the gesture area — there is no bottom bar on this page. */
private val ContentBottomGap = 32.dp

/**
 * What the previews render, and the screen's own default: two contacts, which is what a real account
 * looks like after thirty seconds of use.
 */
val PreviewState = EmergencyContactsUiState(
    contacts = listOf(
        EmergencyContact("preview-0", "Ammu", "+8801711000000", "Mother"),
        EmergencyContact("preview-1", "Dr Rahman", "+8801811000000", "Physician"),
    ),
    loading = false,
)

@DevicePreviews
@Composable
private fun SavedEmergenciesScreenPreview() {
    OmniTheme { SavedEmergenciesScreen() }
}

@DevicePreviews
@Composable
private fun SavedEmergenciesScreenEmptyPreview() {
    OmniTheme {
        SavedEmergenciesScreen(state = EmergencyContactsUiState(loading = false))
    }
}

@DevicePreviews
@Composable
private fun SavedEmergenciesScreenFormPreview() {
    OmniTheme {
        SavedEmergenciesScreen(
            state = PreviewState.copy(
                formOpen = true,
                draft = ContactDraft(name = "Nafis", phone = "+8801911000000", relation = "Brother"),
            ),
        )
    }
}
