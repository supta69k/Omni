package com.example.omni.data.revenuecat

/**
 * Entitlement identifiers as defined in the RevenueCat dashboard.
 */
object EntitlementIds {
    /**
     * The dashboard's entitlement was created as `omni_pro` and its identifier is locked there —
     * the code follows the dashboard, not the other way round. Everything premium keys on this one
     * string, so if a new entitlement is ever created, change it here.
     */
    const val OMNI_PLUS = "omni_pro"
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
