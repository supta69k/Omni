package com.example.omni.data.repo

import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.data.model.Profession
import com.example.omni.data.model.User
import com.example.omni.data.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [UserRepository] over Firestore's `users` collection.
 *
 * The mapper is written by hand rather than with `toObject<User>()`: `role` and `profession` are
 * enums stored as strings, and a document written by an older build (or an admin typing into the
 * console) can hold a value no enum constant matches. `toObject` throws on that and takes the whole
 * screen down; here an unrecognised value falls back to the safe default.
 */
class FirestoreUserRepository(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    override fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val registration = firestore.collection(Users).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // A rules rejection or a dead project is not something the UI can retry its way
                    // out of, so it closes the flow instead of emitting nulls forever.
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUser(uid))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) {
        firestore.collection(Users).document(uid)
            .set(fields + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    private companion object {
        const val Users = "users"

        /** A sleep goal beyond half a day is not a goal, and 24 keeps the axis label two characters wide. */
        const val MaxSleepGoal = 12f

        /** Grams, matching the day value's own clamp in [FirestoreMetricsRepository]. */
        const val MaxFiberGoal = 999f

        fun DocumentSnapshot.toUser(uid: String): User? {
            if (!exists()) return null
            return User(
                uid = uid,
                name = getString("name").orEmpty(),
                email = getString("email").orEmpty(),
                role = enumOrNull<UserRole>(getString("role")) ?: UserRole.USER,
                profession = enumOrNull<Profession>(getString("profession")),
                verified = getBoolean("verified") ?: false,
                photoUrl = getString("photoUrl"),
                unreadMessages = unreadCount("messages"),
                unreadNotifications = unreadCount("notifications"),
                waterGoal = goal("water", DefaultWaterGoal),
                stepsGoal = goal("steps", DefaultStepsGoal),
                sleepGoal = goal("sleepHours", DefaultSleepGoal, max = MaxSleepGoal),
                fiberGoal = goal("fiberGrams", DefaultFiberGoal, max = MaxFiberGoal),
            )
        }

        /**
         * One key out of the `goals` map, or [fallback] when the map, the key or its type is missing.
         *
         * A goal of 0 would make the water card divide by zero and its progress bar meaningless, so a
         * non-positive stored value is treated as absent rather than honoured.
         */
        fun DocumentSnapshot.goal(key: String, fallback: Int): Int =
            (get("goals.$key") as? Number)?.toInt()?.takeIf { it > 0 } ?: fallback

        /**
         * The same, for the two goals the update cards print with a decimal.
         *
         * [max] is here and not on the integer overload because these two are rendered *inside* the
         * footnote sentence as well as on the axis, so an absurd goal widens a line rather than just a
         * number (`UI_ARCHITECTURE.md` §6 rule 8). Non-finite is treated as absent, as everywhere else a
         * console-typed value reaches arithmetic.
         */
        fun DocumentSnapshot.goal(key: String, fallback: Float, max: Float): Float =
            (get("goals.$key") as? Number)
                ?.toFloat()
                ?.takeIf { it > 0f && it.isFinite() }
                ?.coerceAtMost(max)
                ?: fallback

        /**
         * One key out of the `unread` map. Read through [DocumentSnapshot.get] with a dotted path so a
         * missing map is a missing field rather than a null-pointer, and coerced through [Number]
         * because Firestore hands integers back as `Long` and a Cloud Function's `increment` could
         * leave a `Double` behind.
         */
        fun DocumentSnapshot.unreadCount(key: String): Int =
            (get("unread.$key") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        /** `null` for a missing *or* unrecognised name, so a bad string can never crash a screen. */
        inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
            name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
    }
}
