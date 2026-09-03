package io.fastpix.media3

import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.FastPixCacheKeyFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how playback URLs collapse into cache keys.
 *
 * The rule these lock in: parameters that *authorise* a request are dropped, parameters that
 * *select content* are kept. Getting the second half wrong is the dangerous direction — it would
 * serve one rendition's bytes for another.
 */
@UnstableApi
class CacheKeyNormalizationTest {

    private val factory = FastPixCacheKeyFactory(CacheConfig.DEFAULT_IGNORED_QUERY_PARAMETERS)

    @Test
    fun `url without a query is untouched`() {
        val url = "https://stream.fastpix.com/abc-123.m3u8"
        assertEquals(url, factory.normalize(url))
    }

    @Test
    fun `rotating token is dropped so a new session hits the cache`() {
        val first = "https://stream.fastpix.com/abc-123.m3u8?token=eyJhbGciOiJIUzI1NiJ9.AAA"
        val second = "https://stream.fastpix.com/abc-123.m3u8?token=eyJhbGciOiJIUzI1NiJ9.BBB"
        assertEquals(factory.normalize(first), factory.normalize(second))
        assertEquals("https://stream.fastpix.com/abc-123.m3u8", factory.normalize(first))
    }

    @Test
    fun `cdn signature and expiry are dropped`() {
        val url = "https://cdn.fastpix.com/blob/video_270/1.m4s" +
                "?cdn=cloudflare&expires=1790939259&signature=vdPPJi52bUmqft"
        assertEquals("https://cdn.fastpix.com/blob/video_270/1.m4s", factory.normalize(url))
    }

    @Test
    fun `content selecting parameters are preserved`() {
        val url = "https://stream.fastpix.com/abc-123.m3u8" +
                "?maxResolution=720p&token=AAA&renditionOrder=desc"
        assertEquals(
            "https://stream.fastpix.com/abc-123.m3u8?maxResolution=720p&renditionOrder=desc",
            factory.normalize(url),
        )
    }

    @Test
    fun `different renditions of one asset never collapse to the same key`() {
        val hd = factory.normalize("https://stream.fastpix.com/abc.m3u8?maxResolution=1080p&token=A")
        val sd = factory.normalize("https://stream.fastpix.com/abc.m3u8?maxResolution=480p&token=A")
        assert(hd != sd) { "renditions collapsed to one key: $hd" }
    }

    @Test
    fun `parameter names are matched case insensitively`() {
        val url = "https://stream.fastpix.com/abc.m3u8?Token=AAA&EXPIRES=1"
        assertEquals("https://stream.fastpix.com/abc.m3u8", factory.normalize(url))
    }

    @Test
    fun `fragment survives stripping`() {
        val url = "https://stream.fastpix.com/abc.m3u8?token=AAA#t=10"
        assertEquals("https://stream.fastpix.com/abc.m3u8#t=10", factory.normalize(url))
    }

    @Test
    fun `valueless parameters are handled`() {
        val url = "https://stream.fastpix.com/abc.m3u8?token&keepme"
        assertEquals("https://stream.fastpix.com/abc.m3u8?keepme", factory.normalize(url))
    }

    @Test
    fun `encoding of kept parameters is preserved verbatim`() {
        val url = "https://stream.fastpix.com/abc.m3u8?vsid=YF1B%2F28c9RZ%3D%3D&token=AAA"
        assertEquals(
            "https://stream.fastpix.com/abc.m3u8?vsid=YF1B%2F28c9RZ%3D%3D",
            factory.normalize(url),
        )
    }

    @Test
    fun `an empty ignore set restores exact url keying`() {
        val exact = FastPixCacheKeyFactory(emptySet())
        val url = "https://stream.fastpix.com/abc.m3u8?token=AAA"
        assertEquals(url, exact.normalize(url))
    }
}
