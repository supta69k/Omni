package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot

/**
 * One person to reach when the SOS button is pressed — `users/{uid}/emergencyContacts/{contactId}`
 * (BACKEND_PLAN §7).
 *
 * A subcollection rather than an array on the profile, which is what the plan's own data model says:
 * contacts are added and removed one at a time, and a subcollection makes each of those a write of one
 * document instead of a read-modify-write of the whole profile.
 *
 * [relation] is free text, not an enum — "Mum", "Room-mate", "Dr Rahman" are all the right answer, and
 * a picker would only make the user translate what they already know into someone else's categories.
 */
data class EmergencyContact(
    val id: String,
    val name: String,
    val phone: String,
    val relation: String,
)

fun DocumentSnapshot.toEmergencyContact(): EmergencyContact? {
    if (!exists()) return null
    val phone = getString("phone")?.trim().orEmpty()
    // A contact with no number cannot be dialled or texted, so it is not a contact. Dropping it here
    // keeps every screen below from having to render a row that does nothing.
    if (phone.isEmpty()) return null
    return EmergencyContact(
        id = id,
        name = getString("name")?.trim().orEmpty().ifBlank { phone },
        phone = phone,
        relation = getString("relation")?.trim().orEmpty(),
    )
}

/** How many contacts one account may hold. */
const val MaxEmergencyContacts = 5

/** Length caps, applied where the text is entered — see [MaxEmergencyContacts] for the count cap. */
const val MaxContactNameLength = 40
const val MaxContactPhoneLength = 20
const val MaxContactRelationLength = 24
