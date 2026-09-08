package com.example.omni.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniTheme

/**
 * Returning user — Figma frame `iPhone 14 & 15 Pro - 24` (node 1:453).
 *
 * Same construction as [SignUpScreen]: the design's auto-layout column at x=16, width 383, with its
 * gaps (58 / 50 / 20 / 26) kept verbatim. It sits lower than sign up — y=188 rather than 78 — because
 * it has two fields instead of four, so [TopSpacing] is the only structural difference besides the
 * form itself.
 *
 * The screen still owns its field state; the callbacks hand the values out so a ViewModel can act on
 * them. It stays free of Firebase entirely (BACKEND_PLAN §4 rule 1) and every parameter defaults to
 * the inert behaviour the previews had, so `SignInScreen()` still renders the design as drawn.
 */
@Composable
fun SignInScreen(
    onSignUp: () -> Unit = {},
    onSignIn: (email: String, password: String) -> Unit = { _, _ -> },
    onForgotPassword: (email: String) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    noticeMessage: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                        verticalArrangement = Arrangement.spacedBy(50.dp),
                    ) {
                        AuthHeader(
                            title = "Welcome Back",
                            subtitle = "Welcome back! Sign in to review your goals, update your " +
                                "progress, and keep the momentum going.",
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(26.dp),
                            ) {
                                AuthField(
                                    value = email,
                                    onValueChange = { email = it },
                                    placeholder = "Email Address",
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
                                    imeAction = ImeAction.Done,
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailing = {
                                        // The design ships a single `view` glyph, so the same icon
                                        // covers both states; only the masking changes.
                                        Image(
                                            painter = painterResource(R.drawable.ic_auth_view),
                                            contentDescription = if (passwordVisible) {
                                                "Hide password"
                                            } else {
                                                "Show password"
                                            },
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clickable { passwordVisible = !passwordVisible },
                                        )
                                    },
                                )
                            }

                            Text(
                                text = "Forget Password?",
                                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 29.sp),
                                color = OmniInk,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onForgotPassword(email) },
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MessageSlotGap),
                    ) {
                        // The error wins when both are set: a failure is the more urgent thing to
                        // read, and only one line is reserved.
                        AuthMessageSlot(
                            message = errorMessage ?: noticeMessage,
                            isError = errorMessage != null,
                        )
                        AuthPrimaryButton(
                            label = "Sign In",
                            onClick = { onSignIn(email, password) },
                            isLoading = isLoading,
                        )
                    }
                }

                AuthFooter(
                    prompt = "Don’t have an account?",
                    action = "Sign Up",
                    onAction = onSignUp,
                    innerWidth = 225.dp,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Gap between the system status bar and the logo — the frame's y=188 less the roughly 44 taken by
 * its mock status bar.
 */
private val TopSpacing = 144.dp

/**
 * The design leaves 58 between the form and the button. The message slot and its gap are carved out
 * of that 58 rather than added to it, so the button sits exactly where Figma draws it whether or not
 * there is a message to show.
 */
private val ButtonBlockGap = 58.dp - MessageSlotHeight - MessageSlotGap

@DevicePreviews
@Composable
private fun SignInScreenPreview() {
    OmniTheme {
        SignInScreen()
    }
}

/** The state nobody can see in the design file: a rejected password, mid-request. */
@DevicePreviews
@Composable
private fun SignInScreenErrorPreview() {
    OmniTheme {
        SignInScreen(errorMessage = "Wrong password. Try again or reset it.")
    }
}
