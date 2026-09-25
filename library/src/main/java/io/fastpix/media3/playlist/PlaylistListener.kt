package io.fastpix.media3.playlist

import androidx.media3.common.util.UnstableApi

/**
 * Observes a player's playlist. Register with
 * [io.fastpix.media3.core.FastPixPlayer.addPlaylistListener]. Callbacks arrive on the main thread.
 *
 * Both methods have empty defaults; override the ones you need.
 */
@UnstableApi
interface PlaylistListener {

    /**
     * A different entry became current and started loading.
     *
     * @param index position of [item] in the playlist.
     * @param item the entry now current.
     * @param reason what caused the change.
     */
    fun onPlaylistItemChanged(index: Int, item: PlaylistItem, reason: PlaylistItemChangeReason) {}

    /**
     * The playlist's contents changed: set, added to, removed from, or cleared.
     *
     * @param items the playlist after the change; empty when it was cleared.
     */
    fun onPlaylistChanged(items: List<PlaylistItem>) {}
}

/** Why the current playlist entry changed. */
enum class PlaylistItemChangeReason {
    /** A new playlist was set with [io.fastpix.media3.core.FastPixPlayer.setPlaylist]. */
    PLAYLIST_SET,

    /** The app navigated: `next()`, `previous()` or `skipTo()`. */
    NAVIGATION,

    /** The previous entry played to the end and playback moved on. */
    AUTO_ADVANCE,

    /**
     * The playlist was edited: the current entry was removed, or the first entry was added to an
     * empty playlist.
     */
    PLAYLIST_EDITED,
}
