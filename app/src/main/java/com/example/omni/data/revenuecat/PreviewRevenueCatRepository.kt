package com.example.omni.data.revenuecat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for Compose previews and unit tests.
 * Simulates an inactive Omni+ subscription by default, with the two packages the paywall's
 * previews are designed around.
 */
class PreviewRevenueCatRepository(
    private val initialState: RevenueCatState = RevenueCatState.Inactive,
) : RevenueCatRepository {

    private val _state = MutableStateFlow(initialState)
    override val subscriptionState: StateFlow<RevenueCatState> = _state.asStateFlow()

    private val _isOmniPlusActive = MutableStateFlow(hasOmniPlus())
    override val isOmniPlusActive: StateFlow<Boolean> = _isOmniPlusActive.asStateFlow()

    private val _subscription = MutableStateFlow(
        if (hasOmniPlus()) {
            OmniPlusSubscription(
                productId = "omni_plus_monthly",
                period = OmniPlusPeriod.MONTHLY,
                managementUrl = null,
                willRenew = true,
            )
        } else {
            null
        },
    )
    override val subscription: StateFlow<OmniPlusSubscription?> = _subscription.asStateFlow()

    override val packages: StateFlow<List<OfferingPackage>> = MutableStateFlow(
        listOf(
            OfferingPackage(id = "omni_plus_monthly", name = "Omni+ Monthly", price = "$4.99"),
            OfferingPackage(id = "omni_plus_yearly", name = "Omni+ Yearly", price = "$39.99"),
        ),
    )

    override val packagesError: StateFlow<String?> = MutableStateFlow(null)

    override fun hasOmniPlus(): Boolean {
        val state = subscriptionState.value
        return state is RevenueCatState.Active && state.entitlements[EntitlementIds.OMNI_PLUS] == true
    }

    override suspend fun purchase(activity: android.app.Activity, packageId: String) {
        // Simulate successful purchase for preview
        _state.value = RevenueCatState.Active(mapOf(EntitlementIds.OMNI_PLUS to true))
        _isOmniPlusActive.value = true
        _subscription.value = OmniPlusSubscription(
            productId = packageId,
            period = if ("year" in packageId.lowercase() || "annual" in packageId.lowercase()) {
                OmniPlusPeriod.YEARLY
            } else {
                OmniPlusPeriod.MONTHLY
            },
            managementUrl = null,
            willRenew = true,
        )
    }

    override suspend fun refreshOfferings() {
        // Previews stay static — the fake packages above never change.
    }

    override suspend fun restorePurchases() {
        // No-op for preview
    }
}
