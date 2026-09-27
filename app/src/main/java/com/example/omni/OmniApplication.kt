package com.example.omni

import android.app.Application
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.example.omni.di.AppContainer
import com.example.omni.service.OmniTrackingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The one place the real dependency graph is built.
 *
 * Until this class existed, [AppContainer.init] was never called, so `AppContainer.current` always
 * fell back to its preview container — every screen would have talked to `PreviewAuthRepository`,
 * an in-memory fake, and no account would ever have reached Firebase. Registering it in the manifest
 * (`android:name=".OmniApplication"`) is what makes the Firebase layer live.
 *
 * The log line is the cheap standing check for exactly that: filter logcat on `Omni` at launch and
 * it must name `FirebaseAuthRepository`. If it says `PreviewAuthRepository`, the manifest entry is
 * missing or misspelled and nothing below the UI is real.
 */
class OmniApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        // The tracking service owns the step pipeline; started on every app open so a process MIUI
        // killed comes back with it. A no-op while ACTIVITY_RECOGNITION is missing — the service is
        // started again the moment the permission is granted.
        OmniTrackingService.start(this)
        logDebugIdToken()
        Log.i(
            "Omni",
            "AppContainer ready — auth: ${AppContainer.current.authRepository::class.simpleName}",
        )
    }

    /**
     * The one app-wide [ImageLoader], taught to decode animated GIFs (the CPR guide's hero). Every
     * `AsyncImage` in the app uses this singleton, so no existing avatar/photo load changes — the only
     * difference is that a GIF now animates instead of showing its first frame. [AnimatedImageDecoder]
     * is the ImageDecoder-based path (API 28+); [GifDecoder] is the Movie-based fallback for 26–27.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

    /**
     * DEBUG-only helper for the backend's admin/bootstrap curls: prints a freshly forced ID token
     * to logcat (`adb logcat -s OmniDebug`), so the admin claim can be bootstrapped and refreshed
     * without retyping the password. REST-minted tokens carry an issuer the backend's
     * `verifyIdToken` rejects — only SDK tokens verify. Compiled out of release builds.
     */
    private fun logDebugIdToken() {
        if (!BuildConfig.DEBUG) return
        CoroutineScope(Dispatchers.Default).launch {
            repeat(10) {
                runCatching {
                    FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
                }.getOrNull()?.let { token ->
                    Log.i("OmniDebug", "ID_TOKEN=$token")
                    return@launch
                }
                delay(2000)
            }
            Log.w("OmniDebug", "No signed-in user — no ID token to log")
        }
    }
}
