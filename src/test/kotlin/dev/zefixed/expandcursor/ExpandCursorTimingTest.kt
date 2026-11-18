package dev.zefixed.expandcursor

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandCursorTimingTest {

    private val cycle = 1000L

    @Test
    fun `solid at restart and during initial hold`() {
        assertEquals(1f, ExpandCursorTiming.scaleAt(0, cycle), 1e-6f)
        assertEquals(1f, ExpandCursorTiming.scaleAt(50, cycle), 1e-6f)
        assertEquals(1f, ExpandCursorTiming.scaleAt(99, cycle), 1e-6f)
    }

    @Test
    fun `collapse passes half scale at midpoint`() {
        assertEquals(0.5f, ExpandCursorTiming.scaleAt(250, cycle), 1e-3f)
    }

    @Test
    fun `hidden during hidden hold`() {
        assertEquals(0f, ExpandCursorTiming.scaleAt(500, cycle), 1e-6f)
        assertEquals(0f, ExpandCursorTiming.scaleAt(599, cycle), 1e-6f)
    }

    @Test
    fun `expand passes half scale at midpoint`() {
        assertEquals(0.5f, ExpandCursorTiming.scaleAt(750, cycle), 1e-3f)
    }

    @Test
    fun `solid during tail hold and after wrap`() {
        assertEquals(1f, ExpandCursorTiming.scaleAt(950, cycle), 1e-6f)
        assertEquals(1f, ExpandCursorTiming.scaleAt(1050, cycle), 1e-6f)
        assertEquals(1f, ExpandCursorTiming.scaleAt(1950, cycle), 1e-6f)
    }

    @Test
    fun `second cycle repeats the same rhythm`() {
        assertEquals(0.5f, ExpandCursorTiming.scaleAt(1250, cycle), 1e-3f)
        assertEquals(0f, ExpandCursorTiming.scaleAt(1500, cycle), 1e-6f)
    }

    @Test
    fun `animation is continuous at cycle boundaries`() {
        val nearEnd = ExpandCursorTiming.scaleAt(999, cycle).toDouble()
        val nearStart = ExpandCursorTiming.scaleAt(0, cycle).toDouble()
        assertEquals(1.0, nearEnd, 0.05)
        assertEquals(1.0, nearStart, 0.05)
    }

    @Test
    fun `easeInOutCubic matches reference values`() {
        assertEquals(0.0, ExpandCursorTiming.easeInOutCubic(0.0), 1e-9)
        assertEquals(0.5, ExpandCursorTiming.easeInOutCubic(0.5), 1e-9)
        assertEquals(1.0, ExpandCursorTiming.easeInOutCubic(1.0), 1e-9)
    }

    @Test
    fun `cycle follows blink period`() {
        assertEquals(1000L, ExpandCursorTiming.cycleMillis(500))
        assertEquals(20L, ExpandCursorTiming.cycleMillis(10))
        assertEquals(3000L, ExpandCursorTiming.cycleMillis(1500))
    }
}
