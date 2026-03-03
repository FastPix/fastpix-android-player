package io.fastpix.player.seekpreview.repository

import io.fastpix.player.seekpreview.models.SpritesheetMetadata
import java.io.File

/**
 * Source for loading sprite sheet data.
 */
interface SpritesheetSource {
    
    /**
     * Loads the sprite sheet PNG file.
     * 
     * @return The sprite sheet PNG file, or null if loading fails
     */
    suspend fun loadSpritesheet(): File?
    
    /**
     * Loads the sprite sheet metadata.
     * 
     * @return The metadata, or null if loading fails
     */
    suspend fun loadMetadata(): SpritesheetMetadata?
    
    /**
     * Gets a unique identifier for this source (for caching).
     * 
     * @return Unique identifier string
     */
    fun getSourceId(): String
}
