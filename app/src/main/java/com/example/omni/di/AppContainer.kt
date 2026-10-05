package com.example.omni.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.omni.data.local.PreferencesStore
import com.example.omni.data.local.StepCounterSource
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.DeviceStepsRepository
import com.example.omni.data.repo.DoctorRepository
import com.example.omni.data.repo.EmergencyContactRepository
import com.example.omni.data.repo.FirebaseAuthRepository
import com.example.omni.data.repo.CloudinaryMediaRepository
import com.example.omni.data.repo.FeedRepository
import com.example.omni.data.repo.FirestoreDoctorRepository
import com.example.omni.data.repo.FirestoreEmergencyContactRepository
import com.example.omni.data.repo.FirestoreFeedRepository
import com.example.omni.data.repo.FirestoreGuideProgressRepository
import com.example.omni.data.repo.FirestoreHospitalRepository
import com.example.omni.data.repo.FirestoreMealRepository
import com.example.omni.data.repo.FirestoreMetricsRepository
import com.example.omni.data.repo.FirestoreNotificationRepository
import com.example.omni.data.repo.FirestoreSleepRepository
import com.example.omni.data.repo.FirestoreSosRepository
import com.example.omni.data.repo.FirestoreUserRepository
import com.example.omni.data.repo.FirestoreVerificationRepository
import com.example.omni.data.repo.GuideProgressRepository
import com.example.omni.data.repo.BundledGuideRepository
import com.example.omni.data.repo.GuideRepository
import com.example.omni.data.repo.HospitalRepository
import com.example.omni.data.repo.LocationRepository
import com.example.omni.data.model.CoachGuidance
import com.example.omni.data.repo.CoachRepository
import com.example.omni.data.repo.CoachResult
import com.example.omni.data.repo.MealAnalysisRepository
import com.example.omni.data.repo.RenderCoachRepository
import com.example.omni.data.repo.MealRepository
import com.example.omni.data.repo.MediaRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.FirestoreMessageRepository
import com.example.omni.data.repo.MetricsRepository
import com.example.omni.data.repo.NotificationRepository
import com.example.omni.data.repo.PreviewAuthRepository
import com.example.omni.data.repo.PreviewMealAnalysisRepository
import com.example.omni.data.repo.PreviewEmergencyContactRepository
import com.example.omni.data.repo.PreviewFeedRepository
import com.example.omni.data.repo.PreviewGuideProgressRepository
import com.example.omni.data.repo.PreviewMealRepository
import com.example.omni.data.repo.PreviewMessageRepository
import com.example.omni.data.repo.PreviewMetricsRepository
import com.example.omni.data.repo.PreviewNotificationRepository
import com.example.omni.data.repo.PreviewRoutingRepository
import com.example.omni.data.repo.PreviewSleepRepository
import com.example.omni.data.repo.PreviewStepsRepository
import com.example.omni.data.repo.PreviewUserRepository
import com.example.omni.data.repo.PreviewVerificationRepository
import com.example.omni.data.repo.OsrmRoutingRepository
import com.example.omni.data.repo.RenderMealAnalysisRepository
import com.example.omni.data.repo.RoutingRepository
import com.example.omni.data.repo.SleepRepository
import com.example.omni.data.repo.StoryRepository
import com.example.omni.data.repo.FirestoreFollowRepository
import com.example.omni.data.repo.FirestoreStoryRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.SosRepository
import com.example.omni.data.repo.StepsRepository
import com.example.omni.data.repo.UserRepository
import com.example.omni.data.repo.VerificationRepository
import com.example.omni.data.revenuecat.PreviewRevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatManager
import com.example.omni.data.revenuecat.RevenueCatRepository
import com.example.omni.ui.SessionViewModel
import com.example.omni.ui.auth.SignInViewModel
import com.example.omni.ui.auth.SignUpViewModel
import com.example.omni.ui.feed.FeedViewModel
import com.example.omni.ui.feed.StoriesViewModel
import com.example.omni.ui.profile.ProfileViewModel
import com.example.omni.ui.firstaid.GuidesViewModel
import com.example.omni.ui.home.HomeViewModel
import com.example.omni.ui.messages.MessagesViewModel
import com.example.omni.ui.notifications.NotificationsViewModel
import com.example.omni.ui.nutrition.NutritionViewModel
import com.example.omni.ui.settings.EmergencyContactsViewModel
import com.example.omni.ui.settings.GoalsViewModel
import com.example.omni.ui.settings.PersonalInformationViewModel
import com.example.omni.ui.settings.SecurityViewModel
import com.example.omni.ui.settings.VerificationViewModel
import com.example.omni.ui.sleep.SleepViewModel
import com.example.omni.ui.sos.SosViewModel
import com.example.omni.ui.omniplus.DoctorConsultationViewModel
import com.example.omni.ui.omniplus.DoctorDirectoryViewModel
import com.example.omni.ui.omniplus.DoctorProfileViewModel
import com.example.omni.ui.omniplus.OmniPlusViewModel
import com.example.omni.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The app's manual dependency container — the whole DI story, on purpose (see
 * IMPLEMENTATION_PLAN.md, "Deliberate decisions"). Screens never touch this: their ViewModels take
 * repositories as constructor parameters, and [factory] is what builds those ViewModels.
 *
 * [init] runs once from [com.example.omni.OmniApplication]. Compose previews and unit tests never
 * run an Application, so [instance] falls back to a preview container whose auth is the in-memory
 * fake — which is also the seam the Phase 10 tests will use.
 */
class AppContainer private constructor(
    val authRepository: AuthRepository,
    val userRepository: UserRepository,
    val metricsRepository: MetricsRepository,
    val mealRepository: MealRepository,
    val mealAnalysisRepository: MealAnalysisRepository,
    val coachRepository: CoachRepository,
    val sleepRepository: SleepRepository,
    val guideProgressRepository: GuideProgressRepository,
    val feedRepository: FeedRepository,
    val emergencyContactRepository: EmergencyContactRepository,
    val notificationRepository: NotificationRepository,
    val messageRepository: MessageRepository,
    val storyRepository: StoryRepository,
    val followRepository: FollowRepository,
    val verificationRepository: VerificationRepository,
    val doctorRepository: DoctorRepository,
    private val hospitalRepositoryProvider: () -> HospitalRepository,
    private val sosRepositoryProvider: () -> SosRepository,
    private val locationRepositoryProvider: () -> LocationRepository,
    private val routingRepositoryProvider: () -> RoutingRepository,
    private val mediaRepositoryProvider: () -> MediaRepository,
    private val preferencesProvider: () -> PreferencesStore,
    private val applicationProvider: () -> Application,
    private val stepsProvider: (AppContainer) -> StepsRepository,
) {

    /**
     * The post composer's image uploader — lazy, and provider-built, so a preview that never
     * composes a post never constructs an OkHttp client for uploads it cannot make anyway.
     */
    val mediaRepository: MediaRepository by lazy { mediaRepositoryProvider() }

    /** Lazy for the same reason: DataStore needs a real `Context`, which a preview has not got. */
    val preferences: PreferencesStore by lazy { preferencesProvider() }

    /**
     * Lazy for both reasons at once — it needs the sensor service *and* [preferences] — which is why its
     * provider is handed the container rather than nothing: the real implementation is built out of two
     * other members of this same object, and doing that eagerly would drag DataStore into every preview.
     */
    val stepsRepository: StepsRepository by lazy { stepsProvider(this) }

    /** Lazy: Play Services should not be dragged into a preview that never asks for a location. */
    val sosRepository: SosRepository by lazy { sosRepositoryProvider() }
    val locationRepository: LocationRepository by lazy { locationRepositoryProvider() }

    /**
     * The SOS map's route provider — the seam that keeps the screen ignorant of who draws the line.
     *
     * Lazy because the real one builds an OkHttp client, and a preview that never opens the map should
     * not pay for a thread pool it will not use. Swapping OSRM for another provider is one line in
     * [init]: nothing above [RoutingRepository] changes.
     */
    val routingRepository: RoutingRepository by lazy { routingRepositoryProvider() }

    /**
     * Lazy for a third reason: the directory has no preview fake, so building it eagerly would call
     * `FirebaseFirestore.getInstance()` while the preview container is being constructed — inside a
     * `@Preview`, where Firebase has never been initialised, which throws before a single pixel is
     * drawn. A preview that never opens the SOS sheet must never pay for it.
     */
    val hospitalRepository: HospitalRepository by lazy { hospitalRepositoryProvider() }

    /**
     * RevenueCat subscription state — lazy because it needs the Application context and must not
     * be constructed in preview mode (where no Application exists). Initialized after
     * [authRepository] so the Firebase UID is available for identity sync.
     */
    val revenueCatRepository: RevenueCatRepository by lazy {
        RevenueCatManager(
            application = applicationProvider(),
            authRepository = authRepository,
            apiKey = BuildConfig.REVENUECAT_API_KEY,
        )
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun init(appContext: Context) {
            check(instance == null) { "AppContainer is already initialised" }
            val firestore = FirebaseFirestore.getInstance()
            val metricsRepo = FirestoreMetricsRepository(firestore)
            instance = AppContainer(
                authRepository = FirebaseAuthRepository(
                    auth = FirebaseAuth.getInstance(),
                    firestore = firestore,
                ),
                userRepository = FirestoreUserRepository(firestore),
                metricsRepository = metricsRepo,
                mealRepository = FirestoreMealRepository(firestore),
                // AI meal analysis speaks HTTPS to the Omni Render backend (the Gemini key lives there,
                // never in the APK). Not lazy: it only builds an OkHttp client, same as the auth repo.
                mealAnalysisRepository = RenderMealAnalysisRepository(FirebaseAuth.getInstance()),
                coachRepository = RenderCoachRepository(FirebaseAuth.getInstance()),
                sleepRepository = FirestoreSleepRepository(firestore, metricsRepo),
                guideProgressRepository = FirestoreGuideProgressRepository(firestore),
                feedRepository = FirestoreFeedRepository(firestore, FirebaseAuth.getInstance()),
                emergencyContactRepository = FirestoreEmergencyContactRepository(firestore),
                notificationRepository = FirestoreNotificationRepository(firestore),
                messageRepository = FirestoreMessageRepository(firestore, FirebaseAuth.getInstance()),
                storyRepository = FirestoreStoryRepository(firestore),
                followRepository = FirestoreFollowRepository(firestore),
                verificationRepository = FirestoreVerificationRepository(firestore),
                doctorRepository = FirestoreDoctorRepository(firestore),
                hospitalRepositoryProvider = { FirestoreHospitalRepository(firestore) },
                sosRepositoryProvider = { FirestoreSosRepository(firestore) },
                locationRepositoryProvider = { LocationRepository(appContext) },
                routingRepositoryProvider = { OsrmRoutingRepository(BuildConfig.OSRM_BASE_URL) },
                // Cloudinary's unsigned upload — no API secret in the APK, and the preset
                // (`omni_mobile`) is the dashboard-side lock on what may be uploaded. The cloud
                // name and preset are not secrets; they identify the endpoint, like a Firestore
                // project id does. Configured in local.properties so a different environment is a
                // properties change, not a code change.
                mediaRepositoryProvider = {
                    CloudinaryMediaRepository(
                        context = appContext,
                        cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
                        unsignedPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
                    )
                },
                preferencesProvider = { PreferencesStore(appContext) },
                applicationProvider = { appContext.applicationContext as Application },
                stepsProvider = { container ->
                    DeviceStepsRepository(
                        source = StepCounterSource(appContext),
                        preferences = container.preferences,
                        metrics = container.metricsRepository,
                    )
                },
            )
        }

        private val preview: AppContainer by lazy {
            AppContainer(
                authRepository = PreviewAuthRepository(),
                userRepository = PreviewUserRepository(),
                metricsRepository = PreviewMetricsRepository(),
                mealRepository = PreviewMealRepository(),
                mealAnalysisRepository = PreviewMealAnalysisRepository(),
                coachRepository = PreviewCoachRepository,
                sleepRepository = PreviewSleepRepository(),
                guideProgressRepository = PreviewGuideProgressRepository(),
                feedRepository = PreviewFeedRepository(),
                emergencyContactRepository = PreviewEmergencyContactRepository(),
                notificationRepository = PreviewNotificationRepository(),
                messageRepository = PreviewMessageRepository(),
                storyRepository = FirestoreStoryRepository(FirebaseFirestore.getInstance()),
                followRepository = FirestoreFollowRepository(FirebaseFirestore.getInstance()),
                verificationRepository = PreviewVerificationRepository(),
                doctorRepository = FirestoreDoctorRepository(FirebaseFirestore.getInstance()),
                // The directory is Firestore-backed with no fake, deliberately: a preview showing the
                // design's own two hospitals comes from SosScreen's defaults, not from here. Behind a
                // provider so no preview ever reaches Firebase to find that out.
                hospitalRepositoryProvider = { FirestoreHospitalRepository(FirebaseFirestore.getInstance()) },
                sosRepositoryProvider = { throw NotImplementedError("sosEvents are not available in previews") },
                locationRepositoryProvider = { throw NotImplementedError("Location is not available in previews") },
                // A straight line between two points — enough for a preview to draw the polyline and
                // render its distance and ETA, and it never touches the network.
                routingRepositoryProvider = { PreviewRoutingRepository() },
                mediaRepositoryProvider = { throw NotImplementedError("Uploads are not available in previews") },
                preferencesProvider = { throw NotImplementedError("DataStore is not available in previews") },
                applicationProvider = { throw NotImplementedError("Application is not available in previews") },
                stepsProvider = { PreviewStepsRepository() },
            )
        }

        /** The real container once the app has initialised it, the preview fake before that. */
        val current: AppContainer
            get() = instance ?: preview

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                SignInViewModel::class.java -> SignInViewModel(current.authRepository) as T
                SignUpViewModel::class.java -> SignUpViewModel(current.authRepository) as T
                SessionViewModel::class.java ->
                    SessionViewModel(
                        current.authRepository,
                        current.userRepository,
                        current.notificationRepository,
                        current.messageRepository,
                        current.mediaRepository,
                    ) as T
                HomeViewModel::class.java ->
                    HomeViewModel(
                        current.authRepository,
                        current.metricsRepository,
                        current.stepsRepository,
                        current.guideProgressRepository,
                        // Same compiled-in bundle the guides screen uses — no preview/real split.
                        BundledGuideRepository,
                    ) as T
                NutritionViewModel::class.java ->
                    NutritionViewModel(
                        current.authRepository,
                        current.metricsRepository,
                        current.mealRepository,
                        current.mealAnalysisRepository,
                        current.revenueCatRepository,
                        current.coachRepository,
                    ) as T
                GuidesViewModel::class.java ->
                    GuidesViewModel(
                        current.authRepository,
                        // The bundle is the same object in the preview container and the real one —
                        // its content is compiled in, so there is no preview/real split to make.
                        BundledGuideRepository,
                        current.guideProgressRepository,
                    ) as T
                FeedViewModel::class.java ->
                    FeedViewModel(
                        current.authRepository,
                        current.feedRepository,
                        current.mediaRepository,
                        current.followRepository,
                        current.userRepository,
                    ) as T
                StoriesViewModel::class.java ->
                    StoriesViewModel(
                        current.authRepository,
                        current.storyRepository,
                        current.mediaRepository,
                        current.followRepository,
                    ) as T
                ProfileViewModel::class.java ->
                    ProfileViewModel(
                        current.authRepository,
                        current.userRepository,
                        current.feedRepository,
                        current.followRepository,
                    ) as T
                SosViewModel::class.java ->
                    SosViewModel(
                        current.authRepository,
                        current.hospitalRepository,
                        current.locationRepository,
                        current.routingRepository,
                        current.sosRepository,
                        current.emergencyContactRepository,
                        current.notificationRepository,
                    ) as T
                EmergencyContactsViewModel::class.java ->
                    EmergencyContactsViewModel(
                        current.authRepository,
                        current.emergencyContactRepository,
                    ) as T
                GoalsViewModel::class.java ->
                    GoalsViewModel(
                        current.authRepository,
                        current.userRepository,
                    ) as T
                PersonalInformationViewModel::class.java ->
                    PersonalInformationViewModel(
                        current.authRepository,
                        current.userRepository,
                    ) as T
                // Auth alone: the address on the account and the password are Firebase's, not
                // `users/{uid}`'s. The profile document's copy of the email is reconciled by
                // `SessionViewModel` once a change has actually landed.
                SecurityViewModel::class.java ->
                    SecurityViewModel(current.authRepository) as T
                VerificationViewModel::class.java ->
                    VerificationViewModel(
                        current.authRepository,
                        current.userRepository,
                        current.verificationRepository,
                    ) as T
                NotificationsViewModel::class.java ->
                    NotificationsViewModel(
                        current.authRepository,
                        current.notificationRepository,
                    ) as T
                MessagesViewModel::class.java ->
                    MessagesViewModel(
                        current.authRepository,
                        current.userRepository,
                        current.messageRepository,
                        current.followRepository,
                        current.doctorRepository,
                        current.revenueCatRepository,
                    ) as T
                SleepViewModel::class.java ->
                    SleepViewModel(
                        current.authRepository,
                        current.sleepRepository,
                    ) as T
                OmniPlusViewModel::class.java ->
                    OmniPlusViewModel(
                        current.revenueCatRepository,
                    ) as T
                DoctorDirectoryViewModel::class.java ->
                    DoctorDirectoryViewModel(
                        current.doctorRepository,
                        current.userRepository,
                    ) as T
                DoctorProfileViewModel::class.java ->
                    throw IllegalStateException("Use DoctorProfileViewModel.factory() instead")
                DoctorConsultationViewModel::class.java ->
                    throw IllegalStateException("Use DoctorConsultationViewModel.factory() instead")
                else -> throw IllegalArgumentException("No factory for ${modelClass.name} — add it here")
            }
        }
    }
}


/**
 * Previews never reach the network for coaching — the sheet renders this fixed guidance, shaped
 * exactly like the backend's answer so the state machine exercises the same path.
 */
private val PreviewCoachRepository = object : CoachRepository {
    override suspend fun requestDailyGuidance(today: String): CoachResult = CoachResult.Success(
        CoachGuidance(
            summary = "Preview guidance — log a few days and your coach will start spotting patterns.",
            focus = "balance",
            tips = listOf("Log a meal to begin.", "Set your Daily Goals in Settings.", "Keep the phone nearby."),
            starter = true,
            used = 0,
            limit = 1,
        ),
    )
}
