package io.fastpix.media3.analytics

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.fastpix.data.domain.model.PlayerDataDetails
import io.fastpix.data.exo.FastPixBaseMedia3Player
import io.fastpix.media3.info.FastPixPlayerLibraryInfo

/**
 * Internal manager for FastPix Media3 Analytics.
 *
 * Responsibilities:
 * - Initialize FastPix analytics SDK with [AnalyticsConfig]
 * - Attach analytics listener to ExoPlayer (via FastPixBaseMedia3Player)
 * - Release resources when the player is released
 *
 * All operations must run on the main thread. Analytics failures are logged
 * and never affect playback stability.
 */
@UnstableApi
internal class AnalyticsManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val config: AnalyticsConfig
) {

    private var fastPixAnalytics: FastPixBaseMedia3Player? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Initializes the FastPix analytics SDK and attaches to the player.
     * Safe to call from main thread only. On failure, logs and continues without analytics.
     */
    fun initialize() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { initialize() }
            return
        }
        if (!config.enabled) return
        try {
            val media3PlayerView = config.playerView.media3PlayerView
            fastPixAnalytics = FastPixBaseMedia3Player(
                context,
                playerView = media3PlayerView,
                exoPlayer = player,
                beaconUrl = config.beaconDomain,
                workSpaceId = config.workSpaceId,
                playerDataDetails = PlayerDataDetails(
                    FastPixPlayerLibraryInfo.PLAYER_NAME,
                    FastPixPlayerLibraryInfo.PLAYER_VERSION
                ),
                videoDataDetails = config.videoDataDetails,
                customDataDetails = config.customDataDetails
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analytics initialization failed; playback unaffected", e)
        }
    }

    /**
     * Releases analytics resources and removes listeners.
     * Call when the player is released. Safe to call from main thread only.
     */
    fun release() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { release() }
            return
        }
        try {
            fastPixAnalytics?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Analytics release failed", e)
        } finally {
            fastPixAnalytics = null
        }
    }

    companion object {
        private const val TAG = "FastPixAnalytics"
    }
}
