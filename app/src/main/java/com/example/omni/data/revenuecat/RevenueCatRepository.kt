package com.example.omni.data.revenuecat

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the RevenueCat SDK, keeping the UI and ViewModels free of Purchases imports.
 *
 * The real implementation is [RevenueCatManager]; previews use [PreviewRevenueCatRepository].
 */
interface RevenueCatRepository {

    /** Real-time subscription state: Loading → Active/Inactive/Error. */
    val subscriptionState: StateFlow<RevenueCatState>

    /**
     * Whether the signed-in user has the Omni+ entitlement.
     * Convenience wrapper around `subscriptionState` for common checks.
     */
    fun hasOmniPlus(): Boolean

    /**
     * Launch the subscription purchase flow for the given package ID.
     * Package IDs come from [offerings].
     */
    suspend fun purchase(activity: android.app.Activity, packageId: String)

    /**
     * Restore purchases — reconnects an existing subscription to this device/account.
     * After this call, [subscriptionState] is updated with the restored state.
     */
    suspend fun restorePurchases()

    /**
     * The current offering packages available for purchase.
     * Maps packageId → display name for the paywall.
     */
    fun offerings(): Map<String, String>
}
