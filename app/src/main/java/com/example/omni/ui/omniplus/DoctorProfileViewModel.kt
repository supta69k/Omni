package com.example.omni.ui.omniplus

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.example.omni.data.model.Doctor
import com.example.omni.data.repo.DoctorRepository
import com.example.omni.data.repo.UserRepository
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
    private val userRepository: UserRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val profileUid: String? = savedStateHandle.get<String>("doctorUid")

    private val _uiState = MutableStateFlow(DoctorProfileUiState())
    val uiState: StateFlow<DoctorProfileUiState> = _uiState.asStateFlow()

    init {
        if (profileUid != null) {
            viewModelScope.launch {
                val doctor = doctorRepository.getDoctor(profileUid)
                // The tile's photo is written at approval time and can lag; the account document is the
                // live source, so fill it in when the tile has none (same reason as the directory).
                val withPhoto = if (doctor != null && doctor.photoUrl.isNullOrBlank()) {
                    val livePhoto = runCatching { userRepository.getUser(profileUid)?.photoUrl }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                    if (livePhoto != null) doctor.copy(photoUrl = livePhoto) else doctor
                } else {
                    doctor
                }
                _uiState.value = _uiState.value.copy(doctor = withPhoto, isLoading = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    companion object {
        fun factory(
            doctorRepository: DoctorRepository,
            userRepository: UserRepository,
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
                return DoctorProfileViewModel(doctorRepository, userRepository, handle) as T
            }
        }
    }
}
