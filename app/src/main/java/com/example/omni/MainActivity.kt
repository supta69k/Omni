package com.example.omni

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.di.AppContainer
import com.example.omni.ui.ComingSoonScreen
import com.example.omni.ui.SessionViewModel
import com.example.omni.ui.SplashScreen
import com.example.omni.ui.auth.SignInScreen
import com.example.omni.ui.auth.SignInViewModel
import com.example.omni.ui.auth.SignUpScreen
import com.example.omni.ui.auth.SignUpViewModel
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.feed.FeedScreen
import com.example.omni.ui.home.HomeScreen
import com.example.omni.ui.home.HomeViewModel
import com.example.omni.ui.nutrition.NutritionScreen
import com.example.omni.ui.onboarding.OnboardingScreen
import com.example.omni.ui.settings.SettingScreen
import com.example.omni.ui.sos.SosScreen
import com.example.omni.ui.theme.OmniTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniTheme {
                OmniApp()
            }
        }
    }
}

/**
 * The router and the auth gate, which are the same thing here.
 *
 * Shape is `UI_ARCHITECTURE.md` §2a's exactly — one `Crossfade` over a `when(screen)`, no navigation
 * library, no route wrappers. What is new is that [screen] is no longer chosen by a constant: it is
 * reconciled against the session, so a signed-in user reopening the app lands on Home and a signed-out
 * one cannot be left sitting on it.
 *
 * ViewModels are built here (BACKEND_PLAN §4 rule 3) and their state is handed down as plain values
 * and lambdas, so the screens themselves stay free of Firebase and keep rendering from their defaults
 * in `@DevicePreviews`.
 */
@Composable
private fun OmniApp() {
    val container = AppContainer.current
    val scope = rememberCoroutineScope()

    val authState by container.authRepository.authState.collectAsStateWithLifecycle()

    // `null` while DataStore has not answered — a third state, not `false`. Treating "unknown" as
    // "not seen" would walk a returning user back through the carousel for one frame.
    val onboardingSeen by container.preferences.onboardingSeen
        .collectAsStateWithLifecycle(initialValue = null)

    // Hoisted above the `Crossfade` so all five tabbed screens read one `users/{uid}` listener rather
    // than opening one each.
    val session: SessionViewModel = viewModel(factory = AppContainer.factory())
    val user by session.user.collectAsStateWithLifecycle()

    /**
     * The header's data. With no profile yet — signed out, or the document still in flight — the name
     * is **empty**, not the screens' own Figma default: those defaults exist so previews render the
     * design, and showing the mock's "Sayed Mahir" to a real signed-in user would be a lie for as long
     * as it lasted.
     */
    val header = remember(user) {
        user?.let {
            OmniHeaderState(
                userName = it.name,
                photoUrl = it.photoUrl,
                unreadMessages = it.unreadMessages,
                unreadNotifications = it.unreadNotifications,
            )
        } ?: OmniHeaderState(userName = "")
    }

    var screen by remember { mutableStateOf(DesignPreviewScreen ?: AppScreen.Splash) }

    // Hoisted for the same reason as the session: the launcher and the resume check belong to the
    // Activity, not to the Home tab, so tabbing away and back must not rebuild them.
    val stepPermission = rememberStepPermission()

    // The gate. It runs on a change of *session*, never on a change of screen, which is what lets a
    // successful sign-in navigate straight to Home without this effect second-guessing it: by the
    // time `authState` reaches AUTHENTICATED, `screen` is already Home and no longer an entry screen.
    LaunchedEffect(authState, onboardingSeen) {
        if (DesignPreviewScreen != null) return@LaunchedEffect
        val seen = onboardingSeen ?: return@LaunchedEffect
        when (authState) {
            AuthState.LOADING -> Unit

            AuthState.AUTHENTICATED ->
                if (screen == AppScreen.Splash || screen in EntryScreens) screen = AppScreen.Home

            // Covers both a cold start and a sign-out from deep inside the app. Onboarding is only
            // for someone who has genuinely never seen it; everyone else gets sign-in.
            AuthState.UNAUTHENTICATED ->
                if (screen == AppScreen.Splash || screen !in EntryScreens) {
                    screen = if (seen) AppScreen.SignIn else AppScreen.Onboarding
                }
        }
    }

    /** The carousel has been passed; never unset, so signing out lands on sign-in, not onboarding. */
    val markOnboardingSeen: () -> Unit = {
        scope.launch { container.preferences.setOnboardingSeen() }
    }

    /**
     * Every destination the shared chrome can reach — the four bottom-bar tabs plus the three the
     * header opens. One lambda for all of them, which is why they are all [OmniNavItem] members.
     */
    val navigate: (OmniNavItem) -> Unit = { item ->
        screen = when (item) {
            OmniNavItem.Home -> AppScreen.Home
            OmniNavItem.Feed -> AppScreen.Feed
            OmniNavItem.Sos -> AppScreen.Sos
            OmniNavItem.Fitness -> AppScreen.Nutrition
            OmniNavItem.Setting -> AppScreen.Setting
            OmniNavItem.Messages -> AppScreen.Messages
            OmniNavItem.Notifications -> AppScreen.Notifications
        }
    }

    // Pages simply crossfade into one another. The old horizontal slide fought the bottom bar's
    // morph-in-place animation — one screen flew sideways while the pill stayed put — so it is gone.
    // A quiet fade lets the eye stay on the navbar, which is where the motion now lives.
    Crossfade(
        targetState = screen,
        animationSpec = tween(PageFadeMillis),
        label = "omniScreen",
    ) { current ->
        when (current) {
            AppScreen.Splash -> SplashScreen()

            AppScreen.Onboarding -> OnboardingScreen(
                onFinish = {
                    markOnboardingSeen()
                    screen = AppScreen.SignUp
                },
                onSignIn = {
                    markOnboardingSeen()
                    screen = AppScreen.SignIn
                },
            )

            AppScreen.SignUp -> {
                val viewModel: SignUpViewModel = viewModel(factory = AppContainer.factory())
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SignUpScreen(
                    onSignIn = { screen = AppScreen.SignIn },
                    // The trailing lambda is the navigation hook: it runs only after Firebase has
                    // created the account *and* the profile document has been written. Navigating on
                    // tap, as this used to, let a failed sign-up land on Home.
                    onCreateAccount = { name, email, password, confirm ->
                        viewModel.signUp(name, email, password, confirm) { screen = AppScreen.Home }
                    },
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                )
            }

            AppScreen.SignIn -> {
                val viewModel: SignInViewModel = viewModel(factory = AppContainer.factory())
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SignInScreen(
                    onSignUp = { screen = AppScreen.SignUp },
                    onSignIn = { email, password ->
                        viewModel.signIn(email, password) { screen = AppScreen.Home }
                    },
                    onForgotPassword = { email -> viewModel.resetPassword(email) },
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    noticeMessage = if (state.isResetEmailSent) ResetSentMessage else null,
                )
            }

            // The only screen with its own ViewModel so far: it is the only one that writes. The
            // instance lives on the Activity's store, so tabbing away and back reuses it and its
            // listener rather than re-querying.
            AppScreen.Home -> {
                val home: HomeViewModel = viewModel(factory = AppContainer.factory())
                val state by home.uiState.collectAsStateWithLifecycle()

                // A ViewModel cannot see a permission, so it is told — on entry and on every change,
                // which is what makes granting it from the system settings screen start the sensor
                // without a restart. The dialog is asked for here rather than at launch so it follows
                // the card that needs it instead of interrupting onboarding.
                LaunchedEffect(stepPermission.granted) {
                    home.onStepPermission(stepPermission.granted)
                    stepPermission.askOnce()
                }

                HomeScreen(
                    header = header,
                    glasses = state.glasses,
                    // The goals belong to the account, not the day — see [User.waterGoal].
                    waterGoal = user?.waterGoal ?: DefaultWaterGoal,
                    steps = state.steps,
                    stepsGoal = user?.stepsGoal ?: DefaultStepsGoal,
                    stepPermissionNeeded = state.stepPermissionNeeded,
                    fiberGrams = state.fiberGrams,
                    fiberGoal = user?.fiberGoal ?: DefaultFiberGoal,
                    sleepHours = state.sleepHours,
                    sleepGoal = user?.sleepGoal ?: DefaultSleepGoal,
                    cprPercent = state.cprPercent,
                    onAddGlass = home::addGlass,
                    onEnableStepTracking = stepPermission::openSystemSettings,
                    onLogSleep = home::logSleep,
                    onNavigate = navigate,
                )
            }

            AppScreen.Feed -> FeedScreen(header = header, onNavigate = navigate)
            AppScreen.Sos -> SosScreen(header = header, onNavigate = navigate)
            AppScreen.Nutrition -> NutritionScreen(header = header, onNavigate = navigate)

            // Signing out only clears the session; the gate above is what moves the user off this
            // screen, so there is one rule for where a signed-out app goes, not two.
            AppScreen.Setting -> SettingScreen(
                // Settings shows the full name, not the header's clamped one, and the only email in
                // the app. Both are empty rather than mock-filled until the document arrives.
                userName = user?.name.orEmpty(),
                userEmail = user?.email.orEmpty(),
                photoUrl = user?.photoUrl,
                onNavigate = navigate,
                onLogOut = { container.authRepository.signOut() },
            )

            // Neither exists in Figma (BACKEND_PLAN §3.6), but the header's two icons have to land
            // somewhere, and a stub that says so beats a tap that does nothing.
            AppScreen.Messages -> ComingSoonScreen(
                title = "Messages",
                detail = "Direct messages with verified professionals are coming soon.",
                tab = OmniNavItem.Messages,
                onNavigate = navigate,
            )

            AppScreen.Notifications -> ComingSoonScreen(
                title = "Notifications",
                detail = "Replies, reminders and SOS updates will collect here.",
                tab = OmniNavItem.Notifications,
                onNavigate = navigate,
            )
        }
    }
}

/**
 * The `ACTIVITY_RECOGNITION` permission, and the two ways the app can ask for it.
 *
 * Held here rather than in `HomeViewModel` because a permission is an Activity-scoped, UI-driven thing:
 * asking for it needs a launcher, and noticing a grant made outside the app needs the lifecycle. The
 * ViewModel is simply told the answer (BACKEND_PLAN §4 rule 1 — screens and ViewModels take data in,
 * not framework handles).
 */
@Composable
private fun rememberStepPermission(): StepPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasStepPermission(context)) }
    val asked = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    // A grant made on the system settings screen never comes back through the launcher, so the answer is
    // re-read every time the app returns to the foreground. This is also what makes revoking it take
    // effect — Android restarts the process on a revoke, but a re-read costs nothing and is honest.
    LifecycleResumeEffect(Unit) {
        granted = hasStepPermission(context)
        onPauseOrDispose { }
    }

    return remember(granted) {
        StepPermission(
            granted = granted,
            asked = asked,
            ask = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
            openSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
    }
}

/** The state [rememberStepPermission] hands to Home. See it for why this lives at the Activity level. */
private class StepPermission(
    val granted: Boolean,
    private val asked: MutableState<Boolean>,
    private val ask: () -> Unit,
    private val openSettings: () -> Unit,
) {
    /**
     * The system dialog, at most once per process.
     *
     * Once is the limit because the OS itself will not show it again after a refusal, so a second call
     * would return instantly with no visible effect — and re-prompting someone who has already said no is
     * what makes an app feel like it is nagging.
     */
    fun askOnce() {
        if (granted || asked.value) return
        asked.value = true
        ask()
    }

    /**
     * Where the step card's affordance goes: the app's own settings page, not the dialog.
     *
     * Deliberately not a second `ask()`. After a refusal the dialog is suppressed and the launcher
     * returns immediately, so the tap would do nothing visible — a dead tap, which is the one thing
     * `UI_ARCHITECTURE.md` §2a rule 4 rules out. Settings always works.
     */
    fun openSystemSettings() = openSettings()
}

/**
 * Whether the app may read the step counter.
 *
 * True by construction below API 29: the permission did not exist as a runtime one there, and the manifest
 * declaration is the whole of it. Asking for it on those versions would return a refusal.
 */
private fun hasStepPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Set this to a screen to bypass the auth gate and open straight onto it for a pixel-comparison pass
 * against Figma (e.g. `AppScreen.Nutrition`). Leave it `null` — that is the real launch flow.
 */
private val DesignPreviewScreen: AppScreen? = null

/**
 * The screens that belong to someone who is not signed in. A signed-in user is moved off them and a
 * signed-out user is moved onto them; anything else is left where it is, so navigating between tabs
 * never fights the gate.
 *
 * `Splash` is deliberately not a member: it is the "not decided yet" screen, and both branches of the
 * gate have to move off it.
 */
private val EntryScreens = setOf(AppScreen.Onboarding, AppScreen.SignUp, AppScreen.SignIn)

/** Confirmation for the one action whose result is invisible — the reset email has been requested. */
private const val ResetSentMessage = "Reset link sent. Check your email."

/** How long one page takes to crossfade into the next. Short enough to feel instant. */
private const val PageFadeMillis = 200

/**
 * Declared in the order the user moves through them: the launch gate, the entry flow, the four
 * bottom-bar destinations in the bar's own left-to-right order, then the three the header opens. Kept
 * aligned with [OmniNavItem]. `Nutrition` sits in the `Fitness` slot, which is the tab that opens it.
 */
private enum class AppScreen {
    Splash, Onboarding, SignUp, SignIn,
    Home, Feed, Sos, Nutrition,
    Setting, Messages, Notifications,
}
