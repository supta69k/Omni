package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * One story — a document in `stories/{storyId}` (the §7 schema's social surface, finally used).
 *
 * A story is a full-screen image with optional text, live for 24 hours — the Facebook/Instagram
 * shape the product brief asked for, not a post variant: no likes, no comments, no feed card. It is
 * the author's day, not their thoughts.
 *
 * [expiresAt] is a millisecond timestamp; a story whose expiry has passed is filtered out on read
 * (see [isLive]) *and* left to a TTL policy or the Phase 12 functions to delete — the client never
 * deletes another user's document, and its own expired ones are invisible anyway.
 */
data class Story(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String? = null,
    val authorVerified: Boolean = false,
    val imageUrl: String,
    val caption: String? = null,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
) {
    /** Whether the story is still inside its 24-hour window at [now]. */
    fun isLive(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt == 0L || now < expiresAt
}

/** The Firestore map for a story document on create. */
fun Story.toFirestoreMap(): Map<String, Any?> = mapOf(
    "authorId" to authorId,
    "authorName" to authorName,
    "authorPhotoUrl" to authorPhotoUrl,
    "authorVerified" to authorVerified,
    "imageUrl" to imageUrl,
    "caption" to caption,
    "createdAt" to FieldValue.serverTimestamp(),
    "expiresAt" to expiresAt,
)

fun DocumentSnapshot.toStory(): Story? {
    if (!exists()) return null
    return Story(
        id = id,
        authorId = getString("authorId").orEmpty(),
        authorName = getString("authorName").orEmpty().ifBlank { "Someone" },
        authorPhotoUrl = getString("authorPhotoUrl"),
        authorVerified = getBoolean("authorVerified") ?: false,
        imageUrl = getString("imageUrl").orEmpty(),
        caption = getString("caption")?.takeIf { it.isNotBlank() },
        createdAt = millisOf("createdAt"),
        expiresAt = (get("expiresAt") as? Number)?.toLong() ?: 0L,
    )
}

/** How long a story lives, in milliseconds. */
const val StoryLifetimeMillis: Long = 24 * 60 * 60 * 1000L
