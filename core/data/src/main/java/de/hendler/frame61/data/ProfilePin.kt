package de.hendler.frame61.data

import java.security.MessageDigest

/**
 * PIN handling.
 *
 * Only a SHA-256 hash of the PIN is ever stored, never the PIN itself. The salt
 * is per profile so two profiles with the same PIN do not look identical in the
 * database.
 */
object PinHasher {

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = (salt + ":" + pin).toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, salt: String, storedHash: String): Boolean =
        storedHash.isNotBlank() && hash(pin, salt) == storedHash
}

/** One profile row, mirroring the Room entity minus the id. */
data class ProfileDraft(
    val name: String,
    val salt: String = "",
    val pinHash: String = "",
    val allowedSources: String = "",
    val skinId: String = "frame61",
    val layoutId: String = "default"
) {
    val hasPin: Boolean get() = pinHash.isNotBlank()
}