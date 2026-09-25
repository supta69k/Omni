package com.example.omni.ui.omniplus

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.omni.data.revenuecat.EntitlementIds
import com.example.omni.data.revenuecat.PreviewRevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatState
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniTheme

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

// ---- Previews -----------------------------------------------------------------------------------

/**
 * The gate draws nothing of its own — this pane shows the content pass-through while Omni+ is
 * active. With an inactive subscription the composable navigates to the paywall instead, so there
 * is no inactive pane to preview.
 */
@DevicePreviews
@Composable
private fun PremiumGatePreview() {
    OmniTheme {
        PremiumGate(
            revenueCatRepository = PreviewRevenueCatRepository(
                RevenueCatState.Active(mapOf(EntitlementIds.OMNI_PLUS to true)),
            ),
            onNavigateToPaywall = {},
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Omni+ content renders here")
            }
        }
    }
}
