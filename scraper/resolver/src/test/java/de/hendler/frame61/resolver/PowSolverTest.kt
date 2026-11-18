package de.hendler.frame61.resolver

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowSolverTest {

    private val solver = PowSolver()

    /**
     * Real Filemoon challenge (nonce/difficulty) from
     * POST /api/videos/89crzins1ufc/captcha. The returned solution "3643" was
     * accepted by POST /captcha/verify with status "ok".
     */
    @Test
    fun `filemoon vector from a real accepted challenge`() {
        val nonce = "71354556cbb11e72c76b774e16b2adec"
        val solution = solver.solveFilemoon(nonce, difficulty = 16)
        assertEquals("3643", solution)
        val digest = solver.filemoonHash("$nonce:3643".toByteArray(Charsets.UTF_8))
        assertTrue("solution must have at least 16 leading zero bits", solver.leadingZeroBits(digest) >= 16)
    }

    @Test
    fun `filemoon solver is deterministic`() {
        val nonce = "71354556cbb11e72c76b774e16b2adec"
        assertEquals(
            solver.solveFilemoon(nonce, 12),
            solver.solveFilemoon(nonce, 12)
        )
    }

    @Test
    fun `sha256 variant matches the configured prefix`() {
        val input = "frame61-pow"
        val solution = solver.solveSha256(input, targetPrefix = "00", maxIterations = 10_000)
        assertNotNull("no sha256 solution found", solution)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$input:$solution".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertTrue(digest.startsWith("00"))
    }

    @Test
    fun `zero difficulty returns the first counter`() {
        assertEquals("0", solver.solveFilemoon("nonce", difficulty = 0))
    }
}
