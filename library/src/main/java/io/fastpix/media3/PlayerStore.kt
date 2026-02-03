package io.fastpix.media3

import android.util.SparseArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Internal registry for managing ExoPlayer instances across configuration changes.
 *
 * This store maintains player instances to preserve playback state during configuration
 * changes (rotation, multi-window, etc.). Players are keyed by view ID to enable recovery
 * after view recreation.
 *
 * **Memory Management:**
 * - Players are stored with strong references to ensure they survive the brief period
 *   between view destruction and recreation during configuration changes.
 * - Players are automatically removed from the store when explicitly released via
 *   [PlayerView.release()] or when the view opts out of retention.
 * - The store does not prevent garbage collection of PlayerView instances themselves.
 *
 * @see PlayerView
 */
@UnstableApi
internal object PlayerStore {
    
    /**
     * Storage for player instances, keyed by view ID.
     * 
     * Uses strong references to ensure players survive configuration changes.
     * Players are removed from this store when:
     * - Explicitly released via PlayerView.release()
     * - PlayerView opts out of retention (retainPlayerOnConfigChange = false)
     * - PlayerView is destroyed without an ID (cannot be recovered)
     */
    private val players = SparseArray<ExoPlayer>()
    
    /**
     * Retrieves an existing player instance for the given view ID, or null if not found.
     *
     * @param viewId The view ID to look up.
     * @return The ExoPlayer instance if found, null otherwise.
     */
    fun getPlayer(viewId: Int): ExoPlayer? {
        return players.get(viewId)
    }
    
    /**
     * Stores a player instance for the given view ID.
     * If a player already exists for this ID, it is replaced.
     *
     * @param viewId The view ID to associate with the player.
     * @param player The ExoPlayer instance to store.
     */
    fun putPlayer(viewId: Int, player: ExoPlayer) {
        players.put(viewId, player)
    }
    
    /**
     * Removes a player instance from the store.
     * Does NOT release the player - that should be done by the caller if needed.
     *
     * @param viewId The view ID to remove.
     * @return The removed ExoPlayer instance, or null if not found.
     */
    fun removePlayer(viewId: Int): ExoPlayer? {
        val player = players.get(viewId)
        players.remove(viewId)
        return player
    }
    
    /**
     * Checks if a player exists for the given view ID and is still valid.
     *
     * @param viewId The view ID to check.
     * @return true if a valid player exists, false otherwise.
     */
    fun hasPlayer(viewId: Int): Boolean {
        return getPlayer(viewId) != null
    }
    
    /**
     * Releases and removes all stored players.
     * Useful for cleanup scenarios or testing.
     */
    fun releaseAllPlayers() {
        // Collect all players
        val playersToRelease = mutableListOf<ExoPlayer>()
        for (i in 0 until players.size()) {
            players.valueAt(i)?.let { playersToRelease.add(it) }
        }
        
        // Release all players
        playersToRelease.forEach { it.release() }
        
        // Clear storage
        players.clear()
    }
}

