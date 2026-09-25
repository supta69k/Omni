package com.example.omni.data.model

/**
 * A verified doctor available for text-based consultation.
 * Stored in the `doctors` Firestore collection.
 */
data class Doctor(
    val uid: String,
    val name: String,
    val specialty: String,
    val bio: String = "",
    val photoUrl: String? = null,
    val yearsExperience: Int = 0,
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val languages: List<String> = emptyList(),
    val consultationFee: String = "",
    val available: Boolean = true,
    val verified: Boolean = true,
)

/**
 * The result of a doctor consultation session.
 */
data class Consultation(
    val id: String,
    val doctorUid: String,
    val patientUid: String,
    val status: ConsultationStatus = ConsultationStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
)

enum class ConsultationStatus {
    ACTIVE,
    RESOLVED,
    CANCELLED,
}
