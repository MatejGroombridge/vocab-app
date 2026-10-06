package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyPickerTest {

    private fun c(id: String, conv: Int = 3, use: Int = 3, status: WordStatus = WordStatus.New) =
        PickCandidate(id, conversational = conv, usefulness = use, status = status)

    @Test
    fun `skips shelved, excluded and hard-to-say words`() {
        val pick = WeeklyPicker.pick(
            listOf(
                c("shelved", status = WordStatus.Shelved),
                c("excluded"),
                c("literary", conv = 1),
                c("ok", use = 1),
            ),
            exclude = setOf("excluded"),
            seed = 1,
        )
        assertEquals("ok", pick)
    }

    @Test
    fun `prefers words being learned over new and known ones`() {
        val candidates = listOf(
            c("known", status = WordStatus.Known),
            c("new"),
            c("learning", use = 1, status = WordStatus.Learning),
        )
        assertEquals("learning", WeeklyPicker.pick(candidates, emptySet(), seed = 1))
    }

    @Test
    fun `prefers the most useful words within a tier`() {
        val candidates = listOf(c("niche", use = 1), c("useful", use = 3), c("middling", use = 2))
        assertEquals("useful", WeeklyPicker.pick(candidates, emptySet(), seed = 7))
    }

    @Test
    fun `picks among equals and is repeatable for a seed`() {
        val candidates = (1..20).map { c("w$it") }
        val picks = (0L until 50L).map { WeeklyPicker.pick(candidates, emptySet(), it) }.toSet()
        assertTrue("expected variety, got $picks", picks.size > 5)
        assertEquals(
            WeeklyPicker.pick(candidates, emptySet(), 42),
            WeeklyPicker.pick(candidates, emptySet(), 42),
        )
    }

    @Test
    fun `falls back to literary words, then gives up`() {
        assertEquals("literary", WeeklyPicker.pick(listOf(c("literary", conv = 1)), emptySet(), 1))
        assertNull(WeeklyPicker.pick(listOf(c("only")), setOf("only"), 1))
    }
}
