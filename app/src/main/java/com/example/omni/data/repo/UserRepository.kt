package com.example.omni.data.repo

import com.example.omni.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * The `users/{uid}` document — the profile every screen's name, avatar and role come from.
 *
 * Split from [AuthRepository] on purpose: auth owns the *session*, this owns the *profile*. The
 * profile changes while the session does not (editing a name, being verified), so the screens want a
 * live stream, not a one-shot read.
 */
interface UserRepository {
    /**
     * Emits the profile on every change — a snapshot listener, not a fetch. Emits `null` while the
     * document does not exist yet. Firestore serves the first value from its local cache when
     * offline, so a cold start with no network still shows the last known profile.
     */
    fun observeUser(uid: String): Flow<User?>

    /**
     * Every verified professional, name-sorted — who a user is allowed to start a thread with
     * (BACKEND_PLAN §12: "users → verified professionals only").
     *
     * Sorted in Kotlin rather than by Firestore on purpose: ordering a `verified == true` query by name
     * needs a second composite index, and this list is capped at [MaxProfessionals] rows, which sorts in
     * microseconds. One index for the app is enough.
     */
    fun observeProfessionals(): Flow<List<User>>

    /** Merges [fields] into the document; absent keys are left alone. */
    suspend fun updateProfile(uid: String, fields: Map<String, Any?>)
}

/**
 * The ceiling on the professional directory.
 *
 * A term project's database has a handful of verified accounts, and a picker is a list somebody reads
 * rather than pages through. If it ever fills up, the answer is a search field, not a bigger number.
 */
const val MaxProfessionals = 50L
