package com.example.omni.domain

import java.util.Locale
import kotlin.math.roundToInt

/*
 * The rules behind the three "Daily updates & Recomindation" cards — BACKEND_PLAN Phase 5.
 *
 * Deliberately pure Kotlin: no Android, no Compose, no Firebase. Everything here is a function of its
 * arguments, so all of it can be exercised by a JVM unit test without a device, and the same rule can be
 * reused by the nutrition screen's rings in Phase 6 rather than being reinvented there.
 *
 * Every function is total. Firestore hands back whatever the console typed, so a goal of 0, a negative
 * reading and a `NaN` all have to mean something rather than crash a card — they all read as "no
 * progress", which is the only honest answer when the numbers make no sense.
 */

/**
 * How a metric is doing against its goal — the coloured dot beside each card's footnote.
 *
 * Three states because the design draws three. Naming them after the words on the chips ("Good" / "Fair"
 * / "Action Needed") keeps the mapping in [statusFor] readable and stops a fourth colour creeping in.
 */
enum class Status { Good, Fair, ActionNeeded }

/**
 * One rule for all three cards (BACKEND_PLAN Phase 5): four fifths of the way there is good, two fifths
 * is fair, below that needs attention.
 *
 * **This flips two of the design's own chips, on purpose.** Figma pairs 18/31g fibre (58%) with "Good"
 * and 6.5/8h sleep (81%) with "Fair" — the two labels are swapped relative to any consistent threshold,
 * and no single rule can reproduce both. Adopting the rule makes the fibre card read "Fair" and the sleep
 * card "Good"; the CPR card's 0% → "Action Needed" is unchanged. Recorded here and in the composables'
 * KDoc as `UI_ARCHITECTURE.md` §6 rule 9 requires.
 */
fun statusFor(progress: Float): Status = when {
    progress >= GoodFrom -> Status.Good
    progress >= FairFrom -> Status.Fair
    else -> Status.ActionNeeded
}

/**
 * A reading and its goal as a fraction in `0f..1f`.
 *
 * Capped at 1: past the goal there is nothing left to show, and neither the bar nor the status has a
 * state beyond "met". A non-positive or non-finite goal reads as 0 rather than dividing.
 */
fun progressOf(value: Float, goal: Float): Float {
    if (goal <= 0f || !goal.isFinite() || !value.isFinite() || value <= 0f) return 0f
    return (value / goal).coerceAtMost(1f)
}

/**
 * How much of [goal] is still owed, rounded to the tenth that [formatAmount] will print.
 *
 * The rounding lives here rather than at the call site so that "0.04g away" comes back as exactly 0 and
 * the footnote can say the goal is met instead of claiming "0g away from your Goal".
 */
fun remainingTo(value: Float, goal: Float): Float {
    if (!goal.isFinite() || !value.isFinite()) return 0f
    val owed = (goal - value).coerceAtLeast(0f)
    return (owed * 10f).roundToInt() / 10f
}

/**
 * The three weights of an update card's progress bar, plus where its axis marker belongs.
 *
 * @property lead the blank run before the gradient segment.
 * @property fill the segment itself — constant, see [progressBarOf].
 * @property tail the blank run after it.
 * @property markerFraction where the marker triangle's *centre* goes, as a fraction of the track's width.
 */
data class ProgressBar(
    val lead: Float,
    val fill: Float,
    val tail: Float,
    val markerFraction: Float,
)

/**
 * BACKEND_PLAN §3.3's formula: a fixed-width gradient puck sliding along a fixed track.
 *
 * The design's three bars were hand-placed, but their `fill` is identical in all three and the CPR card's
 * tail is exactly `Track − Puck`, which gives the whole thing away: the segment never changes width, it
 * only moves. So there is one number behind a bar — [progress] — and this reproduces the CPR card exactly
 * and the fibre card to within 3%.
 *
 * The marker rides the puck's **centre**, which is what the design actually draws: Figma puts the fibre
 * triangle's centre at 188 of its 346-wide axis row, and the puck's centre at 191.8 of the 354 track
 * scales to 187.5 there — a match to within a dp. The puck's *end* would be at 228, forty dp away, so the
 * older comment claiming the triangle pointed at the end of the segment was simply wrong.
 *
 * Because the marker tracks the centre rather than the end it can never reach either edge (10.3% at
 * `progress = 0`, 89.7% at 1), so no clamping is needed to keep the triangle on the card.
 */
fun progressBarOf(progress: Float): ProgressBar {
    val travel = Track - Puck
    val lead = if (progress.isFinite()) progress.coerceIn(0f, 1f) * travel else 0f
    return ProgressBar(
        lead = lead,
        fill = Puck,
        tail = travel - lead,
        markerFraction = (lead + Puck / 2f) / Track,
    )
}

/**
 * A metric as the cards print it: `18`, `6.5`, `1.5` — one decimal at most, and never a bare `.0`.
 *
 * [Locale.US] rather than the default, because these strings go into boxes measured for a full stop. A
 * phone set to a comma-decimal locale would otherwise render "6,5 h" into a slot sized for "6.5 h", and
 * the design's own axis labels ("31 g", "8/h") would stop matching the value above them.
 */
fun formatAmount(value: Float): String {
    if (!value.isFinite()) return "0"
    val tenths = (value * 10f).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(Locale.US, "%.1f", tenths / 10f)
}

/** The two thresholds, named so the one place they are tuned is obvious. */
private const val GoodFrom = 0.80f
private const val FairFrom = 0.40f

/** The progress bar's full width in the design, and the gradient segment that slides along it (§3.3). */
private const val Track = 354f
private const val Puck = 72.6416f
