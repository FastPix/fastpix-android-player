package io.fastpix.media3

import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.FastPixCacheKeyFactory
import io.fastpix.media3.cache.FastPixItemKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Keys for FastPix URLs whose path carries a signature that changes on every playlist fetch.
 *
 * The two directions of failure: keys that differ across re-signing mean cached segments never
 * hit; keys that collide across assets serve one video's bytes for another. The URL shapes below
 * are taken from a real FastPix ladder.
 */
@UnstableApi
class ItemScopedCacheKeyTest {

    private val ignored = CacheConfig.DEFAULT_IGNORED_QUERY_PARAMETERS
    private val assetA = FastPixCacheKeyFactory(ignored, itemKey = "d3fcdcdd-0608-4767-888d-c7dd5c077a04")
    private val assetB = FastPixCacheKeyFactory(ignored, itemKey = "112a2222-0f31-44a0-bcf6-30cfa6e1d17d")
    private val unscoped = FastPixCacheKeyFactory(ignored)

    private val blob1 = "a2S3qo6Kvrh6P4eHrY3Y98xd4loMKEeP%2BJkQyVxS9OvEmsSlwEDeKSwD%2FUMsS7mldtsIT0eUkA1x69UO8f1H"
    private val blob2 = "Pk0VB8VOhiT5Z7cUMHncWPAYpL%2BqC6wGUdrj%2F2Uf0hVjQmdrKsou525WdWlbqtkESnshhG%2Fz9rdpKKrd"
    private val query = "?cdn=cloudflare&expires=1792866754&signature=Mw6eJS%2FCcLeaG8uiAsxqeXY2EU2Dj1sjK5Ktk56C7Hc%3D"

    private fun segment(blob: String, path: String = "video_1080/1.m4s") =
        "https://cdn.fastpix.io/$blob/$path$query"

    @Test
    fun `the same segment re-signed keeps its key`() {
        assertEquals(assetA.itemScoped(segment(blob1)), assetA.itemScoped(segment(blob2)))
    }

    @Test
    fun `the key names the asset and the stable path, without signing parameters`() {
        assertEquals(
            "fastpix-item:d3fcdcdd-0608-4767-888d-c7dd5c077a04/video_1080/1.m4s",
            assetA.itemScoped(segment(blob1)),
        )
    }

    @Test
    fun `the same path in two assets never shares a key`() {
        assertNotEquals(assetA.itemScoped(segment(blob1)), assetB.itemScoped(segment(blob1)))
    }

    @Test
    fun `renditions and segment numbers stay distinct`() {
        assertNotEquals(
            assetA.itemScoped(segment(blob1, "video_1080/1.m4s")),
            assetA.itemScoped(segment(blob1, "video_720/1.m4s")),
        )
        assertNotEquals(
            assetA.itemScoped(segment(blob1, "video_1080/1.m4s")),
            assetA.itemScoped(segment(blob1, "video_1080/2.m4s")),
        )
    }

    @Test
    fun `content selecting parameters stay in the key`() {
        val key = assetA.itemScoped("https://cdn.fastpix.io/$blob1/video_1080/1.m4s?maxResolution=720p&token=abc")
        assertEquals("fastpix-item:d3fcdcdd-0608-4767-888d-c7dd5c077a04/video_1080/1.m4s?maxResolution=720p", key)
    }

    @Test
    fun `a stream url without a signed segment is not scoped`() {
        assertNull(assetA.itemScoped("https://stream.fastpix.io/d3fcdcdd-0608-4767-888d-c7dd5c077a04.m3u8?token=x"))
    }

    @Test
    fun `short first path segments are ordinary directories, not signatures`() {
        assertNull(assetA.itemScoped("https://example.com/videos/clip/1.ts"))
    }

    @Test
    fun `without an asset key nothing is scoped`() {
        assertNull(unscoped.itemScoped(segment(blob1)))
    }

    @Test
    fun `playback id is read from fastpix stream urls`() {
        assertEquals("abc-123", FastPixItemKeys.fromStreamUrl("https://stream.fastpix.com/abc-123.m3u8"))
        assertEquals("abc-123", FastPixItemKeys.fromStreamUrl("https://stream.fastpix.io/abc-123.m3u8?token=t"))
        assertEquals("abc-123", FastPixItemKeys.fromStreamUrl("https://venus-stream.fastpix.dev/abc-123.m3u8"))
    }

    @Test
    fun `other urls yield no playback id`() {
        assertNull(FastPixItemKeys.fromStreamUrl("https://example.com/abc-123.m3u8"))
        assertNull(FastPixItemKeys.fromStreamUrl("https://stream.fastpix.com/dir/abc-123.m3u8"))
        assertNull(FastPixItemKeys.fromStreamUrl("https://stream.fastpix.com/abc-123.mp4"))
        assertNull(FastPixItemKeys.fromStreamUrl("https://notstream.fastpix.com.evil.io/abc.m3u8"))
        assertNull(FastPixItemKeys.fromStreamUrl(null))
    }
}
