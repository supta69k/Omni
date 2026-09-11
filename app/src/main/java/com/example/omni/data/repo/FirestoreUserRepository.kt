package com.example.omni.data.repo

import com.example.omni.data.model.DefaultCalorieGoal
import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.MaxCalorieGoal
import com.example.omni.data.model.MaxFiberGoal
import com.example.omni.data.model.MaxSleepGoal
import com.example.omni.data.model.MaxStepsGoal
import com.example.omni.data.model.MaxWaterGoal
import com.example.omni.data.model.Profession
import com.example.omni.data.model.User
import com.example.omni.data.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
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

    override suspend fun getUser(uid: String): User? = try {
        firestore.collection(Users).document(uid).get().await().toUser(uid)
    } catch (cause: Exception) {
        // An author whose document cannot be read is not a reason for the feed to fail; the caller
        // keeps whatever identity the post itself was written with.
        android.util.Log.w("Omni", "The profile $uid could not be read", cause)
        null
    }

    override fun observeProfessionals(): Flow<List<User>> = callbackFlow {
        val registration = firestore.collection(Users)
            .whereEqualTo("verified", true)
            .limit(MaxProfessionals)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Degraded, not closed: an empty picker with an honest sentence under it beats a
                    // crash on the one screen a user opened to ask a doctor something.
                    android.util.Log.w("Omni", "The professional directory could not be read", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val professionals = snapshot?.documents
                    ?.mapNotNull { document -> document.toUser(document.id) }
                    // `verified` is the gate, but an account verified before it picked a profession
                    // would render a row with no discipline under the name. Both are required.
                    ?.filter { it.profession != null }
                    ?.sortedBy { it.name.lowercase() }
                    .orEmpty()
                trySend(professionals)
            }
        awaitClose { registration.remove() }
    }

    /**
     * The prefix range on `name`, run once per casing of [query] and merged.
     *
     * `orderBy("name").startAt(term).endAt(term + '')` is Firestore's whole vocabulary for
     * "begins with": `` is the last character in the Basic Multilingual Plane, so the range
     * covers every string that starts with `term`. It needs no composite index — a single-field index
     * on `name` is one Firestore maintains automatically — and it is capped, so the collection is
     * never pulled down to be filtered here.
     *
     * The range is byte-ordered, which is the same as saying it is case-sensitive: typing "ben" would
     * not reach "Ben Carter". So the same range is run for the casings a person actually types —
     * verbatim, all-lower, and Title Case per word — as a [Set], so "Ben" costs two queries and "ben"
     * costs two, never more than three. Each is a capped range query, and they run concurrently.
     *
     * Merged by uid (the casings overlap by design), sorted the way [observeProfessionals] sorts, and
     * cut to [limit] — so the caller gets [limit] *people*, not [limit] per variant.
     */
    override suspend fun searchByName(query: String, limit: Int): List<User> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()

        val variants = setOf(term, term.lowercase(), term.titleCased())
        val found = coroutineScope {
            variants
                .map { variant -> async { prefixMatch(variant, limit) } }
                .awaitAll()
                .flatten()
        }
        return found
            .associateBy { it.uid }
            .values
            .sortedBy { it.name.lowercase() }
            .take(limit)
    }

    /**
     * One casing's range query.
     *
     * Failures are **not** swallowed here, unlike everywhere else in this file: every variant runs
     * against the same collection under the same rule, so one failing means all of them would, and
     * an empty list would be indistinguishable from "nobody by that name". The search field has a
     * sentence to show instead, and it can only show it if the throw reaches it.
     */
    private suspend fun prefixMatch(term: String, limit: Int): List<User> =
        firestore.collection(Users)
            .orderBy("name")
            .startAt(term)
            .endAt(term + PrefixCeiling)
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.toUser(document.id) }

    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) {
        firestore.collection(Users).document(uid)
            .set(fields + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    /**
     * The `goals` map, written whole.
     *
     * A **nested** map, not five dotted keys: `set(…, merge())` treats `"goals.water"` as a field name
     * that happens to contain a dot, so dotted paths here would quietly create five junk top-level
     * fields beside the real map and nothing would ever read them back. Merge recurses into nested
     * maps, so this leaves anything else on the document — and any goal a later build adds — alone.
     */
    override suspend fun updateGoals(uid: String, goals: HealthGoals) {
        val safe = goals.clamped()
        updateProfile(
            uid = uid,
            fields = mapOf(
                "goals" to mapOf(
                    "water" to safe.water,
                    "steps" to safe.steps,
                    "sleepHours" to safe.sleepHours,
                    "fiberGrams" to safe.fiberGrams,
                    "calories" to safe.calories,
                ),
            ),
        )
    }

    private companion object {
        const val Users = "users"

        /**
         * The upper bound of a "begins with" range — the last code point in the Basic Multilingual
         * Plane, so `startAt(t) … endAt(t + this)` covers every string that starts with `t`.
         */
        const val PrefixCeiling = ''

        /** "ben carter" → "Ben Carter": the casing a display name is actually stored in. */
        fun String.titleCased(): String = split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }

        fun DocumentSnapshot.toUser(uid: String): User? {
            if (!exists()) return null
            return User(
                uid = uid,
                name = getString("name").orEmpty(),
                email = getString("email").orEmpty(),
                role = enumOrNull<UserRole>(getString("role")) ?: UserRole.USER,
                profession = enumOrNull<Profession>(getString("profession")),
                verified = getBoolean("verified") ?: false,
                // Blank is absent. A profile that once had a photo and had it cleared holds `""`,
                // and `""` is a perfectly good non-null String: every `photoUrl ?: initial` fallback
                // downstream would take the photo branch and hand Coil an empty model, which loads
                // nothing and draws nothing. That is the missing avatar in the feed.
                photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
                unreadMessages = unreadCount("messages"),
                unreadNotifications = unreadCount("notifications"),
                // The ceilings are the goals page's own, in `User.kt`, rather than three private
                // numbers here: one screen now sets these and one mapper reads them, and the two
                // disagreeing about what a legal target is would show a user a goal they could not
                // then reproduce with the stepper.
                waterGoal = goal("water", DefaultWaterGoal, max = MaxWaterGoal),
                stepsGoal = goal("steps", DefaultStepsGoal, max = MaxStepsGoal),
                sleepGoal = goal("sleepHours", DefaultSleepGoal, max = MaxSleepGoal),
                fiberGoal = goal("fiberGrams", DefaultFiberGoal, max = MaxFiberGoal),
                calorieGoal = goal("calories", DefaultCalorieGoal, max = MaxCalorieGoal),
                pushNotifications = pref("pushNotifications"),
                offlineCache = pref("offlineCache"),
            )
        }

        /**
         * One key out of the `prefs` map, defaulting to **true** when absent.
         *
         * Absent is the normal state for every account created before Phase 10, and the switches have
         * always been drawn on — so "never answered" and "said yes" have to render the same, or every
         * existing user would open Settings to find their notifications apparently turned off.
         */
        fun DocumentSnapshot.pref(key: String): Boolean = get("prefs.$key") as? Boolean ?: true

        /**
         * One key out of the `goals` map, or [fallback] when the map, the key or its type is missing.
         *
         * A goal of 0 would make the water card divide by zero and its progress bar meaningless, so a
         * non-positive stored value is treated as absent rather than honoured. [max] is the goals
         * page's own ceiling, so a value from an older build or the console is pulled back to
         * something the stepper could have produced.
         */
        fun DocumentSnapshot.goal(key: String, fallback: Int, max: Int): Int =
            (get("goals.$key") as? Number)?.toInt()?.takeIf { it > 0 }?.coerceAtMost(max) ?: fallback

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
