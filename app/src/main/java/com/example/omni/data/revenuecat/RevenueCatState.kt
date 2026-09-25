package com.example.omni.data.revenuecat

/**
 * Subscription entitlement identifiers as defined in the RevenueCat dashboard.
 */
object EntitlementIds {
    const val OMNI_PLUS = "omni_plus"
}

/**
 * Subscription state exposed to the UI via [RevenueCatRepository.subscriptionState].
 */
sealed interface RevenueCatState {
    data object Loading : RevenueCatState

    /**
     * Active entitlements map: entitlementId → whether it's active.
     * E.g. `mapOf("omni_plus" to true)` when the user has Omni+.
     */
    data class Active(
        val entitlements: Map<String, Boolean>,
    ) : RevenueCatState

    data object Inactive : RevenueCatState

    data class Error(
        val message: String,
    ) : RevenueCatState
}
