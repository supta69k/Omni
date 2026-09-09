plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.omni"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.omni"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // `collectAsStateWithLifecycle` — the auth gate must stop collecting while backgrounded.
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Onboarding-seen flag now; the step-counter baseline in Phase 4. Chosen over Room because it
    // needs no KSP, which is the riskiest thing to add to an AGP 9 build with built-in Kotlin.
    implementation(libs.androidx.datastore.preferences)
    // Nothing in the app could load a remote image before this: every avatar and feed photo was a
    // bundled drawable. `coil-network-okhttp` is a separate artifact in Coil 3 — without it
    // `AsyncImage` compiles and then fails at runtime on any http(s) model.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Phase 10. Pulls in the FirebaseMessagingService the manifest registers; the token itself is
    // stored on `users/{uid}.fcmTokens` so the Phase 12 functions have somewhere to send to.
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.kotlinx.coroutines.play.services)
    // The SOS screen's fused location (Phase 9). `getCurrentLocation` only — no ongoing updates,
    // so no foreground-service question arises.
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}