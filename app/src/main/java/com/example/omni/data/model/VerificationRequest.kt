package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Where an application to be listed as a healthcare professional stands (BACKEND_PLAN §7,
 * `verificationRequests/{uid}`).
 *
 * The applicant may only ever write [PENDING]; the rules forbid them setting anything else, so a
 * decision is something that happens *to* the document rather than something the phone can claim.
 */
enum class VerificationStatus { PENDING, APPROVED, REJECTED }

/**
 * One application — the `verificationRequests/{uid}` document.
 *
 * The document id is the applicant's uid, so an account has exactly one application and re-applying
 * replaces it rather than queueing a second one for a reviewer to reconcile.
 *
 * @property note the reviewer's sentence, present on a rejection and the only thing that tells the
 *   applicant what to fix. Absent while pending.
 */
data class VerificationRequest(
    val uid: String,
    val name: String,
    val profession: Profession,
    val licenseNumber: String,
    val status: VerificationStatus = VerificationStatus.PENDING,
    val submittedAt: Long = 0L,
    val reviewedAt: Long = 0L,
    val note: String? = null,
)

/**
 * The document as an application, or `null` when there is none.
 *
 * Hand-mapped like every other read in this app: a reviewer typing `Approved` into the console must
 * degrade to "still pending" rather than crash the settings page. An unreadable profession is the one
 * field with no safe default — an application whose profession nobody can read is not an application
 * — so it drops the whole document instead of guessing DOCTOR.
 */
fun DocumentSnapshot.toVerificationRequest(): VerificationRequest? {
    if (!exists()) return null
    val profession = Profession.entries.firstOrNull { it.name == getString("profession") }
        ?: return null
    return VerificationRequest(
        uid = id,
        name = getString("name").orEmpty(),
        profession = profession,
        licenseNumber = getString("licenseNumber").orEmpty(),
        status = VerificationStatus.entries
            .firstOrNull { it.name.equals(getString("status"), ignoreCase = true) }
            ?: VerificationStatus.PENDING,
        submittedAt = millisOf("submittedAt"),
        reviewedAt = millisOf("reviewedAt"),
        note = getString("note")?.takeIf { it.isNotBlank() },
    )
}
