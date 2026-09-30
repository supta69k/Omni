package com.example.omni.data.model

import java.time.LocalTime

/**
 * One night's sleep — the authoritative record in `users/{uid}/sleep/{yyyy-MM-dd}`.
 *
 * This is the single source of truth for detailed sleep tracking (adjustment #2). The date portion of the
 * document id is the *sleep date* — the day the sleep is attributed to, which is the morning you woke up,
 * not the night before. That keeps sleep beside the same day's steps and water rather than split across
 * midnight, and it is what Home's "6.5/8h" card already means.
 *
 * [bedtime] and [wakeTime] are stored as `HH:mm` strings (e.g. "23:00", "07:30") rather than full
 * timestamps, because the year/month/day is already the document id and repeating it would be redundant.
 * A string also sorts correctly, validates easily, and avoids time-zone complexity — these are clock times
 * the user stated, not instants to reconcile across devices.
 *
 * [durationMinutes] is computed from bedtime and wake time and stored alongside them. It is redundant by
 * design: storing it means the history and insights can read the duration without re-parsing every time
 * string, and a mismatch between duration and times (from a manual Firestore edit or a future bug fix)
 * becomes visible rather than silent. The times are the source; the duration is the cache.
 *
 * [quality] is restricted to four stable enum values rather than a free-text field or a numeric scale,
 * because insights and averages must group responses reliably. A user who types "ok" one day and "fine"
 * the next has given two answers the app cannot compare; `FAIR` twice is comparable.
 *
 * @property date the document id, and the sleep date — `yyyy-MM-dd` format, the morning the user woke up.
 * @property bedtime when the user went to bed, as `HH:mm` (24-hour). May be late evening (22:00) or early
 *   morning (01:30) — both are valid, and the duration calculation handles overnight correctly.
 * @property wakeTime when the user woke up, as `HH:mm` (24-hour). Expected to be morning, but the model
 *   does not enforce that — a night-shift worker's "sleep" might be 09:00 to 17:00.
 * @property durationMinutes how long the user slept, in minutes. Computed from [bedtime] and [wakeTime] via
 *   [calculateSleepDuration] and stored redundantly for performance. Must never be negative.
 * @property quality subjective sleep quality, restricted to four stable enum values.
 * @property notes optional free text. Blank is stored as `null` to avoid empty strings in Firestore.
 * @property createdAt when this record was first created (Firestore server timestamp).
 * @property updatedAt when this record was last modified (Firestore server timestamp).
 */
data class SleepRecord(
    val date: String,
    val bedtime: String,
    val wakeTime: String,
    val durationMinutes: Int,
    val quality: SleepQuality,
    val notes: String? = null,
    val createdAt: Any? = null,  // Firestore Timestamp or FieldValue.serverTimestamp()
    val updatedAt: Any? = null,
)

/**
 * Subjective sleep quality — four stable options that can be grouped and compared.
 *
 * An enum rather than a scale or free text, because insights ("your average quality when you sleep 7+ hours")
 * and filtering ("show only GOOD nights") require responses that match exactly. The four levels map to:
 *  - POOR: restless, woke up tired, < 4 subjective stars
 *  - FAIR: acceptable but not great, 4–6 stars
 *  - GOOD: solid, refreshed, 7–8 stars
 *  - EXCELLENT: best possible, 9–10 stars
 *
 * Stored as the enum name string in Firestore ("POOR", "FAIR", etc.), never as an ordinal or a localized
 * label, so the data survives i18n changes and future reorderings.
 */
enum class SleepQuality {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
}

/**
 * Calculates sleep duration in minutes from bedtime to wake time, correctly handling overnight sleep.
 *
 * Examples (adjustment #9's worked cases):
 *  - `"23:00"` to `"07:00"` → 480 minutes (8 hours)
 *  - `"23:30"` to `"00:30"` → 60 minutes (1 hour)
 *  - `"01:00"` to `"08:00"` → 420 minutes (7 hours)
 *  - `"22:00"` to `"21:00"` → 1380 minutes (23 hours) — unusual but valid for a day-long rest
 *
 * The function assumes wake time is *after* bedtime, possibly on the next day. If wake time is earlier on
 * the clock (e.g. bedtime 22:00, wake 21:00), it assumes a full day has passed and adds 24 hours. This
 * handles the common case (most people sleep at night and wake in the morning) and the edge case (a rare
 * 23-hour sleep) without breaking.
 *
 * Returns 0 if either time is malformed or if the inputs are identical (same time = zero duration).
 *
 * @param bedtime `HH:mm` string, 24-hour format
 * @param wakeTime `HH:mm` string, 24-hour format
 * @return duration in minutes, always >= 0
 */
fun calculateSleepDuration(bedtime: String, wakeTime: String): Int {
    return try {
        val bed = LocalTime.parse(bedtime)
        val wake = LocalTime.parse(wakeTime)

        // Minutes from midnight to each time
        val bedMinutes = bed.hour * 60 + bed.minute
        val wakeMinutes = wake.hour * 60 + wake.minute

        // If wake is later on the clock, it's same-day (or we assume next day for overnight)
        // If wake is earlier on the clock, assume it's the next day
        val duration = if (wakeMinutes >= bedMinutes) {
            wakeMinutes - bedMinutes
        } else {
            // Add 24 hours to wake time: (24*60 - bedMinutes) + wakeMinutes
            (24 * 60) - bedMinutes + wakeMinutes
        }

        duration.coerceAtLeast(0)
    } catch (e: Exception) {
        // Malformed time string, or any other parse failure
        0
    }
}

/**
 * Formats a duration in minutes as a human-readable string.
 *
 * Examples:
 *  - 480 → "8h 0m"
 *  - 90 → "1h 30m"
 *  - 45 → "0h 45m"
 *  - 0 → "0h 0m"
 *
 * Always shows both hours and minutes for consistency in the UI, even when one is zero.
 */
fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "${hours}h ${mins}m"
}

/**
 * The account's sleep goal in minutes (adjustment #1 says it must follow the existing goal architecture).
 *
 * [User.sleepGoal] is stored as hours (a `Float`), so this converts the default to minutes for duration
 * comparisons and progress calculations. 8 hours = 480 minutes.
 */
const val DefaultSleepGoalMinutes = 480
