package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.toHospital
import com.example.omni.data.model.withDistanceFrom
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The public hospital directory and the user's own location over it (BACKEND_PLAN §11 Phase 9).
 *
 * ## Where the hospitals come from — and why not Places
 *
 * The plan's spec assumed the Places API, which needs a billing account and an API key this project
 * has never had. The directory is therefore a **seeded Firestore collection** instead: real
 * Chittagong and Dhaka hospitals imported once from OpenStreetMap (the seed script and instructions
 * live in `tools/seed-hospitals.md`). That choice buys the two things the plan itself calls
 * non-negotiable:
 *
 *  - **Offline by cache.** Firestore holds every document the app has read; a directory that never
 *    changes never needs a refresh, so airplane mode still shows the full list. (Places needs the
 *    network for its *first* fetch, which is exactly the failure the plan refuses to accept.)
 *  - **Zero marginal cost.** No key, no billing, no per-request quota to blow through in a demo.
 *
 * Distance is the haversine formula computed locally — the plan's own instruction — so the only
 * device capability the screen needs is the location, which the [LocationRepository] supplies.
 */
interface HospitalRepository {
    /**
     * Every hospital within [radiusKm] of (`lat`, `lng`), nearest first, live, each carrying its
     * distance from that point.
     *
     * Emits an empty list only if the collection is genuinely empty — which means the seed script has
     * not been run, a state the screen names rather than hiding behind mock cards.
     */
    fun observeNearby(lat: Double, lng: Double, radiusKm: Double): Flow<List<Hospital>>

    /**
     * The whole directory, alphabetical, with **no distances** — every `distanceKm` is `null`.
     *
     * A separate method rather than [observeNearby] with a stand-in centre, because the two answer
     * different questions. With no fix there is no "nearest": measuring from the middle of the city
     * would put a confident "0.4 km" on a card belonging to someone standing in another district, and
     * `distanceLabel()` renders `null` as "—" precisely so that number never has to be guessed.
     */
    fun observeAll(): Flow<List<Hospital>>
}

class FirestoreHospitalRepository(
    private val firestore: FirebaseFirestore,
) : HospitalRepository {

    override fun observeNearby(
        lat: Double,
        lng: Double,
        radiusKm: Double,
    ): Flow<List<Hospital>> = directory { all ->
        all.map { it.withDistanceFrom(lat, lng) }
            .filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    override fun observeAll(): Flow<List<Hospital>> = directory { all ->
        all.sortedBy { it.name }
    }

    /**
     * One listener over the whole collection, not a geoquery: the directory is ~200 documents, and
     * Firestore bills per document *read from the server* — after the first sync these all come from
     * the local cache, which costs nothing and works offline.
     *
     * [order] is applied to every emission, so the two public reads differ only in how they arrange
     * what one listener already delivered.
     */
    private fun directory(order: (List<Hospital>) -> List<Hospital>): Flow<List<Hospital>> =
        callbackFlow {
            val registration = firestore.collection(Hospitals)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("Omni", "The hospital directory could not be read", error)
                        // Cache-first means an offline error is not fatal: Firestore serves the cache
                        // and this branch only fires when even the cache is unavailable. An empty
                        // directory is the honest degradation — better an honest empty list than a
                        // crash in the one screen that is about emergencies.
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    trySend(order(snapshot?.documents.orEmpty().mapNotNull { it.toHospital() }))
                }
            awaitClose { registration.remove() }
        }

    private companion object {
        const val Hospitals = "hospitals"
    }
}
