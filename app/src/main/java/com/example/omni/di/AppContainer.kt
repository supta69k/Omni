package com.example.omni.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.omni.data.local.PreferencesStore
import com.example.omni.data.local.StepCounterSource
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.DeviceStepsRepository
import com.example.omni.data.repo.FirebaseAuthRepository
import com.example.omni.data.repo.FirestoreGuideProgressRepository
import com.example.omni.data.repo.FirestoreMetricsRepository
import com.example.omni.data.repo.FirestoreUserRepository
import com.example.omni.data.repo.GuideProgressRepository
import com.example.omni.data.repo.MetricsRepository
import com.example.omni.data.repo.PreviewAuthRepository
import com.example.omni.data.repo.PreviewGuideProgressRepository
import com.example.omni.data.repo.PreviewMetricsRepository
import com.example.omni.data.repo.PreviewStepsRepository
import com.example.omni.data.repo.PreviewUserRepository
import com.example.omni.data.repo.StepsRepository
import com.example.omni.data.repo.UserRepository
import com.example.omni.ui.SessionViewModel
import com.example.omni.ui.auth.SignInViewModel
import com.example.omni.ui.auth.SignUpViewModel
import com.example.omni.ui.home.HomeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

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
    val guideProgressRepository: GuideProgressRepository,
    private val storageProvider: () -> FirebaseStorage,
    private val preferencesProvider: () -> PreferencesStore,
    private val stepsProvider: (AppContainer) -> StepsRepository,
) {

    /** Lazy because previews never touch Storage and must not initialise Firebase for it. */
    val storage: FirebaseStorage by lazy { storageProvider() }

    /** Lazy for the same reason: DataStore needs a real `Context`, which a preview has not got. */
    val preferences: PreferencesStore by lazy { preferencesProvider() }

    /**
     * Lazy for both reasons at once — it needs the sensor service *and* [preferences] — which is why its
     * provider is handed the container rather than nothing: the real implementation is built out of two
     * other members of this same object, and doing that eagerly would drag DataStore into every preview.
     */
    val stepsRepository: StepsRepository by lazy { stepsProvider(this) }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun init(appContext: Context) {
            check(instance == null) { "AppContainer is already initialised" }
            val firestore = FirebaseFirestore.getInstance()
            instance = AppContainer(
                authRepository = FirebaseAuthRepository(
                    auth = FirebaseAuth.getInstance(),
                    firestore = firestore,
                ),
                userRepository = FirestoreUserRepository(firestore),
                metricsRepository = FirestoreMetricsRepository(firestore),
                guideProgressRepository = FirestoreGuideProgressRepository(firestore),
                storageProvider = { FirebaseStorage.getInstance() },
                preferencesProvider = { PreferencesStore(appContext) },
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
                guideProgressRepository = PreviewGuideProgressRepository(),
                storageProvider = { throw NotImplementedError("Storage is not available in previews") },
                preferencesProvider = { throw NotImplementedError("DataStore is not available in previews") },
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
                    SessionViewModel(current.authRepository, current.userRepository) as T
                HomeViewModel::class.java ->
                    HomeViewModel(
                        current.authRepository,
                        current.metricsRepository,
                        current.stepsRepository,
                        current.guideProgressRepository,
                    ) as T
                else -> throw IllegalArgumentException("No factory for ${modelClass.name} — add it here")
            }
        }
    }
}
