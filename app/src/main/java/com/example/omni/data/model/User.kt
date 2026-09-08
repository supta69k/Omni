package com.example.omni.data.model

/**
 * The signed-in account — the `users/{uid}` Firestore document, minus volatile bookkeeping.
 *
 * `role` and `verified` are the verification system's state: they are written by the review flow
 * (Phase 6), never by the user, which is what the security rules will enforce too.
 *
 * The two unread counts are the one piece of bookkeeping that *is* here, because the shared header
 * renders them. They live under a single `unread` map in Firestore and are maintained by the Cloud
 * Functions of Phases 10 and 11; until those land the map is simply absent, which reads as 0 — the
 * same thing the header shows today.
 *
 * [waterGoal], [stepsGoal], [sleepGoal] and [fiberGoal] are the `goals` map (BACKEND_PLAN §7). Goals belong
 * to the *account*, not to a day: changing the target must not rewrite history, so they are stored here
 * and the per-day document keeps only what actually happened. The two hour/gram goals are `Float` because
 * half an hour of sleep is a normal target and the cards print a decimal.
 */
data class User(
    val uid: String,
    val name: String,
    val email: String,
    val role: UserRole = UserRole.USER,
    val profession: Profession? = null,
    val verified: Boolean = false,
    val photoUrl: String? = null,
    val unreadMessages: Int = 0,
    val unreadNotifications: Int = 0,
    val waterGoal: Int = DefaultWaterGoal,
    val stepsGoal: Int = DefaultStepsGoal,
    val sleepGoal: Float = DefaultSleepGoal,
    val fiberGoal: Float = DefaultFiberGoal,
)

/**
 * Eight glasses — BACKEND_PLAN §3.1's resolution of the water card's contradiction.
 *
 * The card draws 8 glass icons but its copy claimed a goal of 12, so "one tap fills one glass" could
 * not be true. Eight 250ml glasses is 2L, a legitimate target, and it makes the tap-to-icon mapping
 * exactly 1:1 with no layout change and no rounding.
 */
const val DefaultWaterGoal = 8

/** The step card's own number, and the usual one. Unlike the water goal, the design is self-consistent. */
const val DefaultStepsGoal = 10_000

/** Eight hours, which is both the sleep card's own axis label and the usual advice. */
const val DefaultSleepGoal = 8f

/**
 * 31g — the fibre card's own axis label, and the adult daily recommendation it is drawn from.
 *
 * Also the upper bound of what the card can display: a goal is clamped to a plausible range when it is
 * read (see `FirestoreUserRepository`), because the footnote's box has a couple of dp of slack and a
 * four-digit remainder would push a word onto a fourth line.
 */
const val DefaultFiberGoal = 31f

enum class UserRole { USER, PROFESSIONAL, ADMIN }

enum class Profession { DOCTOR, NUTRITIONIST }

/** Where the app is in the auth lifecycle — what [MainActivity]'s launch gate reads. */
enum class AuthState {
    /** Firebase has not answered yet; the app shows nothing rather than flashing the wrong page. */
    LOADING,
    UNAUTHENTICATED,
    AUTHENTICATED,
}
