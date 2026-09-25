package com.example.omni.ui.omniplus

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.example.omni.data.revenuecat.RevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatState

@Composable
fun PremiumGate(
    revenueCatRepository: RevenueCatRepository,
    onNavigateToPaywall: () -> Unit,
    content: @Composable () -> Unit,
) {
    val subscriptionState by revenueCatRepository.subscriptionState.collectAsState()

    when (subscriptionState) {
        is RevenueCatState.Active -> content()
        else -> {
            // Trigger navigation to paywall once
            onNavigateToPaywall()
            // Show placeholder while navigating
            // The paywall screen itself will render when screen changes
        }
    }
}
