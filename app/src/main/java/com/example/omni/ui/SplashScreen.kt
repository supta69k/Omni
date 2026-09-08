package com.example.omni.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.omni.ui.auth.AuthLogo
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniTheme

/**
 * What the app shows while it does not yet know who is using it.
 *
 * `FirebaseAuthRepository.authState` starts at `LOADING` and only flips once Firebase's
 * `AuthStateListener` reports the restored session, and the onboarding flag arrives from DataStore a
 * moment later still. Both are fast, but neither is synchronous, and a gate that guessed in the
 * meantime would flash Onboarding for a frame on every cold start of a signed-in app.
 *
 * So this is deliberately the least eventful screen in the app: the same white background and the
 * same mark the auth screens open with, which makes the hand-off to sign-in look like one page rather
 * than two. It is not in the Figma file — there is no design for it — so it borrows [AuthLogo] rather
 * than inventing any new visual language.
 */
@Composable
fun SplashScreen() {
    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
            contentAlignment = Alignment.Center,
        ) {
            AuthLogo()
        }
    }
}

@DevicePreviews
@Composable
private fun SplashScreenPreview() {
    OmniTheme {
        SplashScreen()
    }
}
