package io.fastpix.media3

import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.preload.PreloadStage
import io.fastpix.media3.preload.PreloadWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How [PreloadConfig] turns into work per playlist entry. Getting the window wrong in the generous
 * direction spends the user's data on entries they never reach; in the stingy direction, the
 * transition the developer asked to be instant is not.
 */
class PreloadWindowTest {

    @Test
    fun `the current entry is never preloaded`() {
        assertEquals(PreloadStage.NONE, PreloadWindow(3, 1).stageFor(5, currentIndex = 5))
    }

    @Test
    fun `nothing is preloaded before playback has a current entry`() {
        assertEquals(PreloadStage.NONE, PreloadWindow(3, 1).stageFor(1, currentIndex = -1))
    }

    @Test
    fun `adjacent entries are buffered ready to start`() {
        val window = PreloadWindow(ahead = 3, behind = 1)
        assertEquals(PreloadStage.RANGE_LOADED, window.stageFor(6, currentIndex = 5))
        assertEquals(PreloadStage.RANGE_LOADED, window.stageFor(4, currentIndex = 5))
    }

    @Test
    fun `entries further out only have tracks selected`() {
        val window = PreloadWindow(ahead = 3, behind = 0)
        assertEquals(PreloadStage.TRACKS_SELECTED, window.stageFor(7, currentIndex = 5))
        assertEquals(PreloadStage.TRACKS_SELECTED, window.stageFor(8, currentIndex = 5))
    }

    @Test
    fun `entries beyond the count are left alone`() {
        val window = PreloadWindow(ahead = 3, behind = 1)
        assertEquals(PreloadStage.NONE, window.stageFor(9, currentIndex = 5))
        assertEquals(PreloadStage.NONE, window.stageFor(3, currentIndex = 5))
    }

    @Test
    fun `behind defaults to nothing`() {
        val window = PreloadWindow(PreloadConfig(count = 2))
        assertEquals(PreloadStage.NONE, window.stageFor(4, currentIndex = 5))
        assertEquals(PreloadStage.RANGE_LOADED, window.stageFor(6, currentIndex = 5))
    }

    @Test
    fun `disk warming skips adjacent entries and orders nearest first`() {
        val window = PreloadWindow(ahead = 3, behind = 2)
        assertEquals(listOf(7, 3, 8), window.diskWarmIndices(currentIndex = 5, size = 20))
    }

    @Test
    fun `disk warming stays inside the playlist`() {
        val window = PreloadWindow(ahead = 3, behind = 3)
        assertEquals(listOf(3), window.diskWarmIndices(currentIndex = 1, size = 4))
        assertEquals(emptyList<Int>(), window.diskWarmIndices(currentIndex = 9, size = 4))
    }

    @Test
    fun `a count of one warms nothing to disk`() {
        assertEquals(emptyList<Int>(), PreloadWindow(1, 1).diskWarmIndices(5, 20))
    }

    @Test
    fun `count and behind drive enabled`() {
        assertFalse(PreloadConfig().enabled)
        assertTrue(PreloadConfig(count = 1).enabled)
        assertTrue(PreloadConfig(behind = 1).enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative count is rejected`() {
        PreloadConfig(count = -1)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `pre 2_2 form maps to the next entry`() {
        assertEquals(1, PreloadConfig(enabled = true).count)
        assertEquals(0, PreloadConfig(enabled = false).count)
        assertEquals(1, PreloadConfig.FEED.count)
        assertEquals(5_000L, PreloadConfig.FEED.targetPreloadDurationMs)
    }
}
