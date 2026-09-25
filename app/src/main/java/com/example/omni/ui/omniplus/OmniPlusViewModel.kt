package com.example.omni.ui.omniplus

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.revenuecat.RevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class OmniPlusUiState(
    val subscriptionState: RevenueCatState = RevenueCatState.Loading,
    val isPurchasing: Boolean = false,
    val purchasePackageId: String? = null,
    val isRestoring: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val availablePackages: List<PackageOption> = emptyList(),
)

data class PackageOption(
    val id: String,
    val name: String,
    val price: String,
)

class OmniPlusViewModel(
    private val revenueCatRepository: RevenueCatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OmniPlusUiState())
    val uiState: StateFlow<OmniPlusUiState> = _uiState.asStateFlow()

    init {
        // Fresh offerings every time the paywall opens — the manager also fetches once at init,
        // so a user who was offline at app start still gets plans on returning to this screen.
        viewModelScope.launch {
            revenueCatRepository.refreshOfferings()
        }

        // One subscription builds the whole state from the repository's three sources, so the
        // paywall can never show a plan list that disagrees with the subscription state. The
        // transient purchase/restore flags live outside this combine (see the copy below) because
        // purchase() and restore() toggle them on the state directly.
        viewModelScope.launch {
            combine(
                revenueCatRepository.subscriptionState,
                revenueCatRepository.packages,
                revenueCatRepository.packagesError,
            ) { subscriptionState, packages, packagesError ->
                OmniPlusUiState(
                    subscriptionState = subscriptionState,
                    availablePackages = packages.map { pkg ->
                        PackageOption(id = pkg.id, name = pkg.name, price = pkg.price)
                    },
                    error = packagesError ?: (subscriptionState as? RevenueCatState.Error)?.message,
                    successMessage = if (subscriptionState is RevenueCatState.Active) "Welcome to Omni+!" else null,
                )
            }.collect { rebuilt ->
                _uiState.value = rebuilt.copy(
                    isPurchasing = _uiState.value.isPurchasing,
                    purchasePackageId = _uiState.value.purchasePackageId,
                    isRestoring = _uiState.value.isRestoring,
                )
            }
        }
    }

    fun purchase(activity: Activity, packageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPurchasing = true, purchasePackageId = packageId, error = null)
            try {
                revenueCatRepository.purchase(activity, packageId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isPurchasing = false, purchasePackageId = null)
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, error = null)
            try {
                revenueCatRepository.restorePurchases()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isRestoring = false)
            }
        }
    }
}
