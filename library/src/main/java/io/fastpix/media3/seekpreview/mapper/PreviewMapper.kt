package io.fastpix.player.seekpreview.mapper

import io.fastpix.player.seekpreview.models.SpritesheetMetadata

/**
 * Maps time positions to frame indices and grid coordinates.
 * All operations are O(1) for guaranteed <16ms execution.
 */
interface PreviewMapper {
    
    /**
     * Maps a time position (in milliseconds) to a frame index.
     * 
     * @param timeMs Time position in milliseconds
     * @param metadata The sprite sheet metadata
     * @return Zero-based frame index, or -1 if out of range
     */
    fun timeToFrameIndex(timeMs: Long, metadata: SpritesheetMetadata): Int
    
    /**
     * Maps a frame index to grid coordinates (row, column).
     * 
     * @param frameIndex Zero-based frame index
     * @param metadata The sprite sheet metadata
     * @return Pair of (row, column), or null if out of range
     */
    fun frameIndexToGrid(frameIndex: Int, metadata: SpritesheetMetadata): Pair<Int, Int>?
    
    /**
     * Maps a time position directly to grid coordinates.
     * 
     * @param timeMs Time position in milliseconds
     * @param metadata The sprite sheet metadata
     * @return Pair of (row, column), or null if out of range
     */
    fun timeToGrid(timeMs: Long, metadata: SpritesheetMetadata): Pair<Int, Int>?
    
    /**
     * Formats a time position as a timestamp string (MM:SS).
     * 
     * @param timeMs Time position in milliseconds
     * @param durationMs Total duration in milliseconds
     * @return Formatted timestamp string
     */
    fun formatTimestamp(timeMs: Long, durationMs: Long): String
}

/**
 * Default implementation of [PreviewMapper] with O(1) operations.
 */
internal class PreviewMapperImpl : PreviewMapper {
    
    override fun timeToFrameIndex(timeMs: Long, metadata: SpritesheetMetadata): Int {
        if (timeMs < 0 || timeMs > metadata.durationMs) {
            return -1
        }
        
        // Clamp to valid range
        val clampedTime = timeMs.coerceIn(0, metadata.durationMs)
        
        // Calculate frame index: time / interval, clamped to frame count
        val frameIndex = (clampedTime / metadata.intervalMs).toInt()
        
        return frameIndex.coerceIn(0, metadata.frameCount - 1)
    }
    
    override fun frameIndexToGrid(frameIndex: Int, metadata: SpritesheetMetadata): Pair<Int, Int>? {
        if (frameIndex < 0 || frameIndex >= metadata.frameCount) {
            return null
        }
        
        val row = frameIndex / metadata.columns
        val column = frameIndex % metadata.columns
        
        return Pair(row, column)
    }
    
    override fun timeToGrid(timeMs: Long, metadata: SpritesheetMetadata): Pair<Int, Int>? {
        val frameIndex = timeToFrameIndex(timeMs, metadata)
        if (frameIndex < 0) {
            return null
        }
        return frameIndexToGrid(frameIndex, metadata)
    }
    
    override fun formatTimestamp(timeMs: Long, durationMs: Long): String {
        val clampedTime = timeMs.coerceIn(0, durationMs)
        val totalSeconds = (clampedTime / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        
        return String.format("%02d:%02d", minutes, seconds)
    }
}
