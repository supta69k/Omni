package com.example.omni.data.repo

import com.example.omni.data.model.Doctor
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreDoctorRepository(
    private val firestore: FirebaseFirestore,
) : DoctorRepository {

    override fun observeDoctors(): Flow<List<Doctor>> = callbackFlow {
        val listener = firestore.collection("doctors")
            .whereEqualTo("available", true)
            .orderBy("rating", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val doctors = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Doctor::class.java)
                } ?: emptyList()
                trySend(doctors)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getDoctor(uid: String): Doctor? {
        return try {
            firestore.collection("doctors").document(uid).get().await()
                .toObject(Doctor::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
