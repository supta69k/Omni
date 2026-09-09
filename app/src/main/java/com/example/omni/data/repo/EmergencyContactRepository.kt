package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.EmergencyContact
import com.example.omni.data.model.toEmergencyContact
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The people to reach when SOS is activated — `users/{uid}/emergencyContacts/{contactId}`
 * (BACKEND_PLAN §7, §11 Phase 9).
 *
 * Separate from [UserRepository] even though both live under `users/{uid}`, for the same reason the
 * day documents are: the profile is one document read on every screen, and a contact list nobody has
 * opened should not be riding along with it on the header's listener.
 */
interface EmergencyContactRepository {
    /** Emits the contact list on every change, and an empty list while there is none. */
    fun observe(uid: String): Flow<List<EmergencyContact>>

    /** Adds one contact and returns its new document id. */
    suspend fun add(uid: String, name: String, phone: String, relation: String): String

    suspend fun delete(uid: String, contactId: String)
}

class FirestoreEmergencyContactRepository(
    private val firestore: FirebaseFirestore,
) : EmergencyContactRepository {

    override fun observe(uid: String): Flow<List<EmergencyContact>> = callbackFlow {
        val registration = contacts(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("Omni", "users/$uid/emergencyContacts could not be read", error)
                // Not fatal and not closed: an empty list is what the screen already renders before
                // the first contact exists, so the degradation needs no second state.
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty()
                    .mapNotNull { it.toEmergencyContact() }
                    .sortedBy { it.name.lowercase() },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(uid: String, name: String, phone: String, relation: String): String {
        val document = contacts(uid).document()
        document
            .set(
                mapOf(
                    "name" to name,
                    "phone" to phone,
                    "relation" to relation,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            // `set` on a pre-allocated id rather than `add`, so the id exists before the round-trip
            // and an offline write still has one to return.
            .await()
        return document.id
    }

    override suspend fun delete(uid: String, contactId: String) {
        contacts(uid).document(contactId).delete().await()
    }

    private fun contacts(uid: String) =
        firestore.collection(Users).document(uid).collection(EmergencyContacts)

    private companion object {
        const val Users = "users"
        const val EmergencyContacts = "emergencyContacts"
    }
}

/**
 * The preview twin — one in-memory list, no Firebase.
 *
 * It starts with the two contacts a screenshot wants and stays mutable, so the add and delete paths
 * are exercisable in an interactive preview rather than looking inert.
 */
class PreviewEmergencyContactRepository(
    initial: List<EmergencyContact> = PreviewContacts,
) : EmergencyContactRepository {

    private val contacts = MutableStateFlow(initial)
    private var nextId = initial.size

    override fun observe(uid: String): Flow<List<EmergencyContact>> = contacts

    override suspend fun add(uid: String, name: String, phone: String, relation: String): String {
        val id = "preview-${nextId++}"
        contacts.value = contacts.value + EmergencyContact(id, name, phone, relation)
        return id
    }

    override suspend fun delete(uid: String, contactId: String) {
        contacts.value = contacts.value.filterNot { it.id == contactId }
    }

    private companion object {
        val PreviewContacts = listOf(
            EmergencyContact("preview-0", "Ammu", "+8801711000000", "Mother"),
            EmergencyContact("preview-1", "Dr Rahman", "+8801811000000", "Physician"),
        )
    }
}
