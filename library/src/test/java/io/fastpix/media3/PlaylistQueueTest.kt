package io.fastpix.media3

import io.fastpix.media3.playlist.PlaylistQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation and editing rules behind `FastPixPlayer`'s playlist. The player loads media exactly
 * when these operations report that the current entry changed, so each rule here is also a rule
 * about when playback restarts.
 */
class PlaylistQueueTest {

    private fun queue(vararg items: String, start: Int = 0) =
        PlaylistQueue<String>().apply { set(items.toList(), start) }

    @Test
    fun `set makes the start index current`() {
        val q = queue("a", "b", "c", start = 1)
        assertEquals(1, q.currentIndex)
        assertEquals("b", q.current)
    }

    @Test
    fun `set with an empty list leaves nothing current`() {
        val q = queue("a").apply { set(emptyList(), 0) }
        assertEquals(PlaylistQueue.NO_INDEX, q.currentIndex)
        assertNull(q.current)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `set rejects an out of range start index`() {
        queue("a", "b", start = 2)
    }

    @Test
    fun `next and previous stop at the ends without moving`() {
        val q = queue("a", "b")
        assertFalse(q.previous())
        assertTrue(q.next())
        assertEquals(1, q.currentIndex)
        assertFalse(q.next())
        assertEquals(1, q.currentIndex)
        assertTrue(q.previous())
        assertEquals(0, q.currentIndex)
    }

    @Test
    fun `an empty playlist has neither next nor previous`() {
        val q = PlaylistQueue<String>()
        assertFalse(q.hasNext())
        assertFalse(q.hasPrevious())
        assertFalse(q.next())
    }

    @Test
    fun `inserting before the current entry shifts its index but keeps it current`() {
        val q = queue("a", "b", "c", start = 1)
        val changed = q.add(0, listOf("x", "y"))
        assertFalse(changed)
        assertEquals(3, q.currentIndex)
        assertEquals("b", q.current)
    }

    @Test
    fun `inserting at the current index also shifts it`() {
        val q = queue("a", "b", start = 1)
        q.add(1, listOf("x"))
        assertEquals("b", q.current)
    }

    @Test
    fun `appending after the current entry changes nothing current`() {
        val q = queue("a", "b")
        assertFalse(q.add(2, listOf("c")))
        assertEquals(0, q.currentIndex)
        assertEquals(listOf("a", "b", "c"), q.snapshot())
    }

    @Test
    fun `adding to an empty playlist makes the first added entry current`() {
        val q = PlaylistQueue<String>()
        assertTrue(q.add(0, listOf("a", "b")))
        assertEquals("a", q.current)
    }

    @Test
    fun `adding nothing to an empty playlist changes nothing`() {
        val q = PlaylistQueue<String>()
        assertFalse(q.add(0, emptyList()))
        assertEquals(PlaylistQueue.NO_INDEX, q.currentIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inserting past the end is rejected`() {
        queue("a").add(2, listOf("x"))
    }

    @Test
    fun `removing before the current entry shifts it back`() {
        val q = queue("a", "b", "c", start = 2)
        assertFalse(q.removeAt(0))
        assertEquals(1, q.currentIndex)
        assertEquals("c", q.current)
    }

    @Test
    fun `removing after the current entry leaves it alone`() {
        val q = queue("a", "b", "c")
        assertFalse(q.removeAt(2))
        assertEquals("a", q.current)
    }

    @Test
    fun `removing the current entry makes its successor current`() {
        val q = queue("a", "b", "c", start = 1)
        assertTrue(q.removeAt(1))
        assertEquals(1, q.currentIndex)
        assertEquals("c", q.current)
    }

    @Test
    fun `removing the current last entry falls back to the new last entry`() {
        val q = queue("a", "b", "c", start = 2)
        assertTrue(q.removeAt(2))
        assertEquals("b", q.current)
    }

    @Test
    fun `removing the only entry empties the playlist`() {
        val q = queue("a")
        assertTrue(q.removeAt(0))
        assertEquals(PlaylistQueue.NO_INDEX, q.currentIndex)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `duplicate entries are distinct positions`() {
        val q = queue("a", "a")
        assertTrue(q.next())
        assertEquals(1, q.currentIndex)
        assertEquals("a", q.current)
    }

    @Test
    fun `snapshot is not affected by later edits`() {
        val q = queue("a", "b")
        val snapshot = q.snapshot()
        q.removeAt(0)
        assertEquals(listOf("a", "b"), snapshot)
    }
}
