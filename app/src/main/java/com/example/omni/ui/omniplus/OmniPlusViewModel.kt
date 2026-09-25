package com.example.omni.ui.omniplus

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.revenuecat.RevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        viewModelScope.launch {
            revenueCatRepository.subscriptionState.collect { state ->
                val offerings = revenueCatRepository.offerings()
                _uiState.value = _uiState.value.copy(
                    subscriptionState = state,
                    availablePackages = offerings.map { (id, name) ->
                        PackageOption(id = id, name = name, price = "")
                    },
                    error = if (state is RevenueCatState.Error) state.message else null,
                    successMessage = if (state is RevenueCatState.Active) "Welcome to Omni+!" else null,
                )
            }
        }
    }

    fun purchase(activity: Activity, packageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPurchasing = true, error = null)
            try {
                revenueCatRepository.purchase(activity, packageId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isPurchasing = false)
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
