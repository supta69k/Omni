package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.toHospital
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The pharmacy half of the SOS directory, over the same contract as [HospitalRepository].
 *
 * Everything the hospital repository's header argues — seeded Firestore rather than the Places API,
 * offline by cache, zero marginal cost, haversine measured locally, one listener instead of a
 * geoquery — applies to this collection verbatim, because the pharmacies were seeded by the very
 * same script from the very same OpenStreetMap export (`tools/seed-hospitals.mjs --collection
 * pharmacies`). Writing a second repository rather than widening the first keeps the two
 * directories' listeners independent: a pharmacy import that fails or a collection that is slower
 * to first sync cannot delay or empty the hospital list, which is the half an emergency actually
 * needs.
 *
 * The documents come back as [Hospital]s carrying `isPharmacy = true` — one shape for both halves,
 * because everything downstream (distance, search, selection, routing, dialling) is identical code.
 */
interface PharmacyRepository {
    /** The whole pharmacy directory, alphabetical, with no distances — see [HospitalRepository.observeAll]. */
    fun observeAll(): Flow<List<Hospital>>
}

class FirestorePharmacyRepository(
    private val firestore: FirebaseFirestore,
) : PharmacyRepository {

    override fun observeAll(): Flow<List<Hospital>> = callbackFlow {
        val registration = firestore.collection(Pharmacies)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Omni", "The pharmacy directory could not be read", error)
                    // Same degradation as the hospital directory: cache-first, and an honest empty
                    // list when even the cache is unavailable.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { it.toHospital(isPharmacy = true) }
                        .sortedBy { it.name },
                )
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val Pharmacies = "pharmacies"
    }
}
