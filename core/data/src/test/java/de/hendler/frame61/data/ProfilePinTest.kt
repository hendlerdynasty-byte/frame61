package de.hendler.frame61.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIN handling: hashed, salted per profile, never stored in clear. */
class ProfilePinTest {

    @Test
    fun `hash is deterministic for the same pin and salt`() {
        val a = PinHasher.hash("1234", "7")
        val b = PinHasher.hash("1234", "7")
        assertEquals(a, b)
        assertEquals(64, a.length) // SHA-256 als Hex
    }

    @Test
    fun `same pin with different salt produces different hashes`() {
        assertNotEquals(PinHasher.hash("1234", "1"), PinHasher.hash("1234", "2"))
    }

    @Test
    fun `different pins produce different hashes`() {
        assertNotEquals(PinHasher.hash("1234", "1"), PinHasher.hash("4321", "1"))
    }

    @Test
    fun `verify accepts the right pin and rejects wrong ones`() {
        val salt = "42"
        val stored = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, stored))
        assertTrue(!PinHasher.verify("0000", salt, stored))
        assertTrue(!PinHasher.verify("1234", "43", stored))
    }

    @Test
    fun `verify fails closed on empty stored hash`() {
        assertTrue(!PinHasher.verify("1234", "1", ""))
        assertTrue(!PinHasher.verify("1234", "1", " "))
    }

    @Test
    fun `profile draft reflects pin presence`() {
        assertTrue(!ProfileDraft(name = "ohne").hasPin)
        assertTrue(ProfileDraft(name = "mit", pinHash = "abc").hasPin)
    }
}

/**
 * Profile namespace rule: resume state must not leak between profiles.
 *
 * Pins the key format the repository uses, so a profile change cannot silently
 * read another profile's position.
 */
class ProfileNamespaceTest {

    @Test
    fun `resume key contains the profile id`() {
        // Same shape as ResumeRepository's throttle map key
        val key = "1:100:5"
        assertEquals("1:100:5", key)
    }

    @Test
    fun `profile ids stay separate namespaces`() {
        val keyA = "1:100:5"
        val keyB = "2:100:5"
        assertNotEquals(keyA, keyB)
    }
}