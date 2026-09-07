package com.example.omni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.example.omni.ui.nutrition.NutritionScreen
import com.example.omni.ui.settings.SettingScreen
import com.example.omni.ui.sos.SosScreen
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
                        OmniNavItem.Fitness -> AppScreen.Nutrition
                        OmniNavItem.Setting -> AppScreen.Setting
                    }
                }

                // Pages simply crossfade into one another. The old horizontal slide fought the
                // bottom bar's morph-in-place animation — one screen flew sideways while the pill
                // stayed put — so it is gone. A quiet fade lets the eye stay on the navbar, which is
                // where the motion now lives.
                Crossfade(
                    targetState = screen,
                    animationSpec = tween(PageFadeMillis),
                    label = "omniScreen",
                ) { current ->
                    when (current) {
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
                        AppScreen.Sos -> SosScreen(onNavigate = navigate)
                        AppScreen.Nutrition -> NutritionScreen(onNavigate = navigate)
                        AppScreen.Setting -> SettingScreen(onNavigate = navigate)
                    }
                }
            }
        }
    }
}

/**
 * Where the app opens. Onboarding, as it always has — the constant exists only so a pixel-comparison
 * pass against Figma can be pointed at one screen by editing this single line (e.g. to
 * `AppScreen.Nutrition`). Leave it on `Onboarding` when you are done.
 */
private val launchScreen = AppScreen.Onboarding

/** How long one page takes to crossfade into the next. Short enough to feel instant. */
private const val PageFadeMillis = 200

/**
 * Declared in the order the user moves through them: the entry flow first, then the five bottom-bar
 * destinations in the bar's own left-to-right order. Kept aligned with [OmniNavItem]. `Nutrition`
 * sits in the `Fitness` slot, which is the tab that opens it.
 */
private enum class AppScreen { Onboarding, SignUp, SignIn, Home, Feed, Sos, Nutrition, Setting }
