package com.example.omni.ui.omniplus

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.example.omni.data.model.Doctor
import com.example.omni.data.repo.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoctorProfileUiState(
    val doctor: Doctor? = null,
    val isLoading: Boolean = true,
)

class DoctorProfileViewModel(
    private val doctorRepository: DoctorRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val profileUid: String? = savedStateHandle.get<String>("doctorUid")

    private val _uiState = MutableStateFlow(DoctorProfileUiState())
    val uiState: StateFlow<DoctorProfileUiState> = _uiState.asStateFlow()

    init {
        if (profileUid != null) {
            viewModelScope.launch {
                val doctor = doctorRepository.getDoctor(profileUid)
                _uiState.value = _uiState.value.copy(doctor = doctor, isLoading = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    companion object {
        fun factory(
            doctorRepository: DoctorRepository,
            owner: SavedStateRegistryOwner,
            defaultDoctorUid: String?,
        ): AbstractSavedStateViewModelFactory = object : AbstractSavedStateViewModelFactory() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle,
            ): T {
                handle["doctorUid"] = defaultDoctorUid
                return DoctorProfileViewModel(doctorRepository, handle) as T
            }
        }
    }
}
