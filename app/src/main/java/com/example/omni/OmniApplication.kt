package com.example.omni

import android.app.Application
import android.util.Log
import com.example.omni.di.AppContainer
import com.example.omni.service.OmniTrackingService

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
class OmniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        // The tracking service owns the step pipeline; started on every app open so a process MIUI
        // killed comes back with it. A no-op while ACTIVITY_RECOGNITION is missing — the service is
        // started again the moment the permission is granted.
        OmniTrackingService.start(this)
        Log.i(
            "Omni",
            "AppContainer ready — auth: ${AppContainer.current.authRepository::class.simpleName}",
        )
    }
}
