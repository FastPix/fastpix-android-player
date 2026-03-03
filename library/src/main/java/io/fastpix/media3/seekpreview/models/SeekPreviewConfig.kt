package io.fastpix.player.seekpreview.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.net.URL

/**
 * Configuration for seek preview behavior.
 * Use [Builder] to create instances.
 *
 * @property enabled Whether seek preview (thumbnail scrubbing) is enabled
 * @property customSpritesheetUrl Optional custom spritesheet URL; null to use default from playback URL
 * @property fallbackMode When thumbnails are unavailable: [PreviewFallbackMode.TIMESTAMP] shows time (e.g. "02:30"), [PreviewFallbackMode.NONE] shows nothing
 * @property enablePreload Whether to preload surrounding frames for smooth scrubbing
 * @property preloadRadius Number of frames to preload in each direction (default: 1 = 3x3 grid)
 * @property cacheEnabled Whether to enable disk and memory caching
 */
@Parcelize
data class SeekPreviewConfig(
    val enabled: Boolean = true,
    val fallbackMode: PreviewFallbackMode = PreviewFallbackMode.TIMESTAMP,
    val enablePreload: Boolean = true,
    val preloadRadius: Int = 1,
    val cacheEnabled: Boolean = true
) : Parcelable {

    init {
        require(preloadRadius >= 0) { "Preload radius must be non-negative" }
        require(preloadRadius <= 3) { "Preload radius cannot exceed 3 for performance" }
    }

    class Builder {
        private var enabled: Boolean = true
        private var fallbackMode: PreviewFallbackMode = PreviewFallbackMode.TIMESTAMP
        private var enablePreload: Boolean = true
        private var preloadRadius: Int = 1
        private var cacheEnabled: Boolean = true

        fun setEnabled(enabled: Boolean) = apply { this.enabled = enabled }
        fun setFallbackMode(mode: PreviewFallbackMode) = apply { this.fallbackMode = mode }
        fun setEnablePreload(enablePreload: Boolean) = apply { this.enablePreload = enablePreload }
        fun setPreloadRadius(preloadRadius: Int) = apply { this.preloadRadius = preloadRadius }
        fun setCacheEnabled(cacheEnabled: Boolean) = apply { this.cacheEnabled = cacheEnabled }

        fun build(): SeekPreviewConfig = SeekPreviewConfig(
            enabled = enabled,
            fallbackMode = fallbackMode,
            enablePreload = enablePreload,
            preloadRadius = preloadRadius,
            cacheEnabled = cacheEnabled
        )
    }

    companion object {
        /**
         * Default configuration with seek preview enabled and preloading on.
         */
        @JvmStatic
        val DEFAULT: SeekPreviewConfig = Builder().build()

        /**
         * Configuration with preloading disabled (seek preview still enabled).
         */
        @JvmStatic
        val NO_PRELOAD: SeekPreviewConfig = Builder()
            .setEnablePreload(false)
            .setPreloadRadius(0)
            .build()
    }
}
