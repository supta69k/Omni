package com.example.omni.data.revenuecat

import android.app.Activity
import android.app.Application
import android.util.Log
import com.example.omni.data.repo.AuthRepository
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * RevenueCat SDK wrapper with Firebase UID synchronization.
 *
 * This is the real implementation behind [RevenueCatRepository]. It initializes the Purchases SDK,
 * syncs the RevenueCat user identity with Firebase UID (preventing cross-account premium leaks),
 * listens to entitlement updates, and exposes the subscription state as a flow.
 *
 * Account isolation: when [AuthRepository.sessionUid] changes, this logs into RevenueCat with the
 * new Firebase UID, ensuring Account A's premium state never appears in Account B.
 */
class RevenueCatManager(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val apiKey: String,
) : RevenueCatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _subscriptionState = MutableStateFlow<RevenueCatState>(RevenueCatState.Loading)
    override val subscriptionState: StateFlow<RevenueCatState> = _subscriptionState.asStateFlow()

    private val _packages = MutableStateFlow<List<OfferingPackage>>(emptyList())
    override val packages: StateFlow<List<OfferingPackage>> = _packages.asStateFlow()

    private val _packagesError = MutableStateFlow<String?>(null)
    override val packagesError: StateFlow<String?> = _packagesError.asStateFlow()

    init {
        if (apiKey.isBlank()) {
            Log.e(TAG, "RevenueCat API key is blank — monetization disabled")
            _subscriptionState.value = RevenueCatState.Error("API key not configured")
        } else {
            initializeRevenueCat()
            syncWithAuthState()
            // Fetch the current offering up front — the paywall reads [packages], which used to
            // stay empty until a purchase touched getOfferings, leaving it stuck on "Loading plans…".
            scope.launch { refreshOfferings() }
        }
    }

    private fun initializeRevenueCat() {
        val config = PurchasesConfiguration.Builder(application, apiKey).build()
        Purchases.configure(config)

        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
            Log.d(TAG, "CustomerInfo updated from SDK listener")
            handleCustomerInfo(customerInfo)
        }

        Log.i(TAG, "RevenueCat initialized")
    }

    private fun syncWithAuthState() {
        scope.launch {
            authRepository.sessionUid.collect { firebaseUid ->
                if (firebaseUid == null) {
                    Log.d(TAG, "Auth session ended — logging out of RevenueCat")
                    Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            Log.d(TAG, "RevenueCat logout complete")
                            _subscriptionState.value = RevenueCatState.Inactive
                        }

                        override fun onError(error: PurchasesError) {
                            Log.w(TAG, "RevenueCat logout error: ${error.message}")
                            _subscriptionState.value = RevenueCatState.Inactive
                        }
                    })
                } else {
                    Log.d(TAG, "Auth session started — logging into RevenueCat with UID")
                    Purchases.sharedInstance.logIn(
                        newAppUserID = firebaseUid,
                        callback = object : LogInCallback {
                            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                                Log.d(TAG, "RevenueCat login successful, created=$created")
                                handleCustomerInfo(customerInfo)
                            }

                            override fun onError(error: PurchasesError) {
                                Log.e(TAG, "RevenueCat login failed: ${error.message}")
                                _subscriptionState.value = RevenueCatState.Error(error.message)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleCustomerInfo(customerInfo: CustomerInfo) {
        val activeEntitlements = customerInfo.entitlements.active.mapValues { (_, info) ->
            info.isActive
        }

        _subscriptionState.value = if (activeEntitlements.isEmpty()) {
            RevenueCatState.Inactive
        } else {
            RevenueCatState.Active(activeEntitlements)
        }

        Log.d(TAG, "Active entitlements: ${activeEntitlements.keys}")
    }

    override fun hasOmniPlus(): Boolean {
        val state = subscriptionState.value
        return state is RevenueCatState.Active && state.entitlements[EntitlementIds.OMNI_PLUS] == true
    }

    override suspend fun purchase(activity: Activity, packageId: String) {
        if (!ensureConfigured()) return
        return suspendCoroutine { cont ->
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    val current = offerings.current
                    if (current == null) {
                        Log.e(TAG, "No current offering available")
                        _subscriptionState.value = RevenueCatState.Error("No subscription available")
                        cont.resume(Unit)
                        return
                    }

                    val packageToPurchase = current.availablePackages.find { it.identifier == packageId }
                    if (packageToPurchase == null) {
                        Log.e(TAG, "Package $packageId not found in offerings")
                        _packagesError.value = "Package $packageId is not in the current offering"
                        cont.resume(Unit)
                        return
                    }

                    val purchaseParams = PurchaseParams.Builder(activity, packageToPurchase).build()
                    Purchases.sharedInstance.purchase(purchaseParams, object : PurchaseCallback {
                        override fun onCompleted(transaction: StoreTransaction, customerInfo: CustomerInfo) {
                            Log.i(TAG, "Purchase successful")
                            handleCustomerInfo(customerInfo)
                            cont.resume(Unit)
                        }

                        override fun onError(error: PurchasesError, userCancelled: Boolean) {
                            if (userCancelled) {
                                Log.d(TAG, "Purchase cancelled by user")
                            } else {
                                Log.e(TAG, "Purchase error: ${error.message}")
                                _subscriptionState.value = RevenueCatState.Error(error.message)
                            }
                            cont.resume(Unit)
                        }
                    })
                }

                override fun onError(error: PurchasesError) {
                    Log.e(TAG, "Failed to fetch offerings: ${error.message}")
                    _subscriptionState.value = RevenueCatState.Error(error.message)
                    cont.resume(Unit)
                }
            })
        }
    }

    override suspend fun refreshOfferings() {
        if (apiKey.isBlank()) {
            Log.e(TAG, "refreshOfferings skipped — API key is blank")
            return
        }
        // Resumes with the error message (null = success), which becomes [packagesError].
        // Failures land there rather than in [subscriptionState]: a failed plan fetch is a
        // paywall concern, and overwriting the subscription state here would make a network
        // blip look like an entitlement problem.
        _packagesError.value = suspendCoroutine { cont ->
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    val current = offerings.current
                    if (current == null) {
                        Log.w(TAG, "RevenueCat returned no current offering — is one marked Current in the dashboard?")
                        cont.resume("No plans are published yet — check the RevenueCat dashboard")
                        return
                    }
                    _packages.value = current.availablePackages.map { pkg: Package ->
                        OfferingPackage(
                            id = pkg.identifier,
                            name = pkg.product.name.ifBlank { pkg.product.title },
                            // StoreProduct.price is the SDK's Price object; `formatted` is the
                            // localized string Play shows ("$4.99", "US$4.99", …).
                            price = pkg.product.price.formatted,
                        )
                    }
                    Log.i(TAG, "Offerings loaded: ${current.availablePackages.map { it.identifier }}")
                    cont.resume(null)
                }

                override fun onError(error: PurchasesError) {
                    Log.w(
                        TAG,
                        "Offerings fetch failed: ${error.message} (code=${error.code}, " +
                            "underlying=${error.underlyingErrorMessage ?: "none"})",
                    )
                    cont.resume(error.message?.ifBlank { null } ?: "Couldn't load plans")
                }
            })
        }
    }

    override suspend fun restorePurchases() {
        if (!ensureConfigured()) return
        return suspendCoroutine { cont ->
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    Log.i(TAG, "Purchases restored")
                    handleCustomerInfo(customerInfo)
                    cont.resume(Unit)
                }

                override fun onError(error: PurchasesError) {
                    Log.w(TAG, "Restore purchases failed: ${error.message}")
                    _subscriptionState.value = RevenueCatState.Error(error.message)
                    cont.resume(Unit)
                }
            })
        }
    }

    /**
     * Guards every SDK entry point: `Purchases.sharedInstance` throws when the SDK was never
     * configured, so a blank key must fail into the error state instead of crashing the paywall.
     */
    private fun ensureConfigured(): Boolean {
        if (apiKey.isBlank()) {
            Log.e(TAG, "RevenueCat call skipped — API key is blank")
            _subscriptionState.value = RevenueCatState.Error("API key not configured")
            return false
        }
        return true
    }

    private companion object {
        const val TAG = "RevenueCat"
    }
}
