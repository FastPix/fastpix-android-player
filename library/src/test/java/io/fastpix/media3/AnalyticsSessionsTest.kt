package io.fastpix.media3

import io.fastpix.media3.analytics.AnalyticsSessions
import io.fastpix.media3.analytics.AnalyticsView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The FastPix Data SDK keeps one process-wide instance, and releasing any view tears it down. These
 * pin the order that keeps reporting intact: the old view always ends before the next begins, and a
 * player can only end its own view.
 */
class AnalyticsSessionsTest {

    private val log = mutableListOf<String>()

    private inner class FakeView(private val name: String) : AnalyticsView {
        override fun initialize() {
            log += "begin $name"
        }

        override fun release() {
            log += "end $name"
        }
    }

    private val playerA = Any()
    private val playerB = Any()

    @After
    fun tearDown() {
        AnalyticsSessions.endFor(playerA)
        AnalyticsSessions.endFor(playerB)
    }

    @Test
    fun `the previous view ends before the next begins`() {
        AnalyticsSessions.begin(playerA, FakeView("video 1"))
        AnalyticsSessions.begin(playerA, FakeView("video 2"))
        assertEquals(listOf("begin video 1", "end video 1", "begin video 2"), log)
    }

    @Test
    fun `beginning a view on another player ends the open one`() {
        AnalyticsSessions.begin(playerA, FakeView("page 3"))
        AnalyticsSessions.begin(playerB, FakeView("page 4"))
        assertEquals(listOf("begin page 3", "end page 3", "begin page 4"), log)
    }

    @Test
    fun `a player cannot end another player's view`() {
        AnalyticsSessions.begin(playerB, FakeView("page 4"))
        AnalyticsSessions.endFor(playerA)
        assertEquals(listOf("begin page 4"), log)
    }

    @Test
    fun `ending twice ends once`() {
        AnalyticsSessions.begin(playerA, FakeView("video"))
        AnalyticsSessions.endFor(playerA)
        AnalyticsSessions.endFor(playerA)
        assertEquals(listOf("begin video", "end video"), log)
    }
}
