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

/** Which billing period the active Omni+ subscription renews on — drives the Settings plan label. */
enum class OmniPlusPeriod { MONTHLY, YEARLY, OTHER }

/**
 * The active Omni+ subscription's details, taken straight from RevenueCat's `CustomerInfo` — never
 * computed or persisted locally. `null` whenever Omni+ is not active.
 *
 * @property managementUrl RevenueCat's `CustomerInfo.managementURL` (the Play "manage subscription"
 *   destination). Can be `null` — e.g. Test Store purchases have no Play management page — in which
 *   case Settings falls back to the generic Play subscriptions screen.
 */
data class OmniPlusSubscription(
    val productId: String,
    val period: OmniPlusPeriod,
    val managementUrl: String?,
    val willRenew: Boolean,
)
