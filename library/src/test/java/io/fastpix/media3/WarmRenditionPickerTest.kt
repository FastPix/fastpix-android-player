package io.fastpix.media3

import io.fastpix.media3.cache.WarmRenditionPicker
import io.fastpix.media3.cache.WarmRenditionPicker.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pre-cacher must warm the rendition the player will start on, or every warmed byte is wasted.
 * The ladder below is the real "Multiple Default Audio" asset; on a Wi-Fi phone the player started
 * on 540p (1.8 Mbps) while the old fixed 1.2 Mbps target warmed 360p.
 */
class WarmRenditionPickerTest {

    private val ladder = listOf(
        Candidate(5_000_000, 1920, 1080),
        Candidate(4_000_000, 1920, 1080),
        Candidate(3_000_000, 1280, 720),
        Candidate(1_800_000, 960, 540),
        Candidate(600_000, 640, 360),
    )

    @Test
    fun picksHighestVariantWithinTarget() {
        // 0.7 × a ~2.6 Mbps initial estimate, as the player's AdaptiveTrackSelection allocates it.
        assertEquals(3, WarmRenditionPicker.pick(ladder, targetBps = 1_820_000))
        assertEquals(0, WarmRenditionPicker.pick(ladder, targetBps = 10_000_000))
    }

    @Test
    fun oldFixedTargetWarmedTheWrongRendition() {
        assertEquals(4, WarmRenditionPicker.pick(ladder, targetBps = 1_200_000))
    }

    @Test
    fun fallsBackToLowestWhenNothingFits() {
        assertEquals(4, WarmRenditionPicker.pick(ladder, targetBps = 100_000))
    }

    @Test
    fun viewportDropsVariantsLargerThanTheDisplay() {
        // A 720p portrait phone: 1080p variants are excluded, 720p (fills the screen) is kept.
        assertEquals(2, WarmRenditionPicker.pick(ladder, targetBps = 10_000_000, viewportWidth = 720, viewportHeight = 1280))
    }

    @Test
    fun viewportKeepsSmallestVariantThatFillsA1080Display() {
        assertEquals(0, WarmRenditionPicker.pick(ladder, targetBps = 10_000_000, viewportWidth = 1080, viewportHeight = 2400))
    }

    @Test
    fun unknownBitratesFallBackToFirstVariant() {
        val unknown = listOf(Candidate(-1, 1280, 720), Candidate(-1, 640, 360))
        assertEquals(0, WarmRenditionPicker.pick(unknown, targetBps = 1_000_000))
    }

    @Test
    fun emptyLadder() {
        assertEquals(-1, WarmRenditionPicker.pick(emptyList(), targetBps = 1_000_000))
    }
}
