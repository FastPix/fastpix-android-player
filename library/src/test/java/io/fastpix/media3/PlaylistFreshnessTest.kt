package io.fastpix.media3

import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.cache.PlaylistStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A stored playlist must never outlive the signatures in it, and a live media playlist must never
 * be stored: either mistake breaks playback rather than just slowing it.
 */
@UnstableApi
class PlaylistFreshnessTest {

    private val now = 1_790_767_000_000L // ms
    private val day = 24L * 60 * 60 * 1000
    private val margin = PlaylistStore.EXPIRY_MARGIN_MS
    private val masterUrl = "https://stream.fastpix.io/6a49da1a-6c3e-4c2b-96d2-a0606b8e252a.m3u8"

    private fun master(expiresSeconds: Long) = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,URI="https://m.fastpix.io/x/stream_default_audio_hi.m3u8?cdn=cf&expires=$expiresSeconds&signature=a",GROUP-ID="audio-hi-0",NAME="default"
        #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=960x540,AUDIO="audio-hi-0"
        https://m.fastpix.io/y/stream_3.m3u8?cdn=cf&expires=$expiresSeconds&signature=b
    """.trimIndent()

    private val vodMedia = """
        #EXTM3U
        #EXT-X-TARGETDURATION:4
        #EXTINF:4.000,
        https://cdn.fastpix.io/z/video_540/1.m4s
        #EXT-X-ENDLIST
    """.trimIndent()

    private val liveMedia = """
        #EXTM3U
        #EXT-X-TARGETDURATION:4
        #EXT-X-MEDIA-SEQUENCE:120
        #EXTINF:4.000,
        https://cdn.fastpix.io/z/video_540/120.m4s
    """.trimIndent()

    @Test
    fun masterIsKeptUntilItsSignaturesNearExpiry() {
        val expires = now / 1000 + 3600 // one hour out
        assertEquals(expires * 1000 - margin, PlaylistStore.freshUntil(masterUrl, master(expires), now, day))
    }

    @Test
    fun farExpiryIsCappedAtMaxAge() {
        val expires = now / 1000 + 30L * 24 * 3600 // FastPix signs ~30 days out
        assertEquals(now + day, PlaylistStore.freshUntil(masterUrl, master(expires), now, day))
    }

    @Test
    fun signatureExpiringWithinMarginIsNotStored() {
        val expires = now / 1000 + 60
        assertNull(PlaylistStore.freshUntil(masterUrl, master(expires), now, day))
    }

    @Test
    fun expiryInTheRequestUrlCounts() {
        val expires = now / 1000 + 3600
        val url = "https://m.fastpix.io/y/stream_3.m3u8?cdn=cf&expires=$expires&signature=b"
        assertEquals(expires * 1000 - margin, PlaylistStore.freshUntil(url, vodMedia, now, day))
    }

    @Test
    fun finishedVodMediaPlaylistWithoutSignaturesUsesMaxAge() {
        assertEquals(now + day, PlaylistStore.freshUntil("https://cdn.example.com/a.m3u8", vodMedia, now, day))
    }

    @Test
    fun liveMediaPlaylistIsNeverStored() {
        assertNull(PlaylistStore.freshUntil("https://cdn.example.com/live.m3u8", liveMedia, now, day))
    }

    @Test
    fun zeroMaxAgeDisablesStorage() {
        assertNull(PlaylistStore.freshUntil(masterUrl, master(now / 1000 + 3600), now, 0))
    }
}
