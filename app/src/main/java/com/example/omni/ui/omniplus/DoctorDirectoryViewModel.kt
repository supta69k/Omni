package com.example.omni.ui.omniplus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Doctor
import com.example.omni.data.repo.DoctorRepository
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorDirectoryUiState())
    val uiState: StateFlow<DoctorDirectoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            doctorRepository.observeDoctors().collect { doctors ->
                val specialties = doctors.map { it.specialty }.distinct().sorted()
                _uiState.value = _uiState.value.copy(
                    doctors = doctors,
                    isLoading = false,
                    specialties = specialties,
                )
            }
        }
    }

    fun selectSpecialty(specialty: String?) {
        _uiState.value = _uiState.value.copy(selectedSpecialty = specialty)
    }
}
