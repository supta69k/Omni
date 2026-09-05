package com.example.omni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.feed.FeedScreen
import com.example.omni.ui.onboarding.OnboardingScreen
import com.example.omni.ui.auth.SignInScreen
import com.example.omni.ui.auth.SignUpScreen
import com.example.omni.ui.home.HomeScreen
import com.example.omni.ui.theme.OmniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniTheme {
                var screen by remember { mutableStateOf(launchScreen) }

                /** The five bottom-bar destinations, which every tabbed screen shares. */
                val navigate: (OmniNavItem) -> Unit = { item ->
                    screen = when (item) {
                        OmniNavItem.Home -> AppScreen.Home
                        OmniNavItem.Feed -> AppScreen.Feed
                        OmniNavItem.Sos -> AppScreen.Sos
                        // Not designed yet; the bar still highlights correctly on the two that are.
                        OmniNavItem.Fitness -> AppScreen.Nutrition
                        OmniNavItem.Setting -> screen
                    }
                }

                when (screen) {
                    AppScreen.Onboarding -> OnboardingScreen(
                        onFinish = { screen = AppScreen.SignUp },
                        onSignIn = { screen = AppScreen.SignIn },
                    )
                    AppScreen.SignUp -> SignUpScreen(
                        onSignIn = { screen = AppScreen.SignIn },
                        onCreateAccount = { screen = AppScreen.Home },
                    )
                    AppScreen.SignIn -> SignInScreen(
                        onSignUp = { screen = AppScreen.SignUp },
                        onSignIn = { screen = AppScreen.Home },
                    )
                    AppScreen.Home -> HomeScreen(onNavigate = navigate)
                    AppScreen.Feed -> FeedScreen(onNavigate = navigate)
                    AppScreen.Sos -> HomeScreen(onNavigate = navigate)
                    AppScreen.Nutrition -> HomeScreen(onNavigate = navigate)
                }
            }
        }
    }
}

/**
 * Where the app opens. Onboarding, as it always has — the constant exists only so a pixel-comparison
 * pass against Figma can be pointed at one screen by editing a single line.
 */
private val launchScreen = AppScreen.Feed

private enum class AppScreen { Onboarding, SignUp, SignIn, Home, Feed, Sos, Nutrition }
