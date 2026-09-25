package com.example.omni

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.DefaultCalorieGoal
import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.di.AppContainer
import com.example.omni.data.repo.PostAuthor
import com.example.omni.ui.SessionViewModel
import com.example.omni.ui.SplashScreen
import com.example.omni.ui.auth.SignInScreen
import com.example.omni.ui.auth.SignInViewModel
import com.example.omni.ui.auth.SignUpScreen
import com.example.omni.ui.auth.SignUpViewModel
import com.example.omni.ui.auth.VerifyEmailScreen
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.feed.ComposePostScreen
import com.example.omni.ui.feed.CreateStorySheet
import com.example.omni.ui.feed.FeedScreen
import com.example.omni.ui.feed.FeedViewModel
import com.example.omni.ui.feed.StoriesViewModel
import com.example.omni.ui.feed.StoryViewer
import com.example.omni.ui.firstaid.GuideDetailScreen
import com.example.omni.ui.firstaid.GuidesScreen
import com.example.omni.ui.firstaid.GuidesViewModel
import com.example.omni.ui.home.HomeScreen
import com.example.omni.ui.home.HomeViewModel
import com.example.omni.ui.notifications.NotificationsScreen
import com.example.omni.ui.notifications.NotificationsViewModel
import com.example.omni.ui.nutrition.NutritionScreen
import com.example.omni.ui.nutrition.NutritionViewModel
import com.example.omni.ui.messages.ChatScreen
import com.example.omni.ui.messages.MessagesScreen
import com.example.omni.ui.messages.MessagesViewModel
import com.example.omni.ui.messages.ProfessionalRowState
import com.example.omni.ui.profile.ProfileScreen
import com.example.omni.ui.profile.ProfileViewModel
import com.example.omni.ui.onboarding.OnboardingScreen
import com.example.omni.ui.settings.EmergencyContactsViewModel
import com.example.omni.ui.settings.GoalsScreen
import com.example.omni.ui.settings.GoalsViewModel
import com.example.omni.ui.settings.PersonalInformationScreen
import com.example.omni.ui.settings.PersonalInformationViewModel
import com.example.omni.ui.settings.SavedEmergenciesScreen
import com.example.omni.ui.settings.SecurityScreen
import com.example.omni.ui.settings.SecurityViewModel
import com.example.omni.ui.settings.SettingScreen
import com.example.omni.ui.settings.VerificationScreen
import com.example.omni.ui.settings.VerificationViewModel
import com.example.omni.ui.sleep.SleepScreen
import com.example.omni.ui.sleep.SleepViewModel
import com.example.omni.ui.omniplus.DoctorConsultationScreen
import com.example.omni.ui.omniplus.DoctorConsultationViewModel
import com.example.omni.ui.omniplus.DoctorDirectoryScreen
import com.example.omni.ui.omniplus.DoctorDirectoryViewModel
import com.example.omni.ui.omniplus.DoctorProfileScreen
import com.example.omni.ui.omniplus.DoctorProfileViewModel
import com.example.omni.ui.omniplus.OmniPlusPaywallScreen
import com.example.omni.ui.omniplus.OmniPlusViewModel
import com.example.omni.ui.sos.SosScreen
import com.example.omni.ui.sos.SosViewModel
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.service.OmniMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    val context = LocalContext.current

    val authState by container.authRepository.authState.collectAsStateWithLifecycle()

    // `null` while DataStore has not answered — a third state, not `false`. Treating "unknown" as
    // "not seen" would walk a returning user back through the carousel for one frame.
    val onboardingSeen by container.preferences.onboardingSeen
        .collectAsStateWithLifecycle(initialValue = null)

    // Hoisted above the `Crossfade` so all five tabbed screens read one `users/{uid}` listener rather
    // than opening one each.
    val session: SessionViewModel = viewModel(factory = AppContainer.factory())
    val user by session.user.collectAsStateWithLifecycle()

    // Ownership — "is this my story", "is this my post" — as *state*, so it changes when the session
    // does. Read straight off `authRepository.currentUid` it was a value sampled during composition
    // that nothing invalidated, which is how account A's ownership could survive into account B's
    // first frames. See [SessionViewModel.signedInUid].
    val myUid by session.signedInUid.collectAsStateWithLifecycle()

    // Counted from the inbox rather than read off `users/{uid}.unread` — see [SessionViewModel
    // .unreadNotifications] for why the denormalised field cannot carry this yet. The message badge is
    // the same story, summed across threads instead.
    val unreadNotifications by session.unreadNotifications.collectAsStateWithLifecycle()
    val unreadMessages by session.unreadMessages.collectAsStateWithLifecycle()

    /**
     * The header's data. With no profile yet — signed out, or the document still in flight — the name
     * is **empty**, not the screens' own Figma default: those defaults exist so previews render the
     * design, and showing the mock's "Sayed Mahir" to a real signed-in user would be a lie for as long
     * as it lasted.
     */
    // Two separate remembers: user fields change only on auth events; unreadNotifications changes
    // via Firestore real-time updates which can be very frequent — recreating the whole object on
    // every count change is wasteful. Splitting them means user changes invalidate both, but count
    // changes invalidate only the count field.
    val headerUser = remember(user) {
        user?.let {
            OmniHeaderState(
                userName = it.name,
                photoUrl = it.photoUrl,
                unreadMessages = it.unreadMessages,
                unreadNotifications = 0,
            )
        } ?: OmniHeaderState(userName = "")
    }
    val header = headerUser.copy(unreadNotifications = unreadNotifications)

    // rememberSaveable, not remember: configChanges already keeps this across a rotation, but a
    // low-memory process kill recreates the Activity from a bundle, and a router that came back on
    // Splash would run the gate again and walk a deep-linked page (Security, a Profile) back to Home.
    // AppScreen is an enum, so the default saver serialises it by name with no custom Saver.
    var screen by rememberSaveable { mutableStateOf(DesignPreviewScreen ?: AppScreen.Splash) }

    /**
     * Whose profile [AppScreen.Profile] shows. Beside the screen state because the destination is
     * a parameter of the navigation, not a destination of its own: opening another profile from
     * one profile replaces the page, and back still walks to where the first profile was opened
     * from — the one stack the enum router has.
     */
    var profileUid by rememberSaveable { mutableStateOf<String?>(null) }

    /**
     * Email address for verification screen — passed from SignUp after account creation,
     * or from SignIn for unverified accounts.
     */
    var verificationEmail by rememberSaveable { mutableStateOf("") }

    /**
     * Whether the initial verification code was sent (true for SignUp flow, false for SignIn flow).
     */
    var initialCodeSentForVerification by rememberSaveable { mutableStateOf(false) }

    /**
     * Doctor uid for DoctorProfile and DoctorConsultation screens.
     */
    var doctorUid by rememberSaveable { mutableStateOf<String?>(null) }
    var currentDoctorName by rememberSaveable { mutableStateOf<String?>(null) }

    /**
     * The last bottom-bar tab the user stood on — where the three header pages go back to.
     *
     * This is the whole of the back stack, and one slot is all the app needs: the header sits on every
     * tab, so "back" from Settings, Messages or the bell means "the tab I was reading", which used to be
     * hardcoded to Home. Opening Settings from the feed and landing on the dashboard is the kind of
     * wrongness a real back button is judged by.
     *
     * It only ever holds one of the four tabs, so it can never point at a page that has its own back.
     */
    var lastTab by rememberSaveable { mutableStateOf(AppScreen.Home) }

    /**
     * Android's own convention for a bar: back from a secondary tab returns to the start destination,
     * and back from the start destination leaves the app.
     *
     * Registered here rather than inside the four screens because it is a statement about the *bar*, not
     * about any one page. Inner pages compose their own [BackHandler] after this one and therefore win
     * while they are open; `enabled` keeps this one out of their way regardless, including during the
     * crossfade when both the outgoing and incoming pages are briefly composed.
     */
    BackHandler(enabled = screen in SecondaryTabScreens) { screen = AppScreen.Home }

    // Hoisted for the same reason as the session: the launcher and the resume check belong to the
    // Activity, not to the Home tab, so tabbing away and back must not rebuild them.
    val stepPermission = rememberStepPermission()

    // The SOS screen's location permission, hoisted for the same reason. Asked for when the SOS tab
    // is first opened rather than at launch: the emergency flow must not be gated on a dialog, and
    // someone who only ever tracks water should never be asked where they are.
    val locationPermission = rememberLocationPermission()

    // Hoisted alongside it so tabbing away from SOS and back does not re-ask: `askOnce` is once per
    // process, and the flag has to outlive the branch that calls it.
    val locationAsked = remember { mutableStateOf(false) }

    // Phase 10. Asked for on the notifications page rather than at launch, for the same reason as the
    // location one: a permission dialog on the very first frame asks about a feature the user has not
    // met. Below API 33 this is granted by construction and nothing is ever shown.
    val pushPermission = rememberPushPermission()
    val pushAsked = remember { mutableStateOf(false) }

    /**
     * The channel and this device's FCM token.
     *
     * The channel has to exist before any notification is posted on API 26+, including one that arrives
     * while the app is in the background — so it is created on the Activity's first composition rather
     * than on the notifications screen, which a user may never open.
     *
     * The token is registered per session, not once: it is stored under `users/{uid}`, so signing in as
     * somebody else on the same phone has to move it. Firebase caches the token, so this is a local read
     * on every launch after the first.
     */
    LaunchedEffect(authState) {
        OmniMessagingService.ensureChannel(context)
        if (authState != AuthState.AUTHENTICATED) return@LaunchedEffect
        val uid = container.authRepository.currentUid ?: return@LaunchedEffect
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            container.notificationRepository.registerToken(uid, token)
        } catch (cause: Exception) {
            // Play Services missing, or the project has no FCM sender configured. In-app notifications
            // and the badge both keep working; only tray delivery is lost, so this is a log, not a stop.
            Log.w("Omni", "The FCM token could not be registered", cause)
        }
    }

    // The gate. It runs on a change of *session*, never on a change of screen, which is what lets a
    // successful sign-in navigate straight to Home without this effect second-guessing it: by the
    // time `authState` reaches AUTHENTICATED, `screen` is already Home and no longer an entry screen.
    LaunchedEffect(authState, onboardingSeen) {
        if (DesignPreviewScreen != null) return@LaunchedEffect
        val seen = onboardingSeen ?: return@LaunchedEffect

        // Onboarding outranks the session. It used to sit inside the UNAUTHENTICATED branch, so a
        // phone that had never been introduced to the app still went straight to Home whenever
        // Firebase restored a login from a previous build — which is every reinstall over the top of
        // an existing one, and is why the carousel appeared to have vanished. A first launch is a
        // first launch; who is signed in is the *next* question, answered below once this one is.
        if (!seen) {
            if (screen == AppScreen.Splash) screen = AppScreen.Onboarding
            return@LaunchedEffect
        }

        when (authState) {
            AuthState.LOADING -> Unit

            AuthState.AUTHENTICATED ->
                // Check email verification: unverified users must go to VerifyEmail
                if (screen == AppScreen.Splash || screen in EntryScreens) {
                    if (container.authRepository.isEmailVerified) {
                        screen = AppScreen.Home
                    } else {
                        screen = AppScreen.VerifyEmail
                    }
                }

            // Covers both a cold start and a sign-out from deep inside the app.
            AuthState.UNAUTHENTICATED ->
                if (screen == AppScreen.Splash || screen !in EntryScreens) screen = AppScreen.SignIn
        }
    }

    /** The carousel has been passed; never unset, so signing out lands on sign-in, not onboarding. */
    val markOnboardingSeen: () -> Unit = {
        scope.launch { container.preferences.setOnboardingSeen() }
    }

    /**
     * Every destination the shared chrome can reach — the four bottom-bar tabs plus the three the
     * header opens. One lambda for all of them, which is why they are all [OmniNavItem] members.
     *
     * Tapping a tab also records it as [lastTab], so the header pages know where they were opened from.
     * Tapping a header icon deliberately does not: Settings → bell → back should return to the tab, not
     * bounce between the two header pages.
     */
    val navigate: (OmniNavItem) -> Unit = { item ->
        val target = when (item) {
            OmniNavItem.Home -> AppScreen.Home
            OmniNavItem.Feed -> AppScreen.Feed
            OmniNavItem.Sos -> AppScreen.Sos
            OmniNavItem.Fitness -> AppScreen.Nutrition
            OmniNavItem.Setting -> AppScreen.Setting
            OmniNavItem.Messages -> AppScreen.Messages
            OmniNavItem.Notifications -> AppScreen.Notifications
        }
        if (target in TabScreens) lastTab = target
        screen = target
    }

    // Pages simply crossfade into one another. The old horizontal slide fought the bottom bar's
    // morph-in-place animation — one screen flew sideways while the pill stayed put — so it is gone.
    // A quiet fade lets the eye stay on the navbar, which is where the motion now lives.
    Crossfade(
            targetState = screen,
            animationSpec = tween(PageFadeMillis, easing = FastOutSlowInEasing),
            label = "omniScreen",
        ) { current ->
        when (current) {
            AppScreen.Splash -> SplashScreen()

            // Both exits mark the carousel passed, then route by session rather than assuming there
            // isn't one: with the gate now showing onboarding to a restored login too, "Get Started"
            // can be tapped by somebody who is already signed in, and sending them to sign-up would
            // be a form they have no reason to fill in.
            AppScreen.Onboarding -> OnboardingScreen(
                onFinish = {
                    markOnboardingSeen()
                    screen = if (authState == AuthState.AUTHENTICATED) AppScreen.Home else AppScreen.SignUp
                },
                onSignIn = {
                    markOnboardingSeen()
                    screen = if (authState == AuthState.AUTHENTICATED) AppScreen.Home else AppScreen.SignIn
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
                        viewModel.signUp(name, email, password, confirm) { email, initialCodeSent ->
                            verificationEmail = email
                            initialCodeSentForVerification = initialCodeSent
                            screen = AppScreen.VerifyEmail
                        }
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
                        viewModel.signIn(email, password) {
                            // Check if email is verified after sign in
                            if (container.authRepository.isEmailVerified) {
                                screen = AppScreen.Home
                            } else {
                                verificationEmail = email
                                initialCodeSentForVerification = false
                                screen = AppScreen.VerifyEmail
                            }
                        }
                    },
                    onForgotPassword = { email -> viewModel.resetPassword(email) },
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    noticeMessage = if (state.isResetEmailSent) ResetSentMessage else null,
                )
            }

            AppScreen.VerifyEmail -> {
                val viewModel: SignInViewModel = viewModel(factory = AppContainer.factory())
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                VerifyEmailScreen(
                    email = verificationEmail.ifBlank { user?.email.orEmpty() },
                    // OTP code verification (primary)
                    onVerifyCode = { code ->
                        viewModel.verifyCode(code) { isVerified ->
                            if (isVerified) {
                                screen = AppScreen.Home
                            }
                        }
                    },
                    // Firebase link verification (fallback)
                    onVerifyLinkClick = {
                        viewModel.verifyLink { isVerified ->
                            if (isVerified) {
                                screen = AppScreen.Home
                            }
                        }
                    },
                    // Resend 6-digit code — the one email trigger on this screen; the result
                    // decides whether the button may call itself "Resend" afterwards.
                    onResendCodeClick = {
                        viewModel.resendCode()
                    },
                    initialCodeSent = initialCodeSentForVerification,
                    onChangeEmailClick = {
                        val uid = container.authRepository.currentUid
                        scope.launch {
                            if (uid != null) {
                                try {
                                    val token = FirebaseMessaging.getInstance().token.await()
                                    container.notificationRepository.unregisterToken(uid, token)
                                } catch (cause: Exception) {
                                    Log.w("Omni", "The FCM token could not be released on sign-out", cause)
                                }
                            }
                            container.authRepository.signOut()
                            screen = AppScreen.SignIn
                        }
                    },
                    isVerifyingCode = state.isVerifyingCode,
                    isVerifyingLink = state.isVerifyingLink,
                    isResendingCode = state.isResendingCode,
                    codeError = state.codeError,
                    linkError = state.linkError,
                    resendCodeSuccess = state.resendCodeSuccess,
                    resendCodeCooldown = state.resendCodeCooldown,
                )
            }

            AppScreen.OmniPlus -> {
                val viewModel: OmniPlusViewModel = viewModel(factory = AppContainer.factory())
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val activity = context as? Activity
                OmniPlusPaywallScreen(
                    state = state,
                    onPurchase = { packageId ->
                        if (activity != null) {
                            viewModel.purchase(activity, packageId)
                        }
                    },
                    onRestore = { viewModel.restore() },
                    onBrowseDoctors = { screen = AppScreen.DoctorDirectory },
                    onDismiss = { screen = AppScreen.Setting },
                )
            }

            AppScreen.DoctorDirectory -> {
                val viewModel: DoctorDirectoryViewModel = viewModel(factory = AppContainer.factory())
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DoctorDirectoryScreen(
                    state = state,
                    onDoctorClick = { uid ->
                        val doctor = state.doctors.find { it.uid == uid }
                        doctorUid = uid
                        currentDoctorName = doctor?.name ?: "Doctor"
                        screen = AppScreen.DoctorProfile
                    },
                    onBack = { screen = AppScreen.OmniPlus },
                )
            }

            AppScreen.DoctorProfile -> {
                val viewModel: DoctorProfileViewModel = viewModel(
                    factory = DoctorProfileViewModel.factory(
                        container.doctorRepository,
                        context as androidx.activity.ComponentActivity,
                        doctorUid,
                    ),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DoctorProfileScreen(
                    state = state,
                    onStartConsultation = { screen = AppScreen.DoctorConsultation },
                    onBack = { screen = AppScreen.DoctorDirectory },
                )
            }

            AppScreen.DoctorConsultation -> {
                val viewModel: DoctorConsultationViewModel = viewModel(
                    factory = DoctorConsultationViewModel.factory(
                        container.messageRepository,
                        container.authRepository,
                        context as androidx.activity.ComponentActivity,
                        doctorUid,
                        currentDoctorName,
                    ),
                )
                DoctorConsultationScreen(
                    viewModel = viewModel,
                    onBack = { screen = AppScreen.DoctorDirectory },
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

                // Hoisted so the card and the tap cannot disagree about where the day ends: the
                // button dims at this number and the ViewModel refuses past it, and both read it
                // from here. The goals belong to the account, not the day — see [User.waterGoal].
                val waterGoal = user?.waterGoal ?: DefaultWaterGoal

                HomeScreen(
                    header = header,
                    glasses = state.glasses,
                    waterGoal = waterGoal,
                    steps = state.steps,
                    stepsGoal = user?.stepsGoal ?: DefaultStepsGoal,
                    stepPermissionNeeded = state.stepPermissionNeeded,
                    fiberGrams = state.fiberGrams,
                    fiberGoal = user?.fiberGoal ?: DefaultFiberGoal,
                    sleepHours = state.sleepHours,
                    sleepGoal = user?.sleepGoal ?: DefaultSleepGoal,
                    cprPercent = state.cprPercent,
                    onAddGlass = { home.addGlass(waterGoal) },
                    onEnableStepTracking = stepPermission::openSystemSettings,
                    // The sleep card leaves for the detailed diary rather than opening the old hours-only
                    // sheet: the diary is the single writer of a night's record, and it keeps
                    // `DailyMetrics.sleepHours` — which this card reads — in step on every save.
                    onOpenSleep = { screen = AppScreen.Sleep },
                    onExploreFirstAid = { screen = AppScreen.FirstAid },
                    onNavigate = navigate,
                    onRefresh = { home.refresh() },
                    isRefreshing = state.isRefreshing,
                )
            }

            // The detailed sleep diary — entered from Home's sleep card, back returns there. Its own
            // ViewModel owns one night's record, the month around it, and the saves, edits and deletes
            // the sheet triggers. The target it draws against lives on the account, so it is *told* the
            // profile's `sleepGoal` here rather than opening a second listener for it (BACKEND_PLAN §4
            // rule 3) — the same way Home is told its water goal just above.
            AppScreen.Sleep -> {
                val sleep: SleepViewModel = viewModel(factory = AppContainer.factory())
                val state by sleep.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(user?.sleepGoal) {
                    sleep.onGoalHours(user?.sleepGoal ?: DefaultSleepGoal)
                }

                SleepScreen(
                    state = state,
                    // Back returns to the tab the diary was opened from — Home's sleep card or the
                    // Fitness sleep ring — the same one-slot back the header pages use, rather than a
                    // hardcoded Home that would strand someone who arrived from Fitness.
                    onBack = { screen = lastTab },
                    onSelectDate = sleep::onSelectDate,
                    onBrowseMonth = sleep::onBrowseMonth,
                    onAddSleep = sleep::onAddSleep,
                    onEditSleep = sleep::onEditSleep,
                    onSaveSleep = sleep::onSaveSleep,
                    onConfirmDelete = sleep::onConfirmDelete,
                    onCancelDelete = sleep::onCancelDelete,
                    onDeleteSleep = sleep::onDeleteSleep,
                    onDismissSheet = sleep::onDismissSheet,
                    onDismissError = sleep::onDismissError,
                )
            }

            // The feed: one ViewModel owns the page's reads and writes. The author name is handed
            // down from the hoisted profile (the pattern the goals follow on Home), because the
            // comment sheet stamps it on my comments and the composer on my posts.
            AppScreen.Feed -> {
                val feed: FeedViewModel = viewModel(factory = AppContainer.factory())
                val feedState by feed.uiState.collectAsStateWithLifecycle()
                // Collected separately from `feedState` on purpose: a keystroke must recompose the
                // search pill and its panel, not the twenty post cards under them.
                val searchState by feed.searchState.collectAsStateWithLifecycle()
                val stories: StoriesViewModel = viewModel(factory = AppContainer.factory())
                val storyState by stories.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(user?.name, user?.photoUrl, user?.verified) {
                    feed.onAuthorIdentity(
                        name = user?.name.orEmpty(),
                        photoUrl = user?.photoUrl,
                        verified = user?.verified == true,
                    )
                }

                // The story surface's own state, held beside the feed's because the sheet and the
                // viewer both overlay the feed without being destinations of their own — closing
                // either lands exactly where the user already was.
                var storySheetOpen by remember { mutableStateOf(false) }
                var storyImage by remember { mutableStateOf<Uri?>(null) }
                var storyPublishing by remember { mutableStateOf(false) }
                var storyError by remember { mutableStateOf<String?>(null) }

                val pickStoryImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    storyImage = uri?.let { context.copyToCache(it) }
                    if (storyImage != null) storySheetOpen = true
                }

                // The full-screen viewer draws over everything, above the sheet in the z-order of
                // the composable tree — but only while it is open, so the feed beneath it keeps
                // rendering and returning from a story is instant.
                val viewerOpen = storyState.viewerIndex != null
                if (viewerOpen) {
                    val tile = storyState.tiles.getOrNull(storyState.viewerIndex ?: 0)
                    StoryViewer(
                        authorName = tile?.authorName.orEmpty(),
                        stories = storyState.viewerStories,
                        storyIndex = storyState.viewerStoryIndex,
                        isMine = tile?.authorId != null && tile.authorId == myUid,
                        onAdvance = stories::advance,
                        onRewind = stories::rewind,
                        onClose = stories::closeViewer,
                        onDelete = stories::deleteMyStory,
                        // Same destination the strip's long press carries, from the story itself.
                        onOpenProfile = { authorId ->
                            profileUid = authorId
                            screen = AppScreen.Profile
                        },
                    )
                } else {
                    // The sheet draws its own full-size overlay Box (the same construction the
                    // sleep and meal sheets use), so it wraps the screen from here directly.
                    FeedScreen(
                            header = header,
                            state = feedState,
                            stories = storyState,
                            myUid = myUid,
                            myPhotoUrl = user?.photoUrl,
                            onSegmentChange = feed::onSegmentChange,
                            onLike = feed::toggleLike,
                            onOpenComments = feed::openComments,
                            onCloseComments = feed::closeComments,
                            onSendComment = feed::addComment,
                            onCompose = { screen = AppScreen.ComposePost },
                            onLoadMore = feed::loadMore,
                            onRepost = feed::repost,
                            onOpenStory = stories::openViewer,
                            // The story rail's own entry point. Not `onCompose`: that opens the post
                            // composer, which is exactly why "add to your story" used to publish a
                            // feed post.
                            onAddStory = {
                                storyError = null
                                pickStoryImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            onOpenProfile = { authorId ->
                                profileUid = authorId
                                screen = AppScreen.Profile
                            },
                            // The row's own follow, targeted at the post's author. The ViewModel
                            // refuses my own uid, so this cannot become a follow of myself however
                            // the row was drawn.
                            onFollowToggle = { authorId -> feed.toggleFollow(authorId) },
                            search = searchState,
                            onSearchQueryChange = feed::onSearchQueryChange,
                            onSearchDismiss = feed::clearSearch,
                            onNavigate = navigate,
                            isRefreshing = feedState.isRefreshing,
                            onRefresh = feed::refresh,
                        )

                        // "Add to your story" — the share tile's own picker, the composer's reason
                        // again: the picker's read grant is process-scoped and the Activity survives.
                        CreateStorySheet(
                            visible = storySheetOpen,
                            image = storyImage,
                            isPublishing = storyPublishing,
                            errorText = storyError,
                            onPickImage = {
                                storyError = null
                                pickStoryImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onRemoveImage = { storyImage = null },
                            onDismiss = {
                                storySheetOpen = false
                                storyImage = null
                                storyError = null
                            },
                            onPublish = { caption ->
                                storyPublishing = true
                                storyError = null
                                val image = storyImage ?: run {
                                    storyPublishing = false
                                    return@CreateStorySheet
                                }
                                stories.createStory(
                                    author = PostAuthor(
                                        name = user?.name.orEmpty().ifBlank { "You" },
                                        photoUrl = user?.photoUrl,
                                        verified = user?.verified == true,
                                        profession = user?.profession,
                                    ),
                                    image = image,
                                    caption = caption,
                                ) { failure ->
                                    storyPublishing = false
                                    if (failure == null) {
                                        storySheetOpen = false
                                        storyImage = null
                                    } else {
                                        storyError = failure
                                    }
                                }
                            },
                        )
                }
            }

            // The composer, opened from the feed's add button and its share tile. Back returns to the
            // feed; a successful post also returns there, where the live first page shows it.
            // The composer. The picked image lives here rather than inside the screen so that the
            // picker's round-trip (which the Activity survives) does not strand the composer
            // without its photo. A failed upload keeps the photo and the text where they are —
            // throwing the user's work away because the network blinked is the worse failure.
            AppScreen.ComposePost -> {
                val feed: FeedViewModel = viewModel(factory = AppContainer.factory())
                var posting by remember { mutableStateOf(false) }
                var pickedImage by remember { mutableStateOf<Uri?>(null) }
                var composerError by remember { mutableStateOf<String?>(null) }

                // The picked image lives here rather than inside the screen so that the picker's
                // round-trip does not strand the composer without its photo — and it is **copied to
                // the cache on pick**, because the picker's read grant is process-scoped: holding
                // the original Uri across a process death is exactly how "that image is no longer
                // available" happened. A cache file has no grant to lose.
                val pickImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    pickedImage = uri?.let { context.copyToCache(it) }
                    if (pickedImage == null) {
                        composerError = "That photo could not be opened. Pick it again."
                    }
                }

                ComposePostScreen(
                    image = pickedImage,
                    errorText = composerError,
                    isPosting = posting,
                    onPickImage = {
                        composerError = null
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemoveImage = { pickedImage = null },
                    onBack = {
                        pickedImage = null
                        composerError = null
                        screen = AppScreen.Feed
                    },
                    onPost = { body ->
                        posting = true
                        composerError = null
                        feed.createPost(
                            author = PostAuthor(
                                name = user?.name.orEmpty().ifBlank { "You" },
                                photoUrl = user?.photoUrl,
                                verified = user?.verified == true,
                                profession = user?.profession,
                            ),
                            body = body,
                            image = pickedImage,
                        ) { failure ->
                            posting = false
                            if (failure == null) {
                                pickedImage = null
                                screen = AppScreen.Feed
                            } else {
                                composerError = failure
                            }
                        }
                    },
                )
            }

            // The emergency screen. The location permission follows the step permission's pattern:
            // the launcher belongs to the Activity, the ViewModel is told the answer, and a grant
            // made on the system settings page is picked up on resume.
            AppScreen.Sos -> {
                val sos: SosViewModel = viewModel(factory = AppContainer.factory())
                val sosState by sos.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(locationPermission.granted, locationPermission.permanentlyDenied) {
                    sos.onLocationPermission(
                        granted = locationPermission.granted,
                        permanentlyDenied = locationPermission.permanentlyDenied,
                    )
                    // The offer, once per process. Without this the launcher built above was never
                    // fired and the directory could only ever be the unsorted one.
                    locationPermission.askOnce(locationAsked)
                }

                SosScreen(
                    header = header,
                    hospitals = sosState.hospitals,
                    selectedId = sosState.selected?.id,
                    nearestId = sosState.nearestId,
                    userLocation = sosState.userLocation,
                    routeStatus = sosState.routeStatus,
                    locationStatus = sosState.locationStatus,
                    query = sosState.query,
                    directorySize = sosState.directorySize,
                    recenterTick = sosState.recenterTick,
                    contactCount = sosState.contactCount,
                    loading = sosState.loading,
                    onActivate = sos::onActivated,
                    onQueryChange = sos::onQueryChange,
                    onSelectHospital = sos::onSelectHospital,
                    onRecenter = sos::onRecenter,
                    // The route is drawn *in* the app now, over MapLibre tiles. What used to be a
                    // `geo:` hand-off to Google Maps is gone: it left the app in the middle of an
                    // emergency, and the whole point of Phase 9 is that it no longer has to.
                    onFindRoute = sos::onFindRoute,
                    onCallHospital = { hospital ->
                        // ACTION_DIAL, deliberately not ACTION_CALL: the dialer opens pre-filled
                        // without a permission, and the extra tap is a feature in an emergency —
                        // it is the difference between "call this hospital" and "call someone".
                        //
                        // OpenStreetMap carries a number for maybe half of these, and a dialer that
                        // opens blank is a dead tap. With none listed the button says "Call 999" and
                        // dials that, which is the useful answer to "call this hospital" when the
                        // hospital has no number to call.
                        context.startActivitySafely(
                            Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${hospital.phone?.takeIf(String::isNotBlank) ?: EmergencyNumber}"),
                            ),
                        )
                    },
                    onCallEmergency = {
                        context.startActivitySafely(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EmergencyNumber")),
                        )
                    },
                    onAlertContacts = {
                        val alert = sosState.alert
                        if (alert == null) {
                            // Nothing to alert yet, so the pill is the way to fix that rather than a
                            // dead tap (`UI_ARCHITECTURE.md` §2a rule 4).
                            screen = AppScreen.SavedEmergencies
                        } else {
                            // ACTION_SENDTO, for the same reasons ACTION_DIAL is used above: no
                            // SEND_SMS permission, the user's own app, the message visible before it
                            // goes, and a copy left in their thread history.
                            context.startActivitySafely(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("smsto:${alert.phones.joinToString(";")}"),
                                ).putExtra("sms_body", alert.body),
                            )
                        }
                    },
                    // The three ways out of a location problem, each matched to its own state by the
                    // screen's notice banner. The launchers and the intents stay here, because a
                    // screen never asks the system anything itself (`UI_ARCHITECTURE.md` §6 rule 10).
                    onRequestLocation = locationPermission::request,
                    onOpenAppSettings = locationPermission::openSystemSettings,
                    onOpenLocationSettings = {
                        context.startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onNavigate = navigate,
                )
            }

            AppScreen.Nutrition -> {
                val nutrition: NutritionViewModel = viewModel(factory = AppContainer.factory())
                val state by nutrition.uiState.collectAsStateWithLifecycle()
                val aiMeal by nutrition.aiMeal.collectAsStateWithLifecycle()

                // The day on screen follows the chip, so the goals passed here — which belong to the
                // account — are what the rings and the gauge draw against, whatever day is selected.
                NutritionScreen(
                    header = header,
                    week = state.week,
                    selectedIndex = state.selectedIndex,
                    selectedMonth = state.selectedMonth,
                    monthPickerOpen = state.monthPickerOpen,
                    pickerMonth = state.pickerMonth,
                    monthCalories = state.monthCalories,
                    onOpenMonthPicker = nutrition::onOpenMonthPicker,
                    onDismissMonthPicker = nutrition::onDismissMonthPicker,
                    onBrowseMonth = nutrition::onBrowseMonth,
                    onSelectDate = nutrition::onSelectDay,
                    meals = state.meals,
                    nutrition = state.nutrition,
                    steps = state.steps,
                    stepsGoal = user?.stepsGoal ?: DefaultStepsGoal,
                    glasses = state.glasses,
                    waterGoal = user?.waterGoal ?: DefaultWaterGoal,
                    sleepHours = state.sleepHours,
                    sleepGoal = user?.sleepGoal ?: DefaultSleepGoal,
                    calorieGoal = user?.calorieGoal ?: DefaultCalorieGoal,
                    onSelectDay = { nutrition.onSelectDay(it.date) },
                    // The sleep ring opens the same diary the Home sleep card does — a second entry
                    // point into the existing AppScreen.Sleep, not a new screen.
                    onOpenSleep = { screen = AppScreen.Sleep },
                    onNavigate = navigate,
                    onAddMeal = nutrition::saveMeal,
                    onEditMeal = nutrition::saveMeal,
                    onDeleteMeal = nutrition::deleteMeal,
                    aiMeal = aiMeal,
                    onAnalyzeMeal = nutrition::analyzeMeal,
                    onConfirmAiMeal = nutrition::confirmAiMeal,
                    onDismissAiMeal = nutrition::dismissAiMeal,
                    isRefreshing = state.isRefreshing,
                    onRefresh = nutrition::refresh,
                )
            }

            // The hero card's "Explore!" destination — Phase 7's offline guide list. One ViewModel
            // owns both its pages: `open` inside it is what makes list and detail one destination,
            // so the system back from a guide lands on the list rather than leaving first aid, and
            // back from the list returns to wherever the user came from (Home).
            AppScreen.FirstAid -> {
                val guides: GuidesViewModel = viewModel(factory = AppContainer.factory())
                val state by guides.uiState.collectAsStateWithLifecycle()

                val open = state.open
                if (open != null) {
                    GuideDetailScreen(
                        state = open,
                        onToggleStep = guides::toggleStep,
                        onBack = guides::closeGuide,
                    )
                } else {
                    GuidesScreen(
                        state = state,
                        onQueryChange = guides::onQueryChange,
                        onOpenGuide = guides::openGuide,
                        onBack = { screen = AppScreen.Home },
                        onNavigate = navigate,
                    )
                }
            }

            // Signing out only clears the session; the gate above is what moves the user off this
            // screen, so there is one rule for where a signed-out app goes, not two.
            AppScreen.Setting -> {
                val photoUpload by session.photoUpload.collectAsStateWithLifecycle()

                // The avatar picker lives here for `ComposePost`'s reason and one more: the launcher
                // needs an Activity, which this is the only holder of (§6 rule 10). The pick is copied
                // to the cache before it is handed on, so the upload does not depend on a read grant
                // that dies with the process.
                val pickAvatar = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    val local = uri?.let { context.copyToCache(it) }
                    if (local != null) session.setPhoto(local)
                }

                SettingScreen(
                    // Settings shows the full name, not the header's clamped one, and the only email in
                    // the app. Both are empty rather than mock-filled until the document arrives.
                    userName = user?.name.orEmpty(),
                    userEmail = user?.email.orEmpty(),
                    photoUrl = user?.photoUrl,
                    photoUploading = photoUpload.inFlight,
                    photoError = photoUpload.error,
                    // Both default to on while the profile is in flight, which is what the switches have
                    // always drawn — see `User.pushNotifications`.
                    pushNotifications = user?.pushNotifications ?: true,
                    offlineCache = user?.offlineCache ?: true,
                    // No `onNavigate`: Settings draws no bar or rail, so the back arrow is the only
                    // way out and it returns to whichever tab opened it.
                    onBack = { screen = lastTab },
                    onChangePhoto = {
                        session.clearPhotoError()
                        pickAvatar.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onPersonalInformation = { screen = AppScreen.PersonalInformation },
                    onSecurity = { screen = AppScreen.Security },
                    onSavedEmergencies = { screen = AppScreen.SavedEmergencies },
                    onDailyGoals = { screen = AppScreen.Goals },
                    onApplyForVerification = { screen = AppScreen.Verification },
                    onOmniPlus = { screen = AppScreen.OmniPlus },
                    onPushNotificationsChange = { enabled ->
                        val uid = container.authRepository.currentUid ?: return@SettingScreen
                        scope.launch {
                            try {
                                container.notificationRepository.setPushEnabled(uid, enabled)
                                // The pref is honoured by taking this device out of the send list rather
                                // than by filtering on arrival: a token the server does not have is a push
                                // that is never sent, which no client-side check can match for reliability.
                                val token = FirebaseMessaging.getInstance().token.await()
                                if (enabled) {
                                    container.notificationRepository.registerToken(uid, token)
                                } else {
                                    container.notificationRepository.unregisterToken(uid, token)
                                }
                            } catch (cause: Exception) {
                                Log.w("Omni", "The push preference could not be saved", cause)
                            }
                        }
                    },
                    onOfflineCacheChange = { enabled ->
                        val uid = container.authRepository.currentUid ?: return@SettingScreen
                        scope.launch {
                            try {
                                container.notificationRepository.setOfflineCacheEnabled(uid, enabled)
                            } catch (cause: Exception) {
                                Log.w("Omni", "The offline-cache preference could not be saved", cause)
                            }
                        }
                    },
                    onLogOut = {
                        // The token goes with the session. Left behind, the next push for this account
                        // would land on a phone somebody else is now signed into.
                        val uid = container.authRepository.currentUid
                        scope.launch {
                            if (uid != null) {
                                try {
                                    val token = FirebaseMessaging.getInstance().token.await()
                                    container.notificationRepository.unregisterToken(uid, token)
                                } catch (cause: Exception) {
                                    Log.w("Omni", "The FCM token could not be released on sign-out", cause)
                                }
                            }
                            container.authRepository.signOut()
                        }
                    },
                )
            }

            // The first of the two "Account Details" rows. Nothing is hoisted: the name it edits is the
            // same `users/{uid}.name` the profile stream already carries, so a save moves the header, the
            // settings row and every author row through `user` rather than through anything passed here.
            AppScreen.PersonalInformation -> {
                val personal: PersonalInformationViewModel = viewModel(factory = AppContainer.factory())
                val state by personal.uiState.collectAsStateWithLifecycle()

                PersonalInformationScreen(
                    state = state,
                    onBack = { screen = AppScreen.Setting },
                    onNameChange = personal::onNameChange,
                    onDayChange = personal::onDayChange,
                    onMonthChange = personal::onMonthChange,
                    onYearChange = personal::onYearChange,
                    onGenderChange = personal::onGenderChange,
                    onSave = personal::save,
                    onDiscard = personal::discard,
                )
            }

            // The second of the two. Auth-only, so the profile stream is not involved at all — except
            // that `SessionViewModel` copies a confirmed address change onto `users/{uid}.email`, which
            // is what the settings row above this page reads.
            AppScreen.Security -> {
                val security: SecurityViewModel = viewModel(factory = AppContainer.factory())
                val state by security.uiState.collectAsStateWithLifecycle()

                SecurityScreen(
                    state = state,
                    onBack = { screen = AppScreen.Setting },
                    onNewEmailChange = security::onNewEmailChange,
                    onEmailPasswordChange = security::onEmailPasswordChange,
                    onSubmitEmail = security::submitEmail,
                    onCurrentPasswordChange = security::onCurrentPasswordChange,
                    onNewPasswordChange = security::onNewPasswordChange,
                    onConfirmPasswordChange = security::onConfirmPasswordChange,
                    onSubmitPassword = security::submitPassword,
                )
            }

            // The page behind Settings' "Saved Emergencies" row, and where the SOS sheet's alert pill
            // sends anyone who has not saved anybody yet. Back from either lands on Settings, which is
            // where the row lives — the SOS screen is one tap away on the bar.
            AppScreen.SavedEmergencies -> {
                val contacts: EmergencyContactsViewModel = viewModel(factory = AppContainer.factory())
                val state by contacts.uiState.collectAsStateWithLifecycle()

                SavedEmergenciesScreen(
                    state = state,
                    onBack = { screen = AppScreen.Setting },
                    onOpenForm = contacts::openForm,
                    onCancelForm = contacts::closeForm,
                    onNameChange = contacts::onNameChange,
                    onPhoneChange = contacts::onPhoneChange,
                    onRelationChange = contacts::onRelationChange,
                    onSave = contacts::save,
                    onDelete = contacts::delete,
                    onCall = { phone ->
                        context.startActivitySafely(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")),
                        )
                    },
                )
            }

            // The page behind Settings' "Daily Goals" row. Back lands on Settings, like the other
            // inner page above it. Nothing is hoisted from here: the five targets it edits are the
            // same `goals` map the profile stream already carries, so saving moves the cards on Home
            // and Fitness through `user`, with no second copy of the numbers passing through here.
            AppScreen.Goals -> {
                val goals: GoalsViewModel = viewModel(factory = AppContainer.factory())
                val state by goals.uiState.collectAsStateWithLifecycle()

                GoalsScreen(
                    state = state,
                    onBack = { screen = AppScreen.Setting },
                    onStep = goals::step,
                    onSave = goals::save,
                    onDiscard = goals::discard,
                )
            }

            // The page behind Settings' "Apply for Verification" button — the third and last of the
            // inner pages Settings opens, and back lands on Settings like the other two. Nothing is
            // hoisted: the badge it applies for arrives through `user.verified` on the profile stream,
            // which every screen that draws a badge already reads.
            AppScreen.Verification -> {
                val verification: VerificationViewModel = viewModel(factory = AppContainer.factory())
                val state by verification.uiState.collectAsStateWithLifecycle()

                VerificationScreen(
                    state = state,
                    onBack = { screen = AppScreen.Setting },
                    onProfessionChange = verification::onProfessionChange,
                    onLicenseNumberChange = verification::onLicenseNumberChange,
                    onSubmit = verification::submit,
                )
            }

            // A public profile — reached from a post's or comment's avatar, and from the story
            // strip's authors. The uid travels in [profileUid]; the ViewModel is told on entry so
            // its listeners re-key, the same open-a-page pattern the guides and messages use.
            AppScreen.Profile -> {
                val profile: ProfileViewModel = viewModel(factory = AppContainer.factory())
                val profileState by profile.uiState.collectAsStateWithLifecycle()
                val selfName = user?.name.orEmpty()
                val selfPhoto = user?.photoUrl

                // Built here in the composable scope, because the message button's lambda is not
                // one — the same reason the messages destination builds its own above.
                val messages: MessagesViewModel = viewModel(factory = AppContainer.factory())

                // The feed's own ViewModel, not a second one: these are Activity-scoped, so this is
                // the instance the Feed tab is already using. Opening a post's comments from here is
                // therefore the same sheet, on the same page, rather than a copy of it.
                val feed: FeedViewModel = viewModel(factory = AppContainer.factory())

                LaunchedEffect(profileUid) {
                    profile.setProfileUid(profileUid)
                }

                ProfileScreen(
                    state = profileState,
                    onBack = { screen = AppScreen.Feed },
                    onFollowToggle = profile::toggleFollow,
                    // A post on a profile opens where its actions live — the feed, with the comments
                    // sheet already up. The sheet keys off the open id alone, so the post does not
                    // have to be on the loaded page for this to land.
                    onOpenPost = { post ->
                        feed.openComments(post.id)
                        screen = AppScreen.Feed
                    },
                    onMessage = {
                        // The chat the messages screen already knows how to open, seeded with this
                        // user's identity — `startWith` builds the conversation idempotently.
                        //
                        // Keyed on the *state's* uid rather than on `profileUid`, so a tap during a
                        // page change cannot open the previous person's thread under the new
                        // person's header. `isMe` is the second guard: the button is not drawn on my
                        // own page, but a thread with myself is the one conversation that must never
                        // be written, so the path that writes it checks too.
                        val target = profileState.user ?: return@ProfileScreen
                        if (profileState.isMe || target.uid.isBlank()) return@ProfileScreen
                        messages.startWith(
                            professional = ProfessionalRowState(
                                uid = target.uid,
                                name = target.name,
                                discipline = target.profession?.name.orEmpty(),
                                photoUrl = target.photoUrl,
                            ),
                            selfName = selfName,
                            selfPhotoUrl = selfPhoto,
                        )
                        screen = AppScreen.Messages
                    },
                )
            }

            // Phase 11. One ViewModel owns both pages, exactly as first aid does: `open` inside it is
            // what makes the thread list and a chat one destination, so back from a chat lands on the
            // list rather than leaving the tab, and back from the list returns to the tab the header
            // was tapped on.
            AppScreen.Messages -> {
                val messages: MessagesViewModel = viewModel(factory = AppContainer.factory())
                val state by messages.uiState.collectAsStateWithLifecycle()

                val open = state.open
                if (open != null) {
                    ChatScreen(
                        state = open,
                        onBack = { messages.closeChat() },
                        onDraftChange = messages::onDraftChange,
                        onSend = messages::send,
                    )
                } else {
                    MessagesScreen(
                        state = state,
                        onBack = { screen = lastTab },
                        onNavigate = navigate,
                        onOpen = messages::open,
                        onTogglePicker = messages::togglePicker,
                        // The signed-in name and photo are denormalised onto the new thread so the other
                        // side can draw them without a read. They come from the session the router
                        // already holds rather than from a second listener inside the ViewModel.
                        onStartWith = { professional ->
                            messages.startWith(
                                professional = professional,
                                selfName = user?.name.orEmpty(),
                                selfPhotoUrl = user?.photoUrl,
                            )
                        },
                    )
                }
            }

            // The bell's inbox. Reached from the header, so it keeps the bottom bar and back returns to
            // the tab it was opened from — the header is on all four of them.
            AppScreen.Notifications -> {
                val notifications: NotificationsViewModel = viewModel(factory = AppContainer.factory())
                val state by notifications.uiState.collectAsStateWithLifecycle()

                // Asked for here, on the page the permission is *about*, and only once per process.
                // Refusing costs the tray entry and nothing else: the list below and the header badge
                // are Firestore, not the notification manager.
                LaunchedEffect(Unit) { pushPermission.askOnce(pushAsked) }

                NotificationsScreen(
                    state = state,
                    onBack = { screen = lastTab },
                    onNavigate = navigate,
                    onOpen = { notifications.markRead(it.id) },
                    onMarkAllRead = notifications::markAllRead,
                )
            }
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
/**
 * Copies a picked photo into the app's own cache and returns a `file://` Uri for it.
 *
 * The photo picker's read grant is scoped to this process. Holding the picked Uri past a process
 * death — which is exactly what happens when Android recreates the Activity around the composer —
 * left the uploader reading a grant it no longer held, the "that image is no longer available"
 * failure seen on a real phone. The cache file has no grant to lose; the copy costs one disk pass
 * on a photo about to be re-encoded anyway, and cache files are the system's to reclaim.
 *
 * Returns `null` only when the picker handed back something the resolver cannot even open — the
 * caller treats that as "pick it again" rather than silently keeping a dead Uri.
 */
private fun Context.copyToCache(picked: Uri): Uri? = runCatching {
    val directory = cacheDir.resolve("picked").apply { mkdirs() }
    val file = directory.resolve("pick-${System.currentTimeMillis()}.jpg")
    contentResolver.openInputStream(picked)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    Uri.fromFile(file)
}.getOrNull()

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
 * The SOS screen's location permission — [rememberStepPermission]'s shape, one difference: it is
 * never asked for at launch or on entry, only when the screen's sheet is about to need a sort.
 * The directory still works without it (unsorted, honestly labelled), so the dialog is an offer,
 * not a gate.
 */
@Composable
private fun rememberLocationPermission(): LocationPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    var refused by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        refused = !it
    }

    LifecycleResumeEffect(Unit) {
        granted = hasLocationPermission(context)
        onPauseOrDispose { }
    }

    // "Permanently denied" is not a state Android reports. It is the pair of facts that the user has
    // refused at least once and the OS will no longer show the dialog — `shouldShowRequestPermissionRationale`
    // is false both before the first ask and after a permanent refusal, so the refusal has to be
    // remembered to tell the two apart. It matters because asking again in that state is a dead tap
    // (`UI_ARCHITECTURE.md` §2a rule 4): the SOS screen offers Settings instead.
    val blocked = refused && !granted &&
        context.findActivity()
            ?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false

    return remember(granted, blocked) {
        LocationPermission(
            granted = granted,
            permanentlyDenied = blocked,
            ask = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            openSettings = {
                context.startActivitySafely(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
    }
}

/** The state [rememberLocationPermission] hands to the SOS screen. */
private class LocationPermission(
    val granted: Boolean,
    /**
     * True once the user has refused and Android has stopped offering the dialog. Defaults to false
     * because the other holders built on this class — push notifications — have nowhere to show the
     * distinction and never ask a second time.
     */
    val permanentlyDenied: Boolean = false,
    private val ask: () -> Unit,
    private val openSettings: () -> Unit = {},
) {
    /** Asks once per composition-lifetime holder; a refusal is handled by the honest unsorted list. */
    fun askOnce(asked: MutableState<Boolean>) {
        if (granted || asked.value) return
        asked.value = true
        ask()
    }

    /** The notice banner's "Allow" — shown only while [permanentlyDenied] is false, so it always does something. */
    fun request() = ask()

    /** The notice banner's "Settings", for the refusal only Android's own page can undo. */
    fun openSystemSettings() = openSettings()
}

/**
 * The [Activity] behind a composable's context, or null.
 *
 * `shouldShowRequestPermissionRationale` is an Activity method, and `LocalContext` is only *usually* the
 * Activity — a dialog or an inspection host wraps it. Walking the wrappers is the difference between
 * reading the permission's real state and crashing on a cast.
 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * `POST_NOTIFICATIONS` — [rememberLocationPermission]'s shape again, with the same rule: the feature
 * works without it, so the dialog is an offer.
 *
 * Refusing costs tray entries only. The inbox page and the header's badge read Firestore, so they carry
 * on being right whether or not Android will draw anything in the shade.
 */
@Composable
private fun rememberPushPermission(): LocationPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPushPermission(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    LifecycleResumeEffect(Unit) {
        granted = hasPushPermission(context)
        onPauseOrDispose { }
    }

    return remember(granted) {
        LocationPermission(
            granted = granted,
            ask = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

/**
 * True by construction below API 33, where notifications needed no runtime grant at all.
 *
 * Asking there would return a refusal for a permission the platform does not know, which would then be
 * remembered as "the user said no" — the opposite of the truth.
 */
private fun hasPushPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * `startActivity`, minus the crash.
 *
 * Every hand-off on the SOS screen is to an app that might not be installed — a tablet with no dialer,
 * an emulator with no maps, a phone whose messaging app has been disabled — and an unhandled
 * `ActivityNotFoundException` there takes the app down in the middle of an emergency. Nothing visible
 * happens instead, which is not ideal, but the screen behind is still the one with the hospital list
 * on it.
 */
private fun Context.startActivitySafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (cause: ActivityNotFoundException) {
        Log.w("Omni", "No app on this device can handle ${intent.action} ${intent.data}", cause)
    }
}

/** Bangladesh's national emergency line — the number the app falls back to, and never dials itself. */
private const val EmergencyNumber = "999"

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

/**
 * The four pages the bottom bar can reach — the only ones the header's back button may return to.
 *
 * Kept as a set rather than derived from [OmniNavItem], because the bar's membership and this list are
 * the same fact stated twice on purpose: `Setting`, `Messages` and `Notifications` are [OmniNavItem]s
 * too, and they are exactly what must not end up in [SecondaryTabScreens] or in the back target.
 */
private val TabScreens = setOf(AppScreen.Home, AppScreen.Feed, AppScreen.Sos, AppScreen.Nutrition)

/** The tabs that are not the start destination, which is where the system back sends them. */
private val SecondaryTabScreens = TabScreens - AppScreen.Home

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
    Setting, Messages, Notifications, FirstAid, ComposePost, SavedEmergencies, Goals, Verification,
    /** The two halves of Settings' "Account Details" group: the profile document, then the credentials. */
    PersonalInformation, Security,
    /** The detailed sleep diary — entered from Home's sleep card, back returns there. */
    Sleep,
    /** A public profile — the uid it shows travels beside [MainActivity]'s `profileUid`. */
    Profile,
    /** Email verification screen shown after signup until email is verified. */
    VerifyEmail,
    /** Omni+ premium subscription paywall. */
    OmniPlus,
    /** Doctor directory for Omni+ subscribers. */
    DoctorDirectory,
    /** Doctor profile with consultation button. */
    DoctorProfile,
    /** Text-based doctor consultation chat. */
    DoctorConsultation,
}
