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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Profession
import com.example.omni.data.model.VerificationRequest
import com.example.omni.data.model.VerificationStatus
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAuthError
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Get Verified — the page behind Settings' "Apply for Verification" button (Phase 12).
 *
 * **This frame does not exist in Figma**, so `UI_ARCHITECTURE.md` §6 rule 11 applies and every part
 * is borrowed from a page the design does own: the gutter, back row and intro are
 * [SavedEmergenciesScreen]'s to the dp, the status card and the labelled field are its #F5F5F5 tray
 * and one-line box, the two profession chips are the feed's segment pills in the settings card's
 * white-on-grey (the same inversion [GoalsScreen] documents for its steppers), and the submit button
 * is the auth pages' 50 x 20 ink commitment. No new type, no new colour.
 *
 * The page has **three faces, never two at once**, because an application has three states and each
 * one belongs to a different person: a form while it is the applicant's turn, a review card while it
 * is the reviewer's, and a plain confirmation once the badge is granted. §6 rule 10 is why the form
 * is not simply disabled in the middle state — a greyed-out field that will never accept input is a
 * control pretending to be one.
 *
 * **One divergence from BACKEND_PLAN Phase 12** (§6 rule 9, recorded because it is a real gap): the
 * plan's form has a **certificate upload** to `certificates/{uid}/…`, a Storage path its §8 keeps
 * privately readable. This app has no Firebase Storage — every image goes through Cloudinary's
 * unsigned upload, which returns a *public* URL — and a licence certificate carries a full name and a
 * registration number. Putting one at a public URL to satisfy a checklist would be a worse outcome
 * than not collecting it, so the form takes the registration number and the reviewer asks for
 * documents out of band. The field is one `MediaRepository.upload` call away when a private bucket
 * exists.
 */
@Composable
fun VerificationScreen(
    state: VerificationUiState = VerificationUiState(loading = false),
    onBack: () -> Unit = {},
    onProfessionChange: (Profession) -> Unit = {},
    onLicenseNumberChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        // A pure form: cap the scroll column at the artboard width and centre it. Portrait stays
        // byte-identical (the frame is already 415 wide, so the cap and the centring Box are both
        // inert); landscape centres the form in the wide viewport instead of stranding it at the left
        // gutter. No orientation branch needed.
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
                        .pressEffect()
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
                    text = "Get Verified",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(IntroTop))

            Text(
                text = "A verified badge tells everyone reading your posts that a human being " +
                    "checked your registration. Only a reviewer can grant it — nothing on this " +
                    "page writes it for you.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )

            Spacer(Modifier.height(ListTop))

            when {
                state.loading -> Text(
                    text = "Loading your application…",
                    style = SettingsType.RowSubtitle,
                    color = OmniFeedHint,
                )

                state.verified -> StatusCard(
                    title = "You are verified",
                    body = "Your posts carry the badge, and users can start a chat with you from " +
                        "the messages page.",
                )

                !state.editable -> PendingCard(request = state.request)

                else -> ApplicationForm(
                    state = state,
                    onProfessionChange = onProfessionChange,
                    onLicenseNumberChange = onLicenseNumberChange,
                    onSubmit = onSubmit,
                )
            }

            Spacer(Modifier.height(ContentBottomGap))
        }
        }
    }
}

/**
 * The form — the applicant's turn.
 *
 * A rejection's reviewer note is printed above it rather than below the button: it is the reason the
 * form is on screen a second time, and an applicant who has to scroll past their own retyped answers
 * to find out what was wrong will retype the same thing.
 */
@Composable
private fun ApplicationForm(
    state: VerificationUiState,
    onProfessionChange: (Profession) -> Unit,
    onLicenseNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val rejection = state.request?.takeIf { it.status == VerificationStatus.REJECTED }

    Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
        if (rejection != null) {
            StatusCard(
                title = "Not approved",
                body = rejection.note
                    ?: "The reviewer did not approve this application. Check the registration " +
                    "number and send it again.",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
            Text(
                text = "I am a",
                style = SettingsType.GroupLabel,
                color = OmniSetGroupLabel,
                maxLines = 1,
                softWrap = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                Profession.entries.forEach { profession ->
                    ProfessionChip(
                        label = profession.label(),
                        selected = state.profession == profession,
                        onClick = { onProfessionChange(profession) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        LicenceField(
            value = state.licenseNumber,
            onValueChange = onLicenseNumberChange,
        )

        Text(
            text = "The number on your council or board registration. A reviewer will check it " +
                "against the register before the badge appears.",
            style = HomeType.CardFootnoteWrapped,
            color = OmniSetRowSubtitle,
        )

        if (state.error != null) {
            Text(
                text = state.error,
                style = SettingsType.RowSubtitle,
                color = OmniAuthError,
                maxLines = 2,
            )
        }

        SubmitButton(state = state, onSubmit = onSubmit)
    }
}

/** Doctor or Nutritionist — the feed's segment pill, inverted onto the settings page's grey. */
@Composable
private fun ProfessionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(ChipHeight)
            .pressEffect()
            .clip(RoundedCornerShape(ChipCorner))
            .background(if (selected) OmniSetApply else OmniSetCardSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SettingsType.RowAction,
            color = if (selected) OmniOnInk else OmniSetRowTitle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The registration number — [SavedEmergenciesScreen]'s labelled field, one line high. */
@Composable
private fun LicenceField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(
            text = "Registration number",
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
                // Registration numbers are printed in capitals on every register this app's users
                // would be on, and `Characters` saves them a shift key per keystroke.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                decorationBox = { inner ->
                    // Both children in one Box so the hint sits behind the caret, not beside it —
                    // and the box has a real width, which is the defect Phase 9 found twice.
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "e.g. BMDC-A-12345",
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
 * The one thing on the page that writes.
 *
 * Grey until the form is complete, so "nothing has been filled in" and "this button is broken" do
 * not look the same — the same three-state bargain [GoalsScreen]'s Save makes.
 */
@Composable
private fun SubmitButton(
    state: VerificationUiState,
    onSubmit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .pressEffect(enabled = state.canSubmit)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(if (state.canSubmit) OmniInk else OmniFeedHint)
            .clickable(enabled = state.canSubmit, onClick = onSubmit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                state.submitting -> "Sending…"
                state.request?.status == VerificationStatus.REJECTED -> "Send it again"
                else -> "Submit Application"
            },
            style = HomeType.HeroButton,
            color = OmniOnInk,
            maxLines = 1,
        )
    }
}

/**
 * The review card — somebody else's turn.
 *
 * It prints what was sent, because the only question an applicant has while waiting is whether the
 * right thing went off, and the relative time so "nothing is happening" can be told apart from
 * "this was two minutes ago".
 */
@Composable
private fun PendingCard(request: VerificationRequest?) {
    StatusCard(
        title = "Under review",
        body = buildString {
            append("A reviewer has your application")
            if (request != null && request.submittedAt > 0) {
                append(", sent ").append(relativeTimeOf(request.submittedAt))
            }
            append(". You will see the badge on your posts as soon as it is approved.")
            if (request != null) {
                append("\n\n")
                append(request.profession.label())
                append(" · ")
                append(request.licenseNumber)
            }
        },
    )
}

/** The settings card's grey tray, carrying one heading and one paragraph. */
@Composable
private fun StatusCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner))
            .background(OmniSetCardSurface)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = SettingsType.RowTitle,
            color = OmniSetRowTitle,
            maxLines = 1,
        )
        Text(
            text = body,
            style = HomeType.CardFootnoteWrapped,
            color = OmniSetRowSubtitle,
        )
    }
}

/** Title case for the chips and the review card — the enum's own name is shouting. */
private fun Profession.label(): String = when (this) {
    Profession.DOCTOR -> "Doctor"
    Profession.NUTRITIONIST -> "Nutritionist"
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame, so every number is borrowed: the gutter, back row, field and button are
// SavedEmergenciesScreen's to the dp, and the chips are its 46dp field height at the pill radius.

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val IntroTop = 18.dp
private val ListTop = 24.dp
private val RowCorner = 8.dp
private val RowPaddingH = 14.dp
private val RowPaddingV = 12.dp
private val FieldGap = 16.dp
private val LabelGap = 6.dp
private val FieldHeight = 46.dp
private val FieldCorner = 8.dp
private val FieldPadding = 14.dp

/** The field's height at the segment pill's radius — two chips share the 355dp row. */
private val ChipHeight = 46.dp
private val ChipCorner = 23.dp
private val ChipGap = 10.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Breathing room so the button clears the gesture area — there is no bottom bar on this page. */
private val ContentBottomGap = 32.dp

@DevicePreviews
@Composable
private fun VerificationScreenPreview() {
    OmniTheme { VerificationScreen() }
}

@DevicePreviews
@Composable
private fun VerificationScreenFilledPreview() {
    OmniTheme {
        VerificationScreen(
            state = VerificationUiState(
                loading = false,
                profession = Profession.DOCTOR,
                licenseNumber = "BMDC-A-84120",
            ),
        )
    }
}

@DevicePreviews
@Composable
private fun VerificationScreenPendingPreview() {
    OmniTheme {
        VerificationScreen(
            state = VerificationUiState(
                loading = false,
                request = VerificationRequest(
                    uid = "me",
                    name = "Dr.Ben",
                    profession = Profession.DOCTOR,
                    licenseNumber = "BMDC-A-84120",
                    status = VerificationStatus.PENDING,
                    submittedAt = System.currentTimeMillis() - 3_600_000,
                ),
            ),
        )
    }
}
