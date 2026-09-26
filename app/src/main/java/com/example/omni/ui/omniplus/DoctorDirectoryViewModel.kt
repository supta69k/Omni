package com.example.omni.ui.omniplus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Doctor
import com.example.omni.data.repo.DoctorRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoctorDirectoryUiState(
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSpecialty: String? = null,
    val specialties: List<String> = emptyList(),
)

class DoctorDirectoryViewModel(
    private val doctorRepository: DoctorRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorDirectoryUiState())
    val uiState: StateFlow<DoctorDirectoryUiState> = _uiState.asStateFlow()

    /** Live photos resolved from `users/{uid}`, one read per doctor, cached across re-emissions. */
    private val resolvedPhotos = mutableMapOf<String, String?>()

    init {
        viewModelScope.launch {
            doctorRepository.observeDoctors().collect { doctors ->
                val withPhotos = withLivePhotos(doctors)
                val specialties = withPhotos.map { it.specialty }.distinct().sorted()
                _uiState.value = _uiState.value.copy(
                    doctors = withPhotos,
                    isLoading = false,
                    specialties = specialties,
                )
            }
        }
    }

    /**
     * Fills in a doctor's photo from their account when the directory tile has none.
     *
     * The tile's `photoUrl` is written at approval time and can lag (a doctor seeded before the copy
     * existed, or one who changed their photo since). The account document is the live source, and it
     * is readable, so a tile without a photo is enriched with the account's — one read per doctor,
     * cached so a re-emission of the directory does not re-read.
     */
    private suspend fun withLivePhotos(doctors: List<Doctor>): List<Doctor> {
        doctors.forEach { doctor ->
            if (doctor.photoUrl.isNullOrBlank() && doctor.uid !in resolvedPhotos) {
                resolvedPhotos[doctor.uid] = runCatching { userRepository.getUser(doctor.uid)?.photoUrl }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return doctors.map { doctor ->
            if (!doctor.photoUrl.isNullOrBlank()) doctor
            else resolvedPhotos[doctor.uid]?.let { doctor.copy(photoUrl = it) } ?: doctor
        }
    }

    fun selectSpecialty(specialty: String?) {
        _uiState.value = _uiState.value.copy(selectedSpecialty = specialty)
    }
}
