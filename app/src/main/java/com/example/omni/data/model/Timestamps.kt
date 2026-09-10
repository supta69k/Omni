package com.example.omni.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * Milliseconds from a Firestore time field, whatever shape the field was written in.
 *
 * Every `createdAt` in this app is written with `FieldValue.serverTimestamp()`, and Firestore hands
 * that back as a [Timestamp] — never as a [Number]. Read as `get(field) as? Number` the cast could
 * only fail, so every document in the app reported `0L`, and the consequences were not cosmetic:
 *
 * - Posts and comments rendered "just now" forever, whatever their age.
 * - The feed's `loadMore` finds its paging cursor with `lastOrNull { it.createdAt > 0 }`. With every
 *   post at zero that found nothing and returned, so a second page was never fetched.
 *
 * [Number] is still accepted, because a document written from the console or an import script can
 * legitimately carry plain millis, and [Date] because that is what the driver returns for a written
 * `Date`. Reading all three is what makes the field's storage shape stop mattering.
 *
 * Returns `0L` for an absent field and for a server timestamp that has not resolved yet — a local
 * write appears in the snapshot before the server stamps it. Callers already read 0 as "no time
 * yet": [relativeTimeOf] renders it "just now", which for a write in flight is the truth.
 */
fun DocumentSnapshot.millisOf(field: String): Long = when (val value = get(field)) {
    is Timestamp -> value.toDate().time
    is Number -> value.toLong()
    is Date -> value.time
    else -> 0L
}
