package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.Doctor
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreDoctorRepository(
    private val firestore: FirebaseFirestore,
) : DoctorRepository {

    override fun observeDoctors(): Flow<List<Doctor>> = callbackFlow {
        // Filter on `available` only; sort by rating in Kotlin. The old `orderBy("rating")` needed a
        // composite index (which fails the whole query, emptying the directory, until it is built) AND
        // silently dropped any doctor document missing a `rating` field. A single-field equality query
        // needs no composite index and returns every available doctor, so a verified doctor always shows.
        val listener = firestore.collection("doctors")
            .whereEqualTo("available", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // A rejected listen (rules, offline) must not take the app down — the directory
                    // renders empty and the listener stays alive, so the query recovers on its own.
                    Log.w(TAG, "doctors listen failed: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val doctors = snapshot?.documents?.mapNotNull { it.toDoctor() }
                    ?.sortedByDescending { it.rating }
                    ?: emptyList()
                trySend(doctors)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getDoctor(uid: String): Doctor? {
        return try {
            firestore.collection("doctors").document(uid).get().await().toDoctor()
        } catch (e: Exception) {
            Log.w(TAG, "getDoctor($uid) failed: ${e.message}")
            null
        }
    }

    /**
     * Manual mapping, matching [FirestoreSleepRepository]'s pattern rather than `toObject`:
     * [Doctor] has required constructor parameters, so Firestore's reflective mapper would throw
     * "does not define a no-argument constructor" the moment a document exists. Console-written
     * numbers also land as Long or Double depending on what was typed, so every numeric field is
     * read tolerantly.
     */
    private fun DocumentSnapshot.toDoctor(): Doctor? {
        val name = getString("name") ?: return null
        val specialty = getString("specialty") ?: return null
        return Doctor(
            uid = getString("uid") ?: id,
            name = name,
            specialty = specialty,
            bio = getString("bio").orEmpty(),
            photoUrl = getString("photoUrl"),
            yearsExperience = getLong("yearsExperience")?.toInt() ?: 0,
            rating = (getDouble("rating") ?: getLong("rating")?.toDouble())?.toFloat() ?: 0f,
            reviewCount = getLong("reviewCount")?.toInt() ?: 0,
            languages = (get("languages") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            consultationFee = getString("consultationFee").orEmpty(),
            available = getBoolean("available") ?: true,
            verified = getBoolean("verified") ?: true,
        )
    }

    private companion object {
        const val TAG = "DoctorRepository"
    }
}
