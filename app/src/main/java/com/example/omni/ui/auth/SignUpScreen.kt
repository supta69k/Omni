package com.example.omni.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniAuthLink
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniTheme

/**
 * Account creation — Figma frame `iPhone 14 & 15 Pro - 23` (node 1:366).
 *
 * The source is one nested auto-layout column anchored at x=16, y=78 with a width of 383, so the
 * screen is that same column: 16dp of horizontal padding and the design's gaps (36 / 32 / 30 / 21)
 * reproduced as [Arrangement.spacedBy]. The mock iOS status bar in the frame is replaced by
 * [systemBarsPadding], and [TopSpacing] is what the design leaves between the status bar and the
 * logo, so the layout adapts to whatever inset the real device reports.
 *
 * The column is scrollable: at 415x920 it fits with room to spare, but it is 843dp tall and would
 * otherwise clip on a shorter screen or when the keyboard is up.
 *
 * Like [SignInScreen] it keeps its own field state and hands the values out through
 * [onCreateAccount]; validation and Firebase both live behind that callback (BACKEND_PLAN §4 rule 1).
 */
@Composable
fun SignUpScreen(
    onSignIn: () -> Unit = {},
    onCreateAccount: (name: String, email: String, password: String, confirm: String) -> Unit =
        { _, _, _, _ -> },
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(TopSpacing))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ButtonBlockGap),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        AuthHeader(
                            title = "Create An Account",
                            subtitle = "Create an account to set goals,track your progress and stay " +
                                "motivated every step of the way",
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(30.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(21.dp),
                            ) {
                                AuthField(
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = "Name",
                                    icon = R.drawable.ic_auth_user,
                                    iconSize = 22.dp,
                                    iconGap = 6.dp,
                                )
                                AuthField(
                                    value = email,
                                    onValueChange = { email = it },
                                    placeholder = "Example @gmail.com",
                                    icon = R.drawable.ic_auth_mail,
                                    iconSize = 22.dp,
                                    iconGap = 8.dp,
                                    keyboardType = KeyboardType.Email,
                                )
                                AuthField(
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = "Password",
                                    icon = R.drawable.ic_auth_lock,
                                    iconSize = 24.dp,
                                    iconGap = 6.dp,
                                    keyboardType = KeyboardType.Password,
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                AuthField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    placeholder = "Confirm Password",
                                    icon = R.drawable.ic_auth_lock,
                                    iconSize = 24.dp,
                                    iconGap = 6.dp,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                            }

                            TermsRow()
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MessageSlotGap),
                    ) {
                        AuthMessageSlot(message = errorMessage)
                        AuthPrimaryButton(
                            label = "Create an Account",
                            onClick = { onCreateAccount(name, email, password, confirmPassword) },
                            isLoading = isLoading,
                        )
                    }
                }

                AuthFooter(
                    prompt = "Already a omni member?",
                    action = "Sign In",
                    onAction = onSignIn,
                    innerWidth = 227.dp,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Figma `Frame 152`: the checked `ix:namur-ok-filled` mark plus its label, in a 28-high row.
 *
 * The source only defines the checked state — there is no unchecked variant in the file — so this is
 * rendered exactly as designed rather than guessing at a second graphic. The label's 39px line
 * height is dropped because the row it sits in is 28 tall and the text is a single line, where line
 * height only changes the box, not the glyphs.
 */
@Composable
private fun TermsRow() {
    Row(
        modifier = Modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_auth_check),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "Agree the terms of use and privacy policy",
            style = MaterialTheme.typography.labelSmall,
            color = OmniAuthLink,
        )
    }
}

/**
 * Gap between the system status bar and the logo. The frame puts the content at y=78 measured from
 * the very top, and its mock status bar occupies roughly the first 44 of those.
 */
private val TopSpacing = 34.dp

/**
 * The design leaves 36 between the form and the button. As on sign in, the message slot and its gap
 * come out of that 36 instead of extending it, so the button does not move.
 */
private val ButtonBlockGap = 36.dp - MessageSlotHeight - MessageSlotGap

@DevicePreviews
@Composable
private fun SignUpScreenPreview() {
    OmniTheme {
        SignUpScreen()
    }
}

/** The validation failure the design file has no state for. */
@DevicePreviews
@Composable
private fun SignUpScreenErrorPreview() {
    OmniTheme {
        SignUpScreen(errorMessage = "Passwords do not match.")
    }
}
