package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.WeeklyState
import dev.matejgroombridge.voquab.data.settings.WeekStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeeklyEngineTest {

    // Monday 5 Oct 2026 … Sunday 11 Oct 2026.
    private val mon: Long = LocalDate.of(2026, 10, 5).toEpochDay()
    private val sun: Long = mon + 6

    private fun at(day: Long, hour: Int = 9): LocalDateTime = LocalDate.ofEpochDay(day).atTime(hour, 0)

    /** Hands out w1, w2, … so each test can see which picks happened. */
    private fun counter(): (Set<String>) -> String? {
        var n = 0
        return { _ -> "w${++n}" }
    }

    private fun week(start: Long, used: Boolean = false, paused: Boolean = false, word: String? = "x") =
        WeekEntry(start = start, end = start + 6, wordId = word, usedOn = if (used) start + 2 else null, paused = paused)

    // --- Rollover ---------------------------------------------------------

    @Test
    fun `first week starts today and runs to the end of the week`() {
        val wed = mon + 2
        val state = WeeklyEngine.rollOver(WeeklyState(), wed, WeekStart.Monday, counter())
        assertEquals(1, state.weeks.size)
        assertEquals(wed, state.weeks[0].start)
        assertEquals(sun, state.weeks[0].end)
        assertEquals("w1", state.weeks[0].wordId)
    }

    @Test
    fun `first week installed late in the week stretches to next week`() {
        val fri = mon + 4
        val state = WeeklyEngine.rollOver(WeeklyState(), fri, WeekStart.Monday, counter())
        assertEquals(sun + 7, state.weeks[0].end)
    }

    @Test
    fun `rollover is a no-op inside the current week`() {
        val start = WeeklyState(weeks = listOf(week(mon)))
        assertEquals(start, WeeklyEngine.rollOver(start, mon + 3, WeekStart.Monday, counter()))
    }

    @Test
    fun `next week gets a fresh word`() {
        val state = WeeklyEngine.rollOver(WeeklyState(weeks = listOf(week(mon))), mon + 7, WeekStart.Monday, counter())
        assertEquals(2, state.weeks.size)
        assertEquals(WeekEntry(start = mon + 7, end = sun + 7, wordId = "w1"), state.weeks[1])
    }

    @Test
    fun `weeks the app slept through are back-filled without words`() {
        val state = WeeklyEngine.rollOver(WeeklyState(weeks = listOf(week(mon))), mon + 22, WeekStart.Monday, counter())
        assertEquals(listOf(mon, mon + 7, mon + 14, mon + 21), state.weeks.map { it.start })
        assertNull(state.weeks[1].wordId)
        assertNull(state.weeks[2].wordId)
        assertEquals("w1", state.weeks[3].wordId) // only the current week picks
    }

    @Test
    fun `paused weeks get no word`() {
        val state = WeeklyEngine.rollOver(
            WeeklyState(weeks = listOf(week(mon)), paused = true), mon + 7, WeekStart.Monday, counter(),
        )
        assertTrue(state.weeks[1].paused)
        assertNull(state.weeks[1].wordId)
    }

    @Test
    fun `changing week start leaves a ragged week but no gap or overlap`() {
        // Week ran Mon–Sun; user switched to Sunday starts. Next week begins
        // the following Monday and ends that Saturday.
        val state = WeeklyEngine.rollOver(WeeklyState(weeks = listOf(week(mon))), mon + 8, WeekStart.Sunday, counter())
        val next = state.weeks[1]
        assertEquals(mon + 7, next.start)
        assertEquals(mon + 12, next.end) // Saturday
    }

    @Test
    fun `previous weekly words and swaps are excluded from picks`() {
        var seen: Set<String> = emptySet()
        val weeks = listOf(week(mon, word = "a").copy(swappedOut = "b"))
        WeeklyEngine.rollOver(WeeklyState(weeks = weeks), mon + 7, WeekStart.Monday) { seen = it; "c" }
        assertEquals(setOf("a", "b"), seen)
    }

    // --- Logging & grace ---------------------------------------------------

    @Test
    fun `can log during the week`() {
        assertTrue(WeeklyEngine.canLog(week(mon), mon + 3, at(mon + 3)))
    }

    @Test
    fun `can still log last week until noon the next day`() {
        val w = week(mon)
        assertTrue(WeeklyEngine.canLog(w, sun + 1, at(sun + 1, hour = 11)))
        assertFalse(WeeklyEngine.canLog(w, sun + 1, at(sun + 1, hour = 12)))
    }

    @Test
    fun `cannot log a used, paused or wordless week`() {
        assertFalse(WeeklyEngine.canLog(week(mon, used = true), mon, at(mon)))
        assertFalse(WeeklyEngine.canLog(week(mon, paused = true), mon, at(mon)))
        assertFalse(WeeklyEngine.canLog(week(mon, word = null), mon, at(mon)))
    }

    @Test
    fun `grace week is only offered while open and unused`() {
        val state = WeeklyState(weeks = listOf(week(mon), week(mon + 7)))
        assertEquals(mon, WeeklyEngine.graceWeek(state, sun + 1, at(sun + 1, 9))?.start)
        assertNull(WeeklyEngine.graceWeek(state, sun + 1, at(sun + 1, 13)))
        val used = WeeklyState(weeks = listOf(week(mon, used = true), week(mon + 7)))
        assertNull(WeeklyEngine.graceWeek(used, sun + 1, at(sun + 1, 9)))
    }

    @Test
    fun `swap is allowed once and only before logging`() {
        assertTrue(WeeklyEngine.canSwap(week(mon)))
        assertFalse(WeeklyEngine.canSwap(week(mon).copy(swappedOut = "y")))
        assertFalse(WeeklyEngine.canSwap(week(mon, used = true)))
    }

    // --- Streaks -----------------------------------------------------------

    @Test
    fun `consecutive used weeks build a streak`() {
        val weeks = listOf(week(mon, used = true), week(mon + 7, used = true), week(mon + 14))
        val stats = WeeklyEngine.stats(weeks, mon + 15, at(mon + 15))
        assertEquals(2, stats.current) // current week still open: doesn't break it
        assertEquals(2, stats.best)
        assertEquals(2, stats.totalUsed)
    }

    @Test
    fun `a missed week resets the streak but keeps the best`() {
        val weeks = listOf(
            week(mon, used = true), week(mon + 7, used = true),
            week(mon + 14), // missed
            week(mon + 21, used = true), week(mon + 28),
        )
        val stats = WeeklyEngine.stats(weeks, mon + 29, at(mon + 29))
        assertEquals(1, stats.current)
        assertEquals(2, stats.best)
    }

    @Test
    fun `back-filled wordless weeks count as missed`() {
        val weeks = listOf(week(mon, used = true), week(mon + 7, word = null), week(mon + 14))
        assertEquals(0, WeeklyEngine.stats(weeks, mon + 15, at(mon + 15)).current)
    }

    @Test
    fun `paused weeks neither break nor extend the streak`() {
        val weeks = listOf(week(mon, used = true), week(mon + 7, paused = true, word = null), week(mon + 14, used = true))
        val stats = WeeklyEngine.stats(weeks, mon + 15, at(mon + 15))
        assertEquals(2, stats.current)
        assertEquals(2, stats.totalUsed)
    }

    @Test
    fun `last week does not break the streak during its grace period`() {
        val weeks = listOf(week(mon, used = true), week(mon + 7), week(mon + 14))
        val monday = mon + 14
        assertEquals(1, WeeklyEngine.stats(weeks, monday, at(monday, 9)).current)
        assertEquals(0, WeeklyEngine.stats(weeks, monday, at(monday, 13)).current)
    }
}
