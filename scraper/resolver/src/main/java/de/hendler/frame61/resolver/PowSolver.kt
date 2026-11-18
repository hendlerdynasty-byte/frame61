package de.hendler.frame61.resolver

import java.security.MessageDigest

/**
 * Deterministic proof-of-work solver used by `pow` steps.
 *
 * `filemoon-v1` is a 1:1 port of Filemoon's `pow-*.js` solver: a SHA-256-like
 * 512-word hash over `nonce:counter`, accepted when the digest has at least
 * `difficulty` leading zero bits. `sha256` is the generic hashcash variant:
 * the hex digest of `input + separator + counter` must start with
 * [targetPrefix].
 *
 * The solver never sleeps and never performs I/O, so it is unit-testable and
 * deterministic. The Filemoon vector is a real challenge that was accepted by
 * `/api/videos/<code>/captcha/verify` with `status: ok`.
 */
class PowSolver {

    fun solve(step: HosterStep, input: String, difficulty: Int? = null): String? {
        val algorithm = step.algorithm ?: return null
        return when (algorithm) {
            "filemoon-v1" -> solveFilemoon(
                input = input,
                difficulty = difficulty ?: step.difficulty ?: return null,
                separator = step.separator,
                maxIterations = step.maxIterations
            )
            "sha256" -> solveSha256(
                input = input,
                targetPrefix = step.targetPrefix ?: return null,
                separator = step.separator,
                maxIterations = step.maxIterations
            )
            else -> null
        }
    }

    fun solveFilemoon(
        input: String,
        difficulty: Int,
        separator: String = ":",
        maxIterations: Int = 1_000_000
    ): String? {
        if (difficulty <= 0) return "0"
        for (counter in 0 until maxIterations) {
            val digest = filemoonHash("$input$separator$counter".toByteArray(Charsets.UTF_8))
            if (leadingZeroBits(digest) >= difficulty) return counter.toString()
        }
        return null
    }

    fun solveSha256(
        input: String,
        targetPrefix: String,
        separator: String = ":",
        maxIterations: Int = 1_000_000
    ): String? {
        val prefix = targetPrefix.lowercase()
        if (prefix.isEmpty()) return "0"
        val digest = MessageDigest.getInstance("SHA-256")
        for (counter in 0 until maxIterations) {
            val hash = digest.digest("$input$separator$counter".toByteArray(Charsets.UTF_8))
            val hex = hash.joinToString("") { "%02x".format(it) }
            if (hex.startsWith(prefix)) return counter.toString()
        }
        return null
    }

    /** Filemoon `gr(yr(...))`: custom 512-word SHA-256-like digest. */
    fun filemoonHash(data: ByteArray): IntArray {
        val e = intArrayOf(
            1779033703,
            3144134277.toInt(),
            1013904242,
            2773480762L.toInt()
        )
        for (byte in data) {
            e[0] += byte.toInt() and 0xFF
            e[0] = Integer.rotateLeft(e[0], 7)
            ye(e)
        }
        repeat(8) { ye(e) }
        val r = IntArray(512)
        for (i in 0 until 512) {
            ye(e)
            r[i] = e[0] xor e[2]
        }
        repeat(2) {
            for (s in 0 until 512) {
                val a = r[s] and 511
                var c = r[s] + r[a]
                c = Integer.rotateLeft(c, 13)
                c = c xor (r[(s + 1) and 511] * LR)
                r[s] = c
                e[0] = e[0] xor c
                ye(e)
            }
        }
        val n = IntArray(8)
        for (i in 0 until 8) {
            ye(e)
            var ss = e[0]
            for (c in 0 until 64) {
                val d = r[i * 64 + c]
                ss += d
                ss = Integer.rotateLeft(ss, 5)
                ss = ss xor (d * HR)
            }
            n[i] = ss xor e[2]
        }
        return n
    }

    fun leadingZeroBits(digest: IntArray): Int {
        var bits = 0
        for (word in digest) {
            if (word == 0) {
                bits += 32
                continue
            }
            return bits + Integer.numberOfLeadingZeros(word)
        }
        return bits
    }

    private fun ye(t: IntArray) {
        t[0] += t[1]
        t[3] = Integer.rotateLeft(t[3] xor t[0], 16)
        t[2] += t[3]
        t[1] = Integer.rotateLeft(t[1] xor t[2], 12)
        t[0] += t[1]
        t[3] = Integer.rotateLeft(t[3] xor t[0], 8)
        t[2] += t[3]
        t[1] = Integer.rotateLeft(t[1] xor t[2], 7)
    }

    private companion object {
        const val LR = 2654435761L.toInt()
        const val HR = 2246822519L.toInt()
    }
}
