package com.example.omni.data.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * The SOS event log — `sosEvents/{uid}/items/{id}` (BACKEND_PLAN §7).
 *
 * An activation is written once and never updated: the record exists so a later investigation (or a
 * report) can reconstruct when and where the button was pressed, not to be a live tracker. The rules
 * let only the owner write it, and only create-shaped writes happen here.
 */
interface SosRepository {
    /** Records one activation at (`lat`, `lng`) with the device's reported accuracy, if any. */
    suspend fun recordActivation(uid: String, lat: Double, lng: Double, accuracyMeters: Float?)
}

class FirestoreSosRepository(
    private val firestore: FirebaseFirestore,
) : SosRepository {

    override suspend fun recordActivation(uid: String, lat: Double, lng: Double, accuracyMeters: Float?) {
        firestore.collection(SosEvents).document(uid).collection(Items)
            .add(
                mapOf(
                    "lat" to lat,
                    "lng" to lng,
                    "accuracy" to accuracyMeters,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    private companion object {
        const val SosEvents = "sosEvents"
        const val Items = "items"
    }
}
