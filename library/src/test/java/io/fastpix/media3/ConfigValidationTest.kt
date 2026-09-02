package io.fastpix.media3

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.PreCacheConfig
import io.fastpix.media3.preload.PreloadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards on the configuration objects added in 2.1.0. These are pure-JVM checks: they assert the
 * invariants Media3's own builders would otherwise blow up on at player-construction time, so a
 * bad config fails at the call site with a readable message instead.
 */
@UnstableApi
class ConfigValidationTest {

    @Test
    fun `default buffer config starts sooner than media3 stock`() {
        assertTrue(
            "SDK default should start before Media3's stock threshold",
            BufferConfig.DEFAULT.bufferForPlaybackMs <
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        )
    }

    @Test
    fun `media3 default preset mirrors media3 constants`() {
        assertEquals(
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            BufferConfig.MEDIA3_DEFAULT.bufferForPlaybackMs,
        )
        assertEquals(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            BufferConfig.MEDIA3_DEFAULT.minBufferMs,
        )
    }

    @Test
    fun `feed preset holds a shallower buffer than the default`() {
        assertTrue(BufferConfig.FEED.maxBufferMs < BufferConfig.DEFAULT.maxBufferMs)
        assertTrue(BufferConfig.FEED.bufferForPlaybackMs < BufferConfig.DEFAULT.bufferForPlaybackMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `start threshold above min buffer is rejected`() {
        BufferConfig(minBufferMs = 1_000, maxBufferMs = 10_000, bufferForPlaybackMs = 5_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `max buffer below min buffer is rejected`() {
        BufferConfig(minBufferMs = 20_000, maxBufferMs = 10_000, bufferForPlaybackMs = 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero start threshold is rejected`() {
        BufferConfig(bufferForPlaybackMs = 0)
    }

    @Test
    fun `cache is off by default and on for the feed preset`() {
        assertFalse(CacheConfig().enabled)
        assertFalse("playlists must not be cached unless asked", CacheConfig().cachePlaylists)
        assertTrue(CacheConfig.forOnDemandFeed().enabled)
        assertTrue(CacheConfig.forOnDemandFeed().cachePlaylists)
        assertFalse("plain enabled() must stay live-safe", CacheConfig.enabled().cachePlaylists)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive cache ceiling is rejected`() {
        CacheConfig(enabled = true, maxBytes = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive segment count is rejected`() {
        PreCacheConfig(segmentCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive parallelism is rejected`() {
        PreCacheConfig(maxParallelItems = 0)
    }

    @Test
    fun `preload is off by default`() {
        assertFalse(PreloadConfig.DISABLED.enabled)
        assertTrue(PreloadConfig.FEED.enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive preload duration is rejected`() {
        PreloadConfig(enabled = true, targetPreloadDurationMs = 0L)
    }
}
