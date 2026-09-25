package io.fastpix.media3.playlist

/**
 * The playlist's contents and current position, with no playback attached.
 *
 * Pure bookkeeping so navigation and editing rules are unit-testable on the JVM: the player applies
 * the result, loading media only when [currentIndex] or the entry at it changes. Generic over the
 * entry type for the same reason — tests use plain strings.
 *
 * Not thread-safe; the player confines it to the main thread.
 */
internal class PlaylistQueue<T> {

    private val entries = mutableListOf<T>()

    /** Position of the current entry, or [NO_INDEX] when the playlist is empty. */
    var currentIndex: Int = NO_INDEX
        private set

    val size: Int get() = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()

    /** Snapshot of the entries; later edits do not affect it. */
    fun snapshot(): List<T> = entries.toList()

    val current: T? get() = entries.getOrNull(currentIndex)

    operator fun get(index: Int): T = entries[index]

    fun hasNext(): Boolean = currentIndex != NO_INDEX && currentIndex < entries.lastIndex

    fun hasPrevious(): Boolean = currentIndex > 0

    /** Replaces the contents and makes [startIndex] current (or nothing, when [items] is empty). */
    fun set(items: List<T>, startIndex: Int) {
        if (items.isNotEmpty()) {
            require(startIndex in items.indices) {
                "startIndex $startIndex out of range for ${items.size} items"
            }
        }
        entries.clear()
        entries.addAll(items)
        currentIndex = if (items.isEmpty()) NO_INDEX else startIndex
    }

    /** Moves to the next entry. Returns false, changing nothing, at the end. */
    fun next(): Boolean {
        if (!hasNext()) return false
        currentIndex++
        return true
    }

    /** Moves to the previous entry. Returns false, changing nothing, at the start. */
    fun previous(): Boolean {
        if (!hasPrevious()) return false
        currentIndex--
        return true
    }

    fun moveTo(index: Int) {
        require(index in entries.indices) { "index $index out of range for $size items" }
        currentIndex = index
    }

    /**
     * Inserts [items] at [index] (append when [index] equals [size]).
     *
     * @return true when this made a different entry current — only when adding to an empty
     *   playlist, which makes the first added entry current. Otherwise the current entry stays
     *   current, its index shifting if the insert landed before it.
     */
    fun add(index: Int, items: List<T>): Boolean {
        require(index in 0..entries.size) { "index $index out of range for insert into $size items" }
        if (items.isEmpty()) return false
        val wasEmpty = entries.isEmpty()
        entries.addAll(index, items)
        return if (wasEmpty) {
            currentIndex = 0
            true
        } else {
            if (index <= currentIndex) currentIndex += items.size
            false
        }
    }

    /**
     * Removes the entry at [index].
     *
     * @return true when the current entry was the one removed. The entry that slid into its
     *   position becomes current — or the new last entry when the last one was removed, or nothing
     *   when the playlist is now empty.
     */
    fun removeAt(index: Int): Boolean {
        require(index in entries.indices) { "index $index out of range for $size items" }
        entries.removeAt(index)
        return when {
            index < currentIndex -> {
                currentIndex--
                false
            }

            index > currentIndex -> false

            else -> {
                currentIndex = when {
                    entries.isEmpty() -> NO_INDEX
                    currentIndex > entries.lastIndex -> entries.lastIndex
                    else -> currentIndex
                }
                true
            }
        }
    }

    fun clear() {
        entries.clear()
        currentIndex = NO_INDEX
    }

    companion object {
        const val NO_INDEX = -1
    }
}
