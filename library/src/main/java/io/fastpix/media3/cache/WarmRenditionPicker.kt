package io.fastpix.media3.cache

/**
 * Chooses which HLS variant [FastPixPreCacher] warms, following the same rules ExoPlayer uses to
 * pick the variant a new item starts on. A warm only pays off when playback then asks for the very
 * same segments, so every rule the player's initial selection applies has to be applied here too:
 *
 * 1. **Viewport.** `DefaultTrackSelector` by default drops variants larger than the physical
 *    display, keeping the smallest one that still fills it (orientation may change). Mirrors
 *    `DefaultTrackSelector.getMaxVideoPixelsToRetainForViewport`.
 * 2. **Bitrate.** Of what is left, `AdaptiveTrackSelection` starts on the highest variant whose
 *    bitrate fits the allocated bandwidth ([targetBps]), or the lowest when none fits.
 *
 * Pure Kotlin so the rules can be unit tested without Android.
 */
internal object WarmRenditionPicker {

    /** One variant of the ladder. Unknown values are [NO_VALUE], as in Media3's `Format`. */
    data class Candidate(val bitrate: Int, val width: Int, val height: Int)

    const val NO_VALUE = -1

    /** Media3's `DefaultTrackSelector.FRACTION_TO_CONSIDER_FULLSCREEN`. */
    private const val FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f

    /**
     * @return index into [candidates] of the variant to warm, or -1 when [candidates] is empty.
     * @param viewportWidth display width, or [Int.MAX_VALUE] for no viewport constraint.
     */
    fun pick(
        candidates: List<Candidate>,
        targetBps: Int,
        viewportWidth: Int = Int.MAX_VALUE,
        viewportHeight: Int = Int.MAX_VALUE,
    ): Int {
        if (candidates.isEmpty()) return -1

        val maxPixels = maxPixelsToRetain(candidates, viewportWidth, viewportHeight)
        val fitsViewport = candidates.indices.filter { i ->
            val c = candidates[i]
            maxPixels == Int.MAX_VALUE ||
                (c.width > 0 && c.height > 0 && c.width * c.height <= maxPixels)
        }.ifEmpty { candidates.indices.toList() }

        val withBitrate = fitsViewport.filter { candidates[it].bitrate != NO_VALUE }
        if (withBitrate.isEmpty()) return fitsViewport.first()

        return withBitrate.filter { candidates[it].bitrate <= targetBps }
            .maxByOrNull { candidates[it].bitrate }
            ?: withBitrate.minBy { candidates[it].bitrate }
    }

    private fun maxPixelsToRetain(candidates: List<Candidate>, viewportWidth: Int, viewportHeight: Int): Int {
        if (viewportWidth == Int.MAX_VALUE || viewportHeight == Int.MAX_VALUE) return Int.MAX_VALUE
        var maxPixels = Int.MAX_VALUE
        for (c in candidates) {
            if (c.width <= 0 || c.height <= 0) continue
            val (fitWidth, fitHeight) = maxVideoSizeInViewport(viewportWidth, viewportHeight, c.width, c.height)
            val pixels = c.width * c.height
            if (c.width >= (fitWidth * FRACTION_TO_CONSIDER_FULLSCREEN).toInt() &&
                c.height >= (fitHeight * FRACTION_TO_CONSIDER_FULLSCREEN).toInt() &&
                pixels < maxPixels
            ) {
                maxPixels = pixels
            }
        }
        return maxPixels
    }

    /** Media3's `TrackSelectionUtil.getMaxVideoSizeInViewport` with `orientationMayChange = true`. */
    private fun maxVideoSizeInViewport(
        viewportWidth: Int,
        viewportHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): Pair<Int, Int> {
        var w = viewportWidth
        var h = viewportHeight
        if ((videoWidth > videoHeight) != (w > h)) {
            w = viewportHeight
            h = viewportWidth
        }
        return if (videoWidth * h >= videoHeight * w) {
            w to ceilDivide(w * videoHeight, videoWidth)
        } else {
            ceilDivide(h * videoWidth, videoHeight) to h
        }
    }

    private fun ceilDivide(numerator: Int, denominator: Int): Int =
        (numerator + denominator - 1) / denominator
}
