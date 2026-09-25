package com.example.omni.data.revenuecat

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for Compose previews and unit tests.
 * Simulates an inactive Omni+ subscription by default.
 */
class PreviewRevenueCatRepository(
    private val initialState: RevenueCatState = RevenueCatState.Inactive,
) : RevenueCatRepository {

    private val _state = MutableStateFlow(initialState)
    override val subscriptionState: StateFlow<RevenueCatState> = _state

    override fun hasOmniPlus(): Boolean {
        val state = subscriptionState.value
        return state is RevenueCatState.Active && state.entitlements[EntitlementIds.OMNI_PLUS] == true
    }

    override suspend fun purchase(activity: android.app.Activity, packageId: String) {
        // Simulate successful purchase for preview
        _state.value = RevenueCatState.Active(mapOf(EntitlementIds.OMNI_PLUS to true))
    }

    override suspend fun restorePurchases() {
        // No-op for preview
    }

    override fun offerings(): Map<String, String> = mapOf(
        "omni_plus_monthly" to "Omni+ Monthly",
        "omni_plus_yearly" to "Omni+ Yearly",
    )
}
