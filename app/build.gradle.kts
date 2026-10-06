import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

/** Escapes a raw string for embedding as a `buildConfigField` string literal. */
fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.omni"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.omni"
        minSdk = 26
        // A stable, widely-shipping target so the release APK installs on every current phone when
        // sideloaded. compileSdk stays on the latest (37) for the newest APIs; targetSdk is the lever
        // Android uses for install compatibility, and 35 (Android 15) is supported everywhere Android 8+.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The SOS map's secrets, read from local.properties (gitignored) the same way the weather
        // project reads its API key. Every field has a safe default, so the app compiles and runs with
        // none of them set — the map simply falls back to its "unavailable" state. See OmniMapConfig.
        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }
        buildConfigField(
            "String",
            "MAPTILER_KEY",
            (localProperties.getProperty("MAPTILER_KEY") ?: "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "MAPTILER_STYLE_URL",
            (localProperties.getProperty("MAPTILER_STYLE_URL") ?: "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "OSRM_BASE_URL",
            (localProperties.getProperty("OSRM_BASE_URL") ?: "https://router.project-osrm.org")
                .asBuildConfigString(),
        )
        // Cloudinary's unsigned upload endpoint — cloud name + preset are identifiers, not secrets
        // (the API secret never ships; unsigned presets are the client-safe flow). In
        // local.properties so a second environment is a properties change, not a code change.
        buildConfigField(
            "String",
            "CLOUDINARY_CLOUD_NAME",
            (localProperties.getProperty("CLOUDINARY_CLOUD_NAME") ?: "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLOUDINARY_UPLOAD_PRESET",
            (localProperties.getProperty("CLOUDINARY_UPLOAD_PRESET") ?: "").asBuildConfigString(),
        )
        // RevenueCat public SDK key — not a secret (it's in the APK and safe to ship), but kept
        // in local.properties so a second environment is a properties change, not a code change.
        buildConfigField(
            "String",
            "REVENUECAT_API_KEY",
            (localProperties.getProperty("REVENUECAT_API_KEY") ?: "").asBuildConfigString(),
        )
    }

    buildTypes {
        release {
            // R8 shrink + optimize: the unshrunk release APK was ~82 MB (every library the app
            // touches, whole). The libraries in use (Firebase, MapLibre, RevenueCat, Coil) all ship
            // their own consumer keep rules, and the app does no reflection over its own classes —
            // Firestore documents are mapped by hand, not by annotation — so the default rules hold.
            // Verified on device after enabling: launch, sign-in gate, SOS map, directory sync.
            optimization {
                enable = true
            }
            // Sign the release APK with the upload keystore (read from the gitignored
            // keystore.properties) so it installs cleanly when shared, rather than being an unsigned
            // or debug-only build. Guarded so the project still configures if the file is absent.
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val keystoreProps = Properties().apply {
                    keystorePropsFile.inputStream().use { load(it) }
                }
                signingConfig = signingConfigs.create("release") {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // The MapTiler key and the OSRM base URL reach the code this way rather than through a
        // committed constant. Nothing else in the app used BuildConfig before this.
        buildConfig = true
    }
    testOptions {
        unitTests {
            // The JVM test runtime ships android.jar with every method stubbed to *throw*, so a
            // `catch { Log.w(...); showTheUserWhyItFailed() }` blows up on the log line and the
            // recovery never runs — the ViewModel looks like it swallowed the failure when in fact
            // the test environment did. Defaults make android.util.Log a no-op, which is what a
            // unit test wants from it: the failure paths are testable and nothing in the app changes.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
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
    // Animated GIF decoding for the CPR guide's hero animation. Without it Coil renders only the
    // first frame; with it (registered on the singleton ImageLoader in OmniApplication) the GIF plays.
    implementation(libs.coil.gif)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Phase 10. Pulls in the FirebaseMessagingService the manifest registers; the token itself is
    // stored on `users/{uid}.fcmTokens` so the Phase 12 functions have somewhere to send to.
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    // The SOS screen's fused location (Phase 9). `getCurrentLocation` only — no ongoing updates,
    // so no foreground-service question arises.
    implementation(libs.play.services.location)
    // The SOS map (Phase 9 rework). MapLibre renders MapTiler's vector tiles into an in-app MapView;
    // no Google Maps SDK, no billing. Publishes to Maven Central, which is already declared.
    implementation(libs.maplibre.android)
    // OSRM routing calls. OkHttp is already on the classpath transitively through Coil (4.12.0);
    // declaring it directly so the routing repository does not depend on a transitive version.
    implementation(libs.okhttp)
    implementation(libs.revenuecat)
    implementation(libs.exyte.navbar)
    testImplementation(libs.junit)
    // `runTest` and the virtual clock, for the ViewModel tests: the profile's state machine is a
    // combine over five flows, and the bug it is guarding against is a timing one — what the page
    // shows while some of those reads have answered and others have not. Test-only; nothing here
    // ships in the APK.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}