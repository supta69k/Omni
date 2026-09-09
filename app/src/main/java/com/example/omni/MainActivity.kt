package com.example.omni

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import com.example.omni.data.model.DefaultCalorieGoal
import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.di.AppContainer
import com.example.omni.data.repo.PostAuthor
import com.example.omni.ui.ComingSoonScreen
import com.example.omni.ui.SessionViewModel
import com.example.omni.ui.SplashScreen
import com.example.omni.ui.auth.SignInScreen
import com.example.omni.ui.auth.SignInViewModel
import com.example.omni.ui.auth.SignUpScreen
import com.example.omni.ui.auth.SignUpViewModel
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.feed.ComposePostScreen
import com.example.omni.ui.feed.FeedScreen
import com.example.omni.ui.feed.FeedViewModel
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
import com.example.omni.ui.onboarding.OnboardingScreen
import com.example.omni.ui.settings.EmergencyContactsViewModel
import com.example.omni.ui.settings.SavedEmergenciesScreen
import com.example.omni.ui.settings.SettingScreen
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
    val header = remember(user, unreadNotifications) {
        user?.let {
            OmniHeaderState(
                userName = it.name,
                photoUrl = it.photoUrl,
                unreadMessages = it.unreadMessages,
                unreadNotifications = unreadNotifications,
            )
        } ?: OmniHeaderState(userName = "")
    }

    var screen by remember { mutableStateOf(DesignPreviewScreen ?: AppScreen.Splash) }

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
                    onExploreFirstAid = { screen = AppScreen.FirstAid },
                    onNavigate = navigate,
                )
            }

            // The feed: one ViewModel owns the page's reads and writes. The author name is handed
            // down from the hoisted profile (the pattern the goals follow on Home), because the
            // comment sheet stamps it on my comments and the composer on my posts.
            AppScreen.Feed -> {
                val feed: FeedViewModel = viewModel(factory = AppContainer.factory())
                val feedState by feed.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(user?.name) {
                    feed.onAuthorName(user?.name.orEmpty())
                }

                FeedScreen(
                    header = header,
                    state = feedState,
                    onSegmentChange = feed::onSegmentChange,
                    onLike = feed::toggleLike,
                    onOpenComments = feed::openComments,
                    onCloseComments = feed::closeComments,
                    onSendComment = feed::addComment,
                    onCompose = { screen = AppScreen.ComposePost },
                    onLoadMore = feed::loadMore,
                    onNavigate = navigate,
                )
            }

            // The composer, opened from the feed's add button and its share tile. Back returns to the
            // feed; a successful post also returns there, where the live first page shows it.
            AppScreen.ComposePost -> {
                val feed: FeedViewModel = viewModel(factory = AppContainer.factory())
                var posting by remember { mutableStateOf(false) }

                ComposePostScreen(
                    isPosting = posting,
                    onBack = { screen = AppScreen.Feed },
                    onPost = { body ->
                        posting = true
                        feed.createPost(
                            author = PostAuthor(
                                name = user?.name.orEmpty().ifBlank { "You" },
                                photoUrl = user?.photoUrl,
                                verified = user?.verified == true,
                                profession = user?.profession,
                            ),
                            body = body,
                        ) {
                            posting = false
                            screen = AppScreen.Feed
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

                LaunchedEffect(locationPermission.granted) {
                    sos.onLocationPermission(locationPermission.granted)
                    // The offer, once per process. Without this the launcher built above was never
                    // fired and the directory could only ever be the unsorted one.
                    locationPermission.askOnce(locationAsked)
                }

                SosScreen(
                    header = header,
                    hospitals = sosState.hospitals,
                    hasLocation = sosState.hasLocation,
                    contactCount = sosState.contactCount,
                    onActivate = sos::onActivated,
                    onFindRoute = { hospital ->
                        // Hand off to Google Maps — free, familiar, and better than anything the app
                        // would build itself (BACKEND_PLAN §11 Phase 9, "Routing").
                        context.startActivitySafely(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:${hospital.lat},${hospital.lng}?q=${hospital.lat},${hospital.lng}(${hospital.name})"),
                            ),
                        )
                    },
                    onCallHospital = { hospital ->
                        // ACTION_DIAL, deliberately not ACTION_CALL: the dialer opens pre-filled
                        // without a permission, and the extra tap is a feature in an emergency —
                        // it is the difference between "call this hospital" and "call someone".
                        //
                        // OpenStreetMap carries a number for maybe half of these, and a dialer that
                        // opens blank is a dead tap. With none listed the button falls back to the
                        // national line, which is the useful answer to "call this hospital" when the
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
                    onNavigate = navigate,
                )
            }

            AppScreen.Nutrition -> {
                val nutrition: NutritionViewModel = viewModel(factory = AppContainer.factory())
                val state by nutrition.uiState.collectAsStateWithLifecycle()

                // The day on screen follows the chip, so the goals passed here — which belong to the
                // account — are what the rings and the gauge draw against, whatever day is selected.
                NutritionScreen(
                    header = header,
                    week = state.week,
                    selectedIndex = state.selectedIndex,
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
                    onNavigate = navigate,
                    onAddMeal = nutrition::saveMeal,
                    onEditMeal = nutrition::saveMeal,
                    onDeleteMeal = nutrition::deleteMeal,
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
            AppScreen.Setting -> SettingScreen(
                // Settings shows the full name, not the header's clamped one, and the only email in
                // the app. Both are empty rather than mock-filled until the document arrives.
                userName = user?.name.orEmpty(),
                userEmail = user?.email.orEmpty(),
                photoUrl = user?.photoUrl,
                // Both default to on while the profile is in flight, which is what the switches have
                // always drawn — see `User.pushNotifications`.
                pushNotifications = user?.pushNotifications ?: true,
                offlineCache = user?.offlineCache ?: true,
                onNavigate = navigate,
                onSavedEmergencies = { screen = AppScreen.SavedEmergencies },
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

            // Phase 11. One ViewModel owns both pages, exactly as first aid does: `open` inside it is
            // what makes the thread list and a chat one destination, so back from a chat lands on the
            // list rather than leaving the tab, and back from the list goes Home like every other tab.
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
                        onBack = { screen = AppScreen.Home },
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

            // The bell's inbox. Reached from the header, so it keeps the bottom bar and back lands on
            // Home — the header is on all four tabs, and Home is the one they all return through.
            AppScreen.Notifications -> {
                val notifications: NotificationsViewModel = viewModel(factory = AppContainer.factory())
                val state by notifications.uiState.collectAsStateWithLifecycle()

                // Asked for here, on the page the permission is *about*, and only once per process.
                // Refusing costs the tray entry and nothing else: the list below and the header badge
                // are Firestore, not the notification manager.
                LaunchedEffect(Unit) { pushPermission.askOnce(pushAsked) }

                NotificationsScreen(
                    state = state,
                    onBack = { screen = AppScreen.Home },
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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    LifecycleResumeEffect(Unit) {
        granted = hasLocationPermission(context)
        onPauseOrDispose { }
    }

    return remember(granted) {
        LocationPermission(
            granted = granted,
            ask = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        )
    }
}

/** The state [rememberLocationPermission] hands to the SOS screen. */
private class LocationPermission(
    val granted: Boolean,
    private val ask: () -> Unit,
) {
    /** Asks once per composition-lifetime holder; a refusal is handled by the honest unsorted list. */
    fun askOnce(asked: MutableState<Boolean>) {
        if (granted || asked.value) return
        asked.value = true
        ask()
    }
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
    Setting, Messages, Notifications, FirstAid, ComposePost, SavedEmergencies,
}
