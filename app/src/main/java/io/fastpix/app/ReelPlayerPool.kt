package io.fastpix.app

import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.core.FastPixPlayer
import kotlin.math.abs

/**
 * A small pool of [FastPixPlayer] instances shared across the pages of a reel feed.
 *
 * Creating a player per page and releasing it on the way out means paying MediaCodec configuration
 * (100-300 ms) on every swipe. Real short-form feeds keep two or three players alive and rotate
 * them, so the codec is already warm when the user arrives. This pool does the same: it hands out a
 * player for a page position and, when it runs out, recycles the one belonging to the page furthest
 * from where the user now is.
 *
 * Not thread-safe — call from the main thread only.
 */
@UnstableApi
class ReelPlayerPool(
    private val maxPlayers: Int,
    private val factory: () -> FastPixPlayer,
) {

    private val playersByPosition = LinkedHashMap<Int, FastPixPlayer>()

    /**
     * Returns the player serving [position], creating or recycling one if needed.
     *
     * @param currentPosition the page the user is on, used to decide which player to steal.
     */
    fun acquire(position: Int, currentPosition: Int): FastPixPlayer {
        playersByPosition[position]?.let { return it }

        val player = if (playersByPosition.size >= maxPlayers) {
            val victim = playersByPosition.keys.maxByOrNull { abs(it - currentPosition) }
                ?: playersByPosition.keys.first()
            val recycled = playersByPosition.remove(victim)!!
            recycled.pause()
            recycled
        } else {
            factory()
        }

        playersByPosition[position] = player
        return player
    }

    /** The player currently serving [position], if any. */
    fun playerAt(position: Int): FastPixPlayer? = playersByPosition[position]

    /** Pauses every player except the one serving [position]. */
    fun pauseAllExcept(position: Int) {
        for ((pos, player) in playersByPosition) {
            if (pos != position && player.isPlaying()) player.pause()
        }
    }

    /** The positions a player is currently assigned to — handy for the debug HUD. */
    fun assignedPositions(): Set<Int> = playersByPosition.keys.toSet()

    /** Number of live players. */
    fun size(): Int = playersByPosition.size

    /** Releases every player. Call from the host's `onDestroy`. */
    fun release() {
        for (player in playersByPosition.values) {
            player.release()
        }
        playersByPosition.clear()
    }
}
