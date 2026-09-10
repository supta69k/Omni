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
 * [waterGoal], [stepsGoal], [sleepGoal], [fiberGoal] and [calorieGoal] are the `goals` map (BACKEND_PLAN
 * §7). Goals belong to the *account*, not to a day: changing the target must not rewrite history, so they
 * are stored here and the per-day document keeps only what actually happened. The two hour/gram goals are
 * `Float` because half an hour of sleep is a normal target and the cards print a decimal.
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
    val calorieGoal: Int = DefaultCalorieGoal,
    /**
     * The `prefs` map (BACKEND_PLAN §7). Both default to **on**, which is what the settings switches
     * have always drawn and what a document written before Phase 10 has no opinion about.
     */
    val pushNotifications: Boolean = true,
    val offlineCache: Boolean = true,
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

/**
 * 2,000 kcal — BACKEND_PLAN §7's own default, and the number the gauge's sentence is measured against.
 *
 * Clamped when read (see `FirestoreUserRepository`): the percent is printed inside a fixed box beside
 * the arc, so a six-digit goal would push "999%" into the sentence's line.
 */
const val DefaultCalorieGoal = 2_000

enum class UserRole { USER, PROFESSIONAL, ADMIN }

/**
 * The five targets, together — what the goals page edits and writes as one `goals` map.
 *
 * A value object rather than five parameters on a repository method, because they are always read
 * together, always written together, and a call site that passed steps where calories belong would
 * compile perfectly well as five bare numbers.
 *
 * The field names match the Firestore keys exactly (BACKEND_PLAN §7), which is what lets the write be
 * a literal map and the read stay the hand-written mapper it already is.
 */
data class HealthGoals(
    val water: Int = DefaultWaterGoal,
    val steps: Int = DefaultStepsGoal,
    val sleepHours: Float = DefaultSleepGoal,
    val fiberGrams: Float = DefaultFiberGoal,
    val calories: Int = DefaultCalorieGoal,
) {
    /**
     * Every value forced back into the range the cards can draw.
     *
     * Applied on the way *out* as well as on the way in. The editor's steppers cannot leave the range
     * on their own, but a goal written by an older build, a console edit or a future screen can, and
     * this is the one place that decides what a legal target is.
     */
    fun clamped(): HealthGoals = HealthGoals(
        water = water.coerceIn(MinWaterGoal, MaxWaterGoal),
        steps = steps.coerceIn(MinStepsGoal, MaxStepsGoal),
        sleepHours = if (sleepHours.isFinite()) sleepHours.coerceIn(MinSleepGoal, MaxSleepGoal) else DefaultSleepGoal,
        fiberGrams = if (fiberGrams.isFinite()) fiberGrams.coerceIn(MinFiberGoal, MaxFiberGoal) else DefaultFiberGoal,
        calories = calories.coerceIn(MinCalorieGoal, MaxCalorieGoal),
    )
}

/** The account's five targets as one object. */
val User.goals: HealthGoals
    get() = HealthGoals(
        water = waterGoal,
        steps = stepsGoal,
        sleepHours = sleepGoal,
        fiberGrams = fiberGoal,
        calories = calorieGoal,
    )

/**
 * What the goals page will let a target be, and what a stored one is clamped to.
 *
 * These are display limits before they are health advice. Each ceiling is the point past which some
 * card stops being able to draw the number honestly:
 *
 *  - **Water** — the band holds 8 icons and the count is printed as `n/goal glasses` on one line. At 20
 *    each icon stands for 2.5 glasses, which still reads; the hint's 4dp of slack is what stops it
 *    there (see `HomeBento.waterHint`).
 *  - **Steps** — the hint prints a percentage capped at three digits, so a goal a walker cannot reach
 *    would freeze it at "999%". 50,000 is roughly 40km.
 *  - **Sleep** — 12h was already `FirestoreUserRepository`'s ceiling, and it keeps the axis label two
 *    characters wide. The floor is 4: below that it is not a goal, it is a symptom.
 *  - **Fibre** — the card's footnote wraps to three lines and a three-digit remainder pushes a word
 *    onto a fourth.
 *  - **Calories** — the gauge prints its percent in a fixed box beside the arc.
 *
 * The steps are what the editor's `−`/`+` move by, chosen so a realistic target is a handful of taps
 * from the default rather than fifty.
 */
const val MinWaterGoal = 1
const val MaxWaterGoal = 20
const val WaterGoalStep = 1

const val MinStepsGoal = 1_000
const val MaxStepsGoal = 50_000
const val StepsGoalStep = 500

const val MinSleepGoal = 4f
const val MaxSleepGoal = 12f
const val SleepGoalStep = 0.5f

const val MinFiberGoal = 5f
const val MaxFiberGoal = 99f
const val FiberGoalStep = 1f

const val MinCalorieGoal = 800
const val MaxCalorieGoal = 6_000
const val CalorieGoalStep = 50

enum class Profession { DOCTOR, NUTRITIONIST }

/** Where the app is in the auth lifecycle — what [MainActivity]'s launch gate reads. */
enum class AuthState {
    /** Firebase has not answered yet; the app shows nothing rather than flashing the wrong page. */
    LOADING,
    UNAUTHENTICATED,
    AUTHENTICATED,
}
