package com.example.omni.data.repo

import com.example.omni.data.model.Doctor
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the Omni+ Doctor Consultation feature.
 */
interface DoctorRepository {
    /**
     * Observe the list of available verified doctors.
     */
    fun observeDoctors(): Flow<List<Doctor>>

    /**
     * Get a single doctor by uid.
     */
    suspend fun getDoctor(uid: String): Doctor?
}
