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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniDivider
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniSetEmail
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Security — the page behind Settings' "Security" row, and the other half of [PersonalInformationScreen].
 *
 * Two forms, each complete on its own: an address change and a password change. They are deliberately not
 * one form with one button. The address change only *starts* here — Firebase mails a link and the account
 * does not move until that link is opened — while the password change takes effect the moment it returns,
 * and a single button could not honestly describe both outcomes.
 *
 * Each asks for the current password because Firebase reauthenticates before it will touch a credential.
 * Collecting it in the form turns that requirement into a field rather than into a refusal after the fact.
 *
 * **No Figma frame**, so `UI_ARCHITECTURE.md` §6 rule 11 applies: the chrome, fields and button are
 * [SavedEmergenciesScreen]'s and [GoalsScreen]'s to the dp, and nothing here introduces a colour or a type.
 */
@Composable
fun SecurityScreen(
    state: SecurityUiState = PreviewSecurity,
    onBack: () -> Unit = {},
    onNewEmailChange: (String) -> Unit = {},
    onEmailPasswordChange: (String) -> Unit = {},
    onSubmitEmail: () -> Unit = {},
    onCurrentPasswordChange: (String) -> Unit = {},
    onNewPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onSubmitPassword: () -> Unit = {},
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
                    text = "Security",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(IntroTop))

            Text(
                text = "You're signed in as " + state.email.ifBlank { "this device's account" } + ".",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetEmail,
            )

            Spacer(Modifier.height(ListTop))

            // ---- Change email ------------------------------------------------------------------
            Text(
                text = "Change email",
                style = SettingsType.CardTitle,
                color = OmniCardInk,
                maxLines = 1,
            )

            Spacer(Modifier.height(SectionIntroTop))

            Text(
                text = "We'll send a confirmation link to the new address. Your sign-in email only " +
                    "changes once you open that link.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )

            Spacer(Modifier.height(FieldTop))

            SecurityField(
                label = "New email",
                value = state.newEmail,
                onValueChange = onNewEmailChange,
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
            )

            Spacer(Modifier.height(FieldGap))

            SecurityField(
                label = "Current password",
                value = state.emailPassword,
                onValueChange = onEmailPasswordChange,
                placeholder = "Your password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                masked = true,
            )

            Notice(error = state.emailError, notice = state.emailNotice)

            Spacer(Modifier.height(ButtonTop))

            ActionButton(
                label = "Send confirmation",
                busyLabel = "Sending…",
                enabled = state.canSubmitEmail,
                busy = state.busy == SecurityAction.EMAIL,
                onClick = onSubmitEmail,
            )

            Spacer(Modifier.height(SectionGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DividerThickness)
                    .background(OmniDivider),
            )

            Spacer(Modifier.height(SectionGap))

            // ---- Change password ---------------------------------------------------------------
            Text(
                text = "Change password",
                style = SettingsType.CardTitle,
                color = OmniCardInk,
                maxLines = 1,
            )

            Spacer(Modifier.height(SectionIntroTop))

            Text(
                text = "At least $MinPasswordLength characters. This one takes effect straight away — " +
                    "you'll stay signed in here.",
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )

            Spacer(Modifier.height(FieldTop))

            SecurityField(
                label = "Current password",
                value = state.currentPassword,
                onValueChange = onCurrentPasswordChange,
                placeholder = "Your password",
                keyboardType = KeyboardType.Password,
                masked = true,
            )

            Spacer(Modifier.height(FieldGap))

            SecurityField(
                label = "New password",
                value = state.newPassword,
                onValueChange = onNewPasswordChange,
                placeholder = "New password",
                keyboardType = KeyboardType.Password,
                masked = true,
            )

            Spacer(Modifier.height(FieldGap))

            SecurityField(
                label = "Confirm new password",
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                placeholder = "Repeat it",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                masked = true,
            )

            // Said as soon as the two are both typed and disagree, rather than after a tap on a button
            // that cannot do anything — the mismatch is knowable without asking Firebase.
            if (state.confirmPassword.isNotEmpty() && state.newPassword != state.confirmPassword) {
                Spacer(Modifier.height(NoticeTop))
                Text(
                    text = "Those two don't match.",
                    style = SettingsType.RowSubtitle,
                    color = OmniAlertRed,
                    maxLines = 1,
                )
            }

            Notice(error = state.passwordError, notice = state.passwordNotice)

            Spacer(Modifier.height(ButtonTop))

            ActionButton(
                label = "Change password",
                busyLabel = "Changing…",
                enabled = state.canSubmitPassword,
                busy = state.busy == SecurityAction.PASSWORD,
                onClick = onSubmitPassword,
            )

            Spacer(Modifier.height(ContentBottomGap))
        }
        }
    }
}

/**
 * One form's last word — a failure or a confirmation, never both.
 *
 * Never both because the ViewModel clears each when the other is set: a page carrying a stale "check your
 * inbox" above a fresh "wrong password" would be describing two different attempts as if they were one.
 */
@Composable
private fun Notice(error: String?, notice: String?) {
    val line = error ?: notice ?: return
    Spacer(Modifier.height(NoticeTop))
    Text(
        text = line,
        style = HomeType.CardFootnoteWrapped,
        color = if (error != null) OmniAlertRed else OmniCardInk,
    )
}

/**
 * The section's commitment button — [GoalsScreen]'s, with a label that changes while it waits.
 *
 * Grey and unresponsive until the form is complete, which is the honest state for something that would
 * be rejected: the alternative is a live button whose only job is to produce an error (`UI_ARCHITECTURE.md`
 * §6 rule 10).
 */
@Composable
private fun ActionButton(
    label: String,
    busyLabel: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .pressEffect(enabled = enabled)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(if (enabled) OmniInk else OmniFeedHint)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (busy) busyLabel else label,
            style = HomeType.HeroButton,
            color = OmniOnInk,
            maxLines = 1,
        )
    }
}

/** [SavedEmergenciesScreen]'s labelled field, masked when it is holding a password. */
@Composable
private fun SecurityField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Next,
    masked: Boolean = false,
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
                visualTransformation = if (masked) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                decorationBox = { inner ->
                    // One Box for both, so the hint sits behind the caret rather than beside it.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
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

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame, so every number is borrowed: the gutter, back row, fields and button are
// SavedEmergenciesScreen's and GoalsScreen's to the dp, and the rule is the settings card's divider.

private val PagePadding = 16.dp

private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val IntroTop = 18.dp
private val ListTop = 24.dp

private val SectionIntroTop = 8.dp
private val FieldTop = 16.dp
private val FieldGap = 14.dp
private val LabelGap = 6.dp
private val FieldHeight = 46.dp
private val FieldCorner = 8.dp
private val FieldPadding = 14.dp

private val NoticeTop = 12.dp
private val ButtonTop = 20.dp

/** The two forms are further apart than anything inside one, which is what makes them read as two. */
private val SectionGap = 26.dp
private val DividerThickness = 1.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Breathing room so the last button clears the gesture area — this page has no bottom bar. */
private val ContentBottomGap = 32.dp

/** What the previews render, and the screen's own default: a signed-in account with both forms idle. */
val PreviewSecurity = SecurityUiState(email = "sayed@omni.app")

@DevicePreviews
@Composable
private fun SecurityScreenPreview() {
    OmniTheme { SecurityScreen() }
}

@DevicePreviews
@Composable
private fun SecurityScreenNoticePreview() {
    OmniTheme {
        SecurityScreen(
            state = SecurityUiState(
                email = "sayed@omni.app",
                emailNotice = "Confirmation sent to new@omni.app. Open the link there to finish the " +
                    "change — until you do, keep signing in with sayed@omni.app.",
                passwordError = "Wrong password. Try again or reset it.",
            ),
        )
    }
}
