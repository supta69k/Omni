package com.example.omni.data.repo

import com.example.omni.data.model.SleepQuality
import com.example.omni.data.model.SleepRecord
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed sleep tracking — the real implementation.
 *
 * Collection: `users/{uid}/sleep/{yyyy-MM-dd}`. One document per sleep date (adjustment #8), where the date
 * is the morning the user woke up, matching how Home's sleep card already attributes sleep to "today".
 *
 * Every write also synchronizes `days/{date}.sleepHours` (adjustment #2) so the two sources never drift:
 * this collection holds the detailed authoritative record, and `DailyMetrics.sleepHours` is the derived
 * summary that Home reads.
 */
class FirestoreSleepRepository(
    private val firestore: FirebaseFirestore,
    private val metricsRepository: MetricsRepository,
) : SleepRepository {

    override fun observeDay(uid: String, date: String): Flow<SleepRecord?> = callbackFlow {
        val listener = sleepDocument(uid, date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val record = snapshot?.takeIf { it.exists() }?.toSleepRecord()
                trySend(record)
            }
        awaitClose { listener.remove() }
    }

    override fun observeMonth(uid: String, month: String): Flow<Map<String, SleepRecord>> = callbackFlow {
        // Query all documents whose id starts with the month prefix, using the FieldPath.documentId()
        // range trick that MetricsRepository already uses (adjustment #4: reuse the existing date system).
        // "$month-01" to "$month-32" captures every day in the month regardless of how many days it has.
        val listener = firestore.collection(Users).document(uid).collection(Sleep)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), "$month-01")
            .whereLessThan(FieldPath.documentId(), "$month-32")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toSleepRecord() }
                    ?.associateBy { it.date }
                    ?: emptyMap()
                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveSleep(uid: String, record: SleepRecord) {
        // Write the detailed record to sleep/{date}
        val data = mapOf(
            "date" to record.date,
            "bedtime" to record.bedtime,
            "wakeTime" to record.wakeTime,
            "durationMinutes" to record.durationMinutes.coerceAtLeast(0),
            "quality" to record.quality.name,
            "notes" to record.notes,  // null is fine; Firestore omits absent fields
            "createdAt" to (record.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        sleepDocument(uid, record.date).set(data, SetOptions.merge()).await()

        // Synchronize DailyMetrics.sleepHours so Home's card stays correct (adjustment #2).
        // Convert minutes back to hours (a Float) to match the existing storage format.
        val hours = record.durationMinutes / 60f
        metricsRepository.setSleepHours(uid, record.date, hours)
    }

    override suspend fun deleteSleep(uid: String, date: String) {
        // Delete the detailed record
        sleepDocument(uid, date).delete().await()

        // Clear DailyMetrics.sleepHours so Home shows 0 (adjustment #2)
        metricsRepository.setSleepHours(uid, date, 0f)
    }

    private fun sleepDocument(uid: String, date: String) =
        firestore.collection(Users).document(uid).collection(Sleep).document(date)

    private companion object {
        const val Users = "users"
        const val Sleep = "sleep"
    }
}

/**
 * Maps a Firestore document to [SleepRecord], or `null` if required fields are missing.
 *
 * Quality is parsed from its string name back to the enum. If the stored value is unrecognized (a manual
 * Firestore edit wrote garbage, or a future build adds a sixth quality level), it defaults to FAIR rather
 * than crashing — a conservative middle ground that keeps the record renderable.
 */
private fun com.google.firebase.firestore.DocumentSnapshot.toSleepRecord(): SleepRecord? {
    val date = id
    val bedtime = getString("bedtime") ?: return null
    val wakeTime = getString("wakeTime") ?: return null
    val durationMinutes = getLong("durationMinutes")?.toInt() ?: return null
    val qualityString = getString("quality") ?: return null
    val quality = try {
        SleepQuality.valueOf(qualityString)
    } catch (e: IllegalArgumentException) {
        SleepQuality.FAIR  // Unrecognized value: default to middle rather than crash
    }
    val notes = getString("notes")  // null is fine

    return SleepRecord(
        date = date,
        bedtime = bedtime,
        wakeTime = wakeTime,
        durationMinutes = durationMinutes,
        quality = quality,
        notes = notes,
        createdAt = get("createdAt"),
        updatedAt = get("updatedAt"),
    )
}
