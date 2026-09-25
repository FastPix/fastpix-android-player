package io.fastpix.media3

import io.fastpix.media3.prerender.DecoderBudgetLedger
import io.fastpix.media3.prerender.PrerenderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep pre-rendering from starving playback of a decoder. Overshooting the device's
 * limit is a fatal error on whichever player asks next — possibly the one being watched — so these
 * err on the side of pre-rendering less.
 */
class DecoderBudgetTest {

    @Test
    fun `grants stop short of the reserve`() {
        // Ceiling 4, reserve 1: three decoders may be in use in total.
        val ledger = DecoderBudgetLedger(initialCeiling = 4, reserve = 1)
        ledger.onDecoderOpened("playing")
        assertTrue(ledger.tryGrant("next"))
        assertTrue(ledger.tryGrant("previous"))
        assertFalse(ledger.tryGrant("next+1"))
    }

    @Test
    fun `a grant counts once, before and after its decoder opens`() {
        val ledger = DecoderBudgetLedger(initialCeiling = 3, reserve = 1)
        assertTrue(ledger.tryGrant("next"))
        assertEquals(1, ledger.inUse)
        ledger.onDecoderOpened("next")
        assertEquals(1, ledger.inUse)
    }

    @Test
    fun `asking again for a held grant succeeds without spending more`() {
        val ledger = DecoderBudgetLedger(initialCeiling = 2, reserve = 1)
        assertTrue(ledger.tryGrant("next"))
        assertTrue(ledger.tryGrant("next"))
        assertEquals(1, ledger.inUse)
    }

    @Test
    fun `releasing a grant frees room for another`() {
        val ledger = DecoderBudgetLedger(initialCeiling = 2, reserve = 1)
        assertTrue(ledger.tryGrant("a"))
        assertFalse(ledger.tryGrant("b"))
        ledger.release("a")
        assertTrue(ledger.tryGrant("b"))
    }

    @Test
    fun `playback that is not pre-rendering still counts against the budget`() {
        val ledger = DecoderBudgetLedger(initialCeiling = 3, reserve = 1)
        ledger.onDecoderOpened("feed player")
        ledger.onDecoderOpened("another screen's player")
        assertFalse(ledger.tryGrant("next"))
    }

    @Test
    fun `a failed open lowers the ceiling to what was running`() {
        val ledger = DecoderBudgetLedger(initialCeiling = 8, reserve = 1)
        ledger.onDecoderOpened("a")
        ledger.onDecoderOpened("b")
        ledger.onOpenFailed()
        assertEquals(2, ledger.ceiling)
        ledger.onDecoderClosed("b")
        // One open, ceiling 2, reserve 1: nothing left to pre-render with.
        assertFalse(ledger.tryGrant("next"))
    }

    @Test
    fun `the ceiling never rises after a failure`() {
        val ledger = DecoderBudgetLedger(initialCeiling = 8, reserve = 1)
        ledger.onDecoderOpened("a")
        ledger.onOpenFailed()
        ledger.onDecoderOpened("b")
        ledger.onDecoderOpened("c")
        ledger.onOpenFailed()
        assertEquals(1, ledger.ceiling)
    }

    @Test
    fun `pre-render range follows count and behind`() {
        val config = PrerenderConfig(count = 2, behind = 1)
        assertFalse(config.covers(0))
        assertTrue(config.covers(1))
        assertTrue(config.covers(2))
        assertFalse(config.covers(3))
        assertTrue(config.covers(-1))
        assertFalse(config.covers(-2))
    }

    @Test
    fun `entries are pre-rendered nearest first, ahead before behind`() {
        val config = PrerenderConfig(count = 2, behind = 1)
        assertEquals(listOf(6, 4, 7), config.windowIndices(currentIndex = 5, size = 20))
    }

    @Test
    fun `the pre-render window stays inside the playlist`() {
        val config = PrerenderConfig(count = 2, behind = 2)
        assertEquals(listOf(1, 2), config.windowIndices(currentIndex = 0, size = 3))
        assertEquals(listOf(1, 0), config.windowIndices(currentIndex = 2, size = 3))
        assertEquals(emptyList<Int>(), config.windowIndices(currentIndex = -1, size = 3))
    }

    @Test
    fun `pre-rendering is off by default`() {
        assertFalse(PrerenderConfig().enabled)
        assertTrue(PrerenderConfig(count = 1).enabled)
    }
}
