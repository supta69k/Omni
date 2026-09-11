package com.example.omni.data.repo

import com.example.omni.data.model.HealthGoals
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
     * One profile, once — the read behind the feed's author cache.
     *
     * A listener per author would be one registration for every name on screen; a feed shows the
     * same twenty authors over and over, and their name and photo do not change while it is being
     * scrolled. Returns `null` for a deleted account or a rejected read, so a caller resolving an
     * author falls back to what the post itself carries rather than failing.
     */
    suspend fun getUser(uid: String): User?

    /**
     * Every verified professional, name-sorted — who a user is allowed to start a thread with
     * (BACKEND_PLAN §12: "users → verified professionals only").
     *
     * Sorted in Kotlin rather than by Firestore on purpose: ordering a `verified == true` query by name
     * needs a second composite index, and this list is capped at [MaxProfessionals] rows, which sorts in
     * microseconds. One index for the app is enough.
     */
    fun observeProfessionals(): Flow<List<User>>

    /**
     * People whose display name *starts with* [query], name-sorted — the feed's search field.
     *
     * One-shot rather than a live stream: a search is a question asked once per keystroke-pause, and
     * a listener per query would leave a registration behind for every word ever typed.
     *
     * ### What this can and cannot match, and why
     *
     * Firestore has no substring or full-text operator, so the only server-side name query available
     * is a range over the ordered `name` field: `startAt(q) … endAt(q + '')`. That is a **prefix**
     * match on the whole display name — "Ben" finds "Ben Carter", "Carter" does not. It is also
     * byte-ordered, so it is case-*sensitive*; implementations work around that by running the same
     * range for a few casings of [query] and merging, which is what makes typing "ben" find "Ben
     * Carter" without a schema change.
     *
     * The alternative is a normalised `nameLower` (or a token array) on every user document, which
     * would match mid-name and fold case properly — and would need a backfill of the accounts that
     * already exist, because a user missing the field would silently stop being findable. That is a
     * data migration, not a repository method, and the schema is deliberately left as it is.
     *
     * Capped at [limit] and always a range query: the collection is never downloaded to be filtered
     * in Kotlin. A blank [query] returns nothing rather than everyone.
     */
    suspend fun searchByName(query: String, limit: Int = UserSearchLimit): List<User>

    /** Merges [fields] into the document; absent keys are left alone. */
    suspend fun updateProfile(uid: String, fields: Map<String, Any?>)

    /**
     * Replaces the account's five daily targets.
     *
     * On the account rather than on a day, which is the whole reason goals live here: raising the water
     * target tomorrow must not rewrite what yesterday's card said. Implementations clamp — the caller is
     * a stepper today, but the range is a data rule, not a UI one.
     */
    suspend fun updateGoals(uid: String, goals: HealthGoals)
}

/**
 * The ceiling on the professional directory.
 *
 * A term project's database has a handful of verified accounts, and a picker is a list somebody reads
 * rather than pages through. If it ever fills up, the answer is a search field, not a bigger number.
 */
const val MaxProfessionals = 50L

/**
 * How many people one search shows.
 *
 * A phone-sized result panel holds a handful; more than this is a list nobody reads to the end of,
 * and every extra row is a document read. Narrowing the query is cheaper than paging the answer.
 */
const val UserSearchLimit = 15
