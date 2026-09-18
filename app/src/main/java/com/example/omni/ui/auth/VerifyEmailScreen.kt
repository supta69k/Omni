package com.example.omni.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Email verification screen — shown after signup until the user verifies their email.
 *
 * Supports TWO verification methods:
 * 1. 6-digit OTP code (primary) — auto-submits on 6th digit, immediate navigation
 * 2. Firebase email verification link (fallback) — "I've verified my email" button
 *
 * Figma frame reference: custom design matching Omni's style.
 */
@Composable
fun VerifyEmailScreen(
    email: String,
    onVerifyCode: (code: String) -> Unit = { _ -> }, // OTP verification callback
    onVerifyLinkClick: () -> Unit = {},               // "I've verified my email" (Firebase link)
    onResendCodeClick: () -> Unit = {},               // Resend 6-digit code (backend)
    onChangeEmailClick: () -> Unit = {},              // Change email address
    isVerifyingCode: Boolean = false,                 // OTP verification in progress
    isVerifyingLink: Boolean = false,                 // Link verification in progress
    isResendingCode: Boolean = false,                 // Resending code
    codeError: String? = null,                        // OTP error message
    linkError: String? = null,                        // Link error message
    resendCodeSuccess: String? = null,                // Code resend success
    resendCodeCooldown: Int = 0,                      // Code resend cooldown in seconds
    initialCodeSent: Boolean = false,                 // Whether initial code was already sent
) {
    // OTP input state - 6 separate fields
    val codeDigits = remember { mutableStateOf(List(6) { "" }) }
    val isSubmitting = remember { mutableStateOf(false) }
    val showSuccess = remember { mutableStateOf(false) }
    // "Sent" is true only while the backend's success message is on screen, so the button can
    // claim it after a confirmed send — never on the mere fact that it was tapped.
    val codeSentState = resendCodeSuccess != null

    // Scope for cooldown timers
    val scope = remember { CoroutineScope(Dispatchers.Main + Job()) }

    // Start cooldown timers when values change
    LaunchedEffect(resendCodeCooldown) {
        if (resendCodeCooldown > 0) {
            scope.launch {
                while (resendCodeCooldown > 0) {
                    delay(1000)
                }
            }
        }
    }

    // Clear code on error
    fun clearCode() {
        codeDigits.value = List(6) { "" }
        isSubmitting.value = false
    }

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Email verification icon
            Image(
                painter = painterResource(R.drawable.ic_auth_mail),
                contentDescription = "Email verification",
                modifier = Modifier.size(80.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Verify your email",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF1E1E1E),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "We sent a 6-digit code and a verification link to:",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6C6C6C),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1E1E1E),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ===== SECTION 1: CODE VERIFICATION (PRIMARY) =====
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Enter verification code",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1E1E1E),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Enter the 6-digit code we sent to your email.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6C6C6C),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Single text field with visual OTP boxes as decoration
                Box(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = codeDigits.value.joinToString(""),
                        onValueChange = { newValue ->
                            if (isSubmitting.value || showSuccess.value) return@BasicTextField

                            val digitsOnly = newValue.filter { it.isDigit() }.take(6)
                            val newDigits = mutableListOf<String>()
                            for (i in 0 until 6) {
                                newDigits.add(if (i < digitsOnly.length) digitsOnly[i].toString() else "")
                            }
                            codeDigits.value = newDigits

                            // Auto-submit when 6 digits entered
                            if (newDigits.all { it.isNotBlank() }) {
                                val fullCode = newDigits.joinToString("")
                                isSubmitting.value = true
                                onVerifyCode(fullCode)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.Transparent,
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        decorationBox = { innerTextField ->
                            // Visual OTP boxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                codeDigits.value.forEachIndexed { index, digit ->
                                    val hasError = codeError != null && index == 5 && isSubmitting.value
                                    val isSuccessState = showSuccess.value
                                    val isFocused = codeDigits.value.take(index + 1).any { it.isNotBlank() }

                                    val boxColor by animateColorAsState(
                                        targetValue = when {
                                            isSuccessState -> Color(0xFF4CAF50)
                                            hasError -> Color(0xFFB00020)
                                            isFocused -> Color(0xFF1E1E1E)
                                            else -> Color(0xFFE0E0E0)
                                        },
                                        animationSpec = tween(150),
                                        label = "boxColor"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .background(
                                                color = boxColor.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .border(
                                                width = if (hasError || isSuccessState || isFocused) 2.dp else 1.dp,
                                                color = boxColor,
                                                shape = RoundedCornerShape(12.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = digit,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = if (isSuccessState) Color(0xFF4CAF50) else if (hasError) Color(0xFFB00020) else Color(0xFF1E1E1E),
                                        )
                                    }
                                }
                            }
                            // Invisible text field on top for input capture
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                            ) {
                                innerTextField()
                            }
                        }
                    )
                }

                // Error message for code
                if (codeError != null) {
                    Text(
                        text = codeError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB00020),
                        textAlign = TextAlign.Center,
                    )
                }

                // Success state
                if (showSuccess.value) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_auth_check),
                            contentDescription = "Verified",
                            modifier = Modifier.size(20.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF4CAF50)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Email verified!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF4CAF50),
                        )
                    }
                }

                // Code expiration timer (placeholder - backend should provide actual expiration)
                if (resendCodeCooldown <= 0 && !showSuccess.value && !isVerifyingCode) {
                    Text(
                        text = "Code expires in 10:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== SECTION 2: RESEND CODE =====
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (resendCodeCooldown > 0) {
                    Text(
                        text = "Resend code in ${formatTime(resendCodeCooldown)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9E9E9E),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            onResendCodeClick()
                            // Clear any previous error when resending
                            clearCode()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isResendingCode,
                        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF1E1E1E),
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            text = if (isResendingCode) "Sending..." else
                                if (initialCodeSent || codeSentState) "Resend verification code" else "Send verification code",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF1E1E1E),
                            maxLines = 1,
                        )
                    }
                }

                if (resendCodeSuccess != null) {
                    Text(
                        text = resendCodeSuccess,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== SECTION 3: EMAIL LINK ALTERNATIVE (FALLBACK) =====
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // OR separator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE0E0E0)),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "OR",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE0E0E0)),
                    )
                }

                Text(
                    text = "Prefer using the email link?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1E1E1E),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Open the email we sent and tap \"Verify my email\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6C6C6C),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onVerifyLinkClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isVerifyingLink,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = if (isVerifyingLink) "Verifying..." else "I've verified my email",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                    )
                }

                if (linkError != null) {
                    Text(
                        text = linkError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB00020),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== BOTTOM ACTIONS =====
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Change email
                TextButton(
                    onClick = onChangeEmailClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Change email",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF1E1E1E),
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help text
            Text(
                text = "Didn't receive it? Check your spam/junk folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6C6C6C),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@DevicePreviews
@Composable
private fun VerifyEmailScreenPreview() {
    OmniTheme {
        VerifyEmailScreen(
            email = "user@example.com",
            onVerifyCode = {},
            onVerifyLinkClick = {},
            onResendCodeClick = {},
            onChangeEmailClick = {},
        )
    }
}

@DevicePreviews
@Composable
private fun VerifyEmailScreenCodeErrorPreview() {
    OmniTheme {
        VerifyEmailScreen(
            email = "user@example.com",
            onVerifyCode = {},
            onVerifyLinkClick = {},
            onResendCodeClick = {},
            onChangeEmailClick = {},
            codeError = "Incorrect or expired code.",
        )
    }
}

@DevicePreviews
@Composable
private fun VerifyEmailScreenSuccessPreview() {
    OmniTheme {
        VerifyEmailScreen(
            email = "user@example.com",
            onVerifyCode = {},
            onVerifyLinkClick = {},
            onResendCodeClick = {},
            onChangeEmailClick = {},
        )
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}