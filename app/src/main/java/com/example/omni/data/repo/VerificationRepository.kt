package com.example.omni.data.repo

import com.example.omni.data.model.Profession
import com.example.omni.data.model.VerificationRequest
import com.example.omni.data.model.toVerificationRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Applications to be listed as a healthcare professional — `verificationRequests/{uid}`
 * (BACKEND_PLAN §7 and Phase 12).
 *
 * The client's whole half of the protocol is one write: submit, with `status: "pending"`. Everything
 * that happens next — approval, the `verified` flag on the profile, the professional badge in the
 * feed — is a reviewer's action against rules that forbid the phone from performing it. That is the
 * point: a badge a user could award themselves would be worth nothing on a health app.
 *
 * **The status string is lower-case on the wire** (`"pending"`), because `firestore.rules` compares
 * against that literal. The enum is upper-case in Kotlin and the mapper reads either, so a reviewer
 * typing `Approved` in the console still lands.
 */
interface VerificationRepository {

    /** This account's application, live — `null` when they have never applied. */
    fun observeRequest(uid: String): Flow<VerificationRequest?>

    /**
     * Submits or re-submits an application.
     *
     * Re-submission is a `set` over the same document rather than a second one: the id is the uid, so
     * an account has exactly one application and a reviewer never has to work out which of three is
     * current. The rules allow the overwrite only from a rejected application, so a pending one
     * cannot be edited out from under a reviewer who is reading it.
     */
    suspend fun submit(uid: String, name: String, profession: Profession, licenseNumber: String)
}

class FirestoreVerificationRepository(
    private val firestore: FirebaseFirestore,
) : VerificationRepository {

    override fun observeRequest(uid: String): Flow<VerificationRequest?> = callbackFlow {
        val registration = document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toVerificationRequest())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun submit(
        uid: String,
        name: String,
        profession: Profession,
        licenseNumber: String,
    ) {
        document(uid).set(
            mapOf(
                "uid" to uid,
                "name" to name,
                "profession" to profession.name,
                "licenseNumber" to licenseNumber,
                "status" to PendingStatus,
                "submittedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun document(uid: String) = firestore.collection(Requests).document(uid)

    private companion object {
        const val Requests = "verificationRequests"

        /** Lower-case, because that is the literal `firestore.rules` compares the create against. */
        const val PendingStatus = "pending"
    }
}

/**
 * The preview fake: nobody has applied, and applying is a no-op.
 *
 * A preview of the apply page should draw the empty form, which is exactly what "no application"
 * renders — and a `@Preview` that reached Firestore would throw before drawing anything.
 */
class PreviewVerificationRepository : VerificationRepository {
    override fun observeRequest(uid: String): Flow<VerificationRequest?> = flowOf(null)
    override suspend fun submit(
        uid: String,
        name: String,
        profession: Profession,
        licenseNumber: String,
    ) = Unit
}
