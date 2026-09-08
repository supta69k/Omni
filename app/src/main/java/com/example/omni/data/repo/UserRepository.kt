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

    /** Merges [fields] into the document; absent keys are left alone. */
    suspend fun updateProfile(uid: String, fields: Map<String, Any?>)
}
