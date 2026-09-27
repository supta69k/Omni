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
     * The single source of truth for premium access: `true` only while the `omni_pro` entitlement is
     * currently active. Every premium gate in the app observes this one flow rather than re-deriving
     * the check. It is `false` while [subscriptionState] is Loading, Inactive or Error — premium is
     * never granted by default, only by an active entitlement (never `hasEverPurchased`).
     */
    val isOmniPlusActive: StateFlow<Boolean>

    /**
     * The active Omni+ subscription's details for the Settings "current plan" area, from RevenueCat's
     * `CustomerInfo`. `null` whenever Omni+ is not active.
     */
    val subscription: StateFlow<OmniPlusSubscription?>

    /**
     * Whether the signed-in user has the Omni+ entitlement.
     * Convenience wrapper around `subscriptionState` for common checks.
     */
    fun hasOmniPlus(): Boolean

    /**
     * Launch the subscription purchase flow for the given package ID.
     * Package IDs come from [packages].
     */
    suspend fun purchase(activity: android.app.Activity, packageId: String)

    /**
     * Restore purchases — reconnects an existing subscription to this device/account.
     * After this call, [subscriptionState] is updated with the restored state.
     */
    suspend fun restorePurchases()

    /**
     * Re-fetch the current offering from RevenueCat. Called once at initialization and again
     * every time the paywall opens, so a user who was offline at app start gets a retry.
     */
    suspend fun refreshOfferings()

    /**
     * The current offering's packages, as fetched from the store.
     * Empty until a fetch succeeds; [packagesError] says why when it stays that way.
     */
    val packages: StateFlow<List<OfferingPackage>>

    /** Why [packages] is empty — a failed fetch, or no current offering in the dashboard. */
    val packagesError: StateFlow<String?>
}

/** One purchasable plan on the paywall, taken from the current offering's packages. */
data class OfferingPackage(
    val id: String,
    val name: String,
    val price: String,
)
