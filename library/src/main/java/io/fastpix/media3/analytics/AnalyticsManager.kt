package io.fastpix.media3.analytics

import android.content.Context
import android.util.Log
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.fastpix.data.domain.model.PlayerDataDetails
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.data.exo.FastPixBaseMedia3Player
import io.fastpix.media3.info.FastPixPlayerLibraryInfo

/**
 * One FastPix Data view: one video, watched through one view, from [initialize] to [release].
 *
 * The FastPix Data SDK reports "view begin", "player ready" and "play" the moment it is created,
 * and keeps reporting under the video metadata it was created with — it has no way to switch
 * videos. So a view is created only when a video starts being watched, and a new one for each
 * video after it. Always go through [AnalyticsSessions], never construct one directly.
 *
 * All operations run on the main thread. Analytics failures are logged and never affect playback.
 */
@UnstableApi
internal class AnalyticsManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val config: AnalyticsConfig,
    private val view: View,
    private val videoDataDetails: VideoDataDetails?,
) : AnalyticsView {

    private var fastPixAnalytics: FastPixBaseMedia3Player? = null

    /** Begins the view. On failure, logs and continues without analytics. */
    override fun initialize() {
        if (!config.enabled) return
        try {
            fastPixAnalytics = FastPixBaseMedia3Player(
                context,
                playerView = view,
                exoPlayer = player,
                beaconUrl = config.beaconDomain,
                workSpaceId = config.workSpaceId,
                playerDataDetails = PlayerDataDetails(
                    FastPixPlayerLibraryInfo.PLAYER_NAME,
                    FastPixPlayerLibraryInfo.PLAYER_VERSION
                ),
                videoDataDetails = videoDataDetails,
                customDataDetails = config.customDataDetails
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analytics initialization failed; playback unaffected", e)
        }
    }

    /** Ends the view and detaches from the player. */
    override fun release() {
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

/** A view that can be begun and ended; [AnalyticsManager] in production. */
internal interface AnalyticsView {
    fun initialize()
    fun release()
}

/**
 * The single FastPix Data view open in this process.
 *
 * The FastPix Data SDK keeps one process-wide instance: creating a view re-initialises it, and
 * releasing any view tears it down. Two views open at once would corrupt each other, and ending
 * them in the wrong order would silence the newer one. So views are opened and closed only here,
 * one at a time, always closing the previous view before opening the next. Main thread only.
 */
internal object AnalyticsSessions {

    private var owner: Any? = null
    private var session: AnalyticsView? = null

    /** Ends whatever view is open — any player's — and begins [newSession] for [newOwner]. */
    fun begin(newOwner: Any, newSession: AnalyticsView) {
        end()
        owner = newOwner
        session = newSession
        newSession.initialize()
    }

    /** Ends the open view if [ownerToEnd] opened it. */
    fun endFor(ownerToEnd: Any) {
        if (owner === ownerToEnd) end()
    }

    private fun end() {
        session?.release()
        session = null
        owner = null
    }
}
