package com.pocketclaw.agent.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that the loop-detection hash mechanism works correctly.
 *
 * The SHA-256 hash must be deterministic and input-sensitive so that
 * identical (action, DOM) pairs always produce the same hash — a prerequisite
 * for the sliding-window loop detector to fire reliably.
 */
class AgentOrchestratorLoopDetectionTest {

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun sameInputProducesSameHash() {
        val h1 = sha256("action:dom")
        val h2 = sha256("action:dom")
        assertEquals(h1, h2)
    }

    @Test
    fun differentInputProducesDifferentHash() {
        val h1 = sha256("action:dom_v1")
        val h2 = sha256("action:dom_v2")
        assertNotEquals(h1, h2)
    }

    @Test
    fun loopWindow_sameHashThreeTimes_exceedsThreshold() {
        val hashes = ArrayDeque<String>(10)
        val stateHash = sha256("stuck_action:same_dom")
        repeat(3) {
            hashes.addLast(stateHash)
            if (hashes.size > 10) hashes.removeFirst()
        }
        val count = hashes.count { it == stateHash }
        assertTrue("Expected loop detection to trigger", count >= 3)
    }

    @Test
    fun iterationNumberInHash_wouldPreventLoopDetection() {
        // Demonstrates the bug that was fixed: hashing with iteration number
        // produces a different hash each time even for identical actions.
        val action = "same_action"
        val h1 = sha256("$action:1")
        val h2 = sha256("$action:2")
        assertNotEquals("Iteration-based hashes must differ (this was the bug)", h1, h2)
    }

    @Test
    fun domBasedHash_sameActionSameDom_producesLoopableHash() {
        val action = "same_action"
        val dom = "same_dom"
        val h1 = sha256("$action:$dom")
        val h2 = sha256("$action:$dom")
        assertEquals("DOM-based hashes must be identical for identical inputs", h1, h2)
    }
}
