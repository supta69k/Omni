package com.example.omni.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canonical direct-conversation identity.
 *
 * This is what makes "get or create the conversation between A and B" idempotent and
 * entry-point-independent: the id is a pure function of the two uids, sorted, so every door — Doctor
 * Directory, New Message, a profile's Message button — lands on the SAME document. There is no random
 * id anywhere, which is also why two near-simultaneous opens cannot create two conversations.
 */
class ConversationTest {

    @Test
    fun `the id is the same whichever order the pair is given`() {
        assertEquals(conversationIdOf("alice", "bob"), conversationIdOf("bob", "alice"))
    }

    @Test
    fun `the id is stable across repeated calls`() {
        val a = conversationIdOf("uid-A", "uid-Z")
        val b = conversationIdOf("uid-A", "uid-Z")
        val c = conversationIdOf("uid-Z", "uid-A")
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `the identity map sorts participants regardless of argument order`() {
        val forward = conversationIdentityMap("bob", "Bob", null, "alice", "Alice", null)
        val reverse = conversationIdentityMap("alice", "Alice", null, "bob", "Bob", null)
        assertEquals(listOf("alice", "bob"), forward["participants"])
        assertEquals(forward["participants"], reverse["participants"])
    }

    @Test
    fun `a normal open carries no consultation flag, a consultation open does`() {
        val normal = conversationIdentityMap("a", "A", null, "b", "B", null)
        val consult = conversationIdentityMap("a", "A", null, "b", "B", null, consultation = true)
        // Absent (not false) so a merge can never turn an existing consultation flag back off.
        assertFalse(normal.containsKey("consultation"))
        assertEquals(true, consult["consultation"])
    }

    @Test
    fun `a stored consultation flag or a legacy id both read as a consultation`() {
        val flagged = Conversation(id = "a_b", otherUid = "b", otherName = "B", consultation = true)
        val legacy = Conversation(id = "consultation_a_b", otherUid = "b", otherName = "B")
        val normal = Conversation(id = "a_b", otherUid = "b", otherName = "B")
        assertTrue(flagged.isConsultation)
        assertTrue(legacy.isConsultation)
        assertFalse(normal.isConsultation)
    }
}
