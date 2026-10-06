package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.CardKind
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordProgress
import dev.matejgroombridge.voquab.data.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    private val today = 20_000L
    private fun learning(stage: Int, lapses: Int = 0) =
        WordProgress(status = WordStatus.Learning, stage = stage, lapses = lapses, dueDay = today)

    @Test
    fun `meeting a word schedules the first card tomorrow`() {
        val p = Scheduler.meet(WordProgress(), today, MeetOutcome.GotIt)
        assertEquals(WordStatus.Learning, p.status)
        assertEquals(1, p.stage)
        assertEquals(today + 1, p.dueDay)
        assertEquals(today, p.introducedOn)
    }

    @Test
    fun `already knew it and not worth learning skip the ladder`() {
        assertEquals(WordStatus.Known, Scheduler.meet(WordProgress(), today, MeetOutcome.AlreadyKnew).status)
        val shelved = Scheduler.meet(WordProgress(), today, MeetOutcome.NotWorthLearning)
        assertEquals(WordStatus.Shelved, shelved.status)
        assertNull(shelved.dueDay)
    }

    @Test
    fun `a hit climbs one stage with a growing interval`() {
        for (stage in 1..4) {
            val p = Scheduler.review(learning(stage), today, correct = true, seed = 1)
            assertEquals(stage + 1, p.stage)
            val gap = p.dueDay!! - today
            val expected = Scheduler.INTERVALS[stage]
            assertTrue("stage $stage gap $gap", gap in (expected * 0.84).toLong()..(expected * 1.16).toLong() + 1)
        }
    }

    @Test
    fun `a hit on the last stage graduates the word`() {
        val p = Scheduler.review(learning(Scheduler.LAST_STAGE), today, correct = true)
        assertEquals(WordStatus.Known, p.status)
        assertTrue(p.dueDay!! - today > 90)
    }

    @Test
    fun `a miss drops a stage and comes back tomorrow, never today`() {
        val p = Scheduler.review(learning(3), today, correct = false)
        assertEquals(2, p.stage)
        assertEquals(today + 1, p.dueDay)
        assertEquals(1, p.lapses)
    }

    @Test
    fun `two misses in a row go back to the meet card`() {
        val p = Scheduler.review(learning(3, lapses = 1), today, correct = false)
        assertEquals(0, p.stage)
        assertEquals(0, p.lapses)
    }

    @Test
    fun `a hit clears lapses`() {
        assertEquals(0, Scheduler.review(learning(2, lapses = 1), today, correct = true).lapses)
    }

    @Test
    fun `missing a known word sends it back to the fill-in-the-blank stage`() {
        val known = WordProgress(status = WordStatus.Known, dueDay = today)
        val p = Scheduler.review(known, today, correct = false)
        assertEquals(WordStatus.Learning, p.status)
        assertEquals(Scheduler.RELEARN_STAGE, p.stage)
    }

    @Test
    fun `fuzz stays within 15 percent and is repeatable`() {
        val values = (0L until 200L).map { Scheduler.fuzz(100, it) }
        assertTrue(values.all { it in 85..115 })
        assertTrue(values.toSet().size > 10)
        assertEquals(Scheduler.fuzz(16, 7), Scheduler.fuzz(16, 7))
    }
}

class DailySetTest {

    private val today = 20_000L

    private fun word(id: String, use: Int = 2, pos: String = "noun") = Word(
        id = id, term = id, pos = pos, gloss = "$id-gloss", definition = "def",
        examples = listOf("A *$id* here."), usefulness = use, conversational = 2,
    )

    private fun entry(id: String, status: WordStatus = WordStatus.New, stage: Int = 0, due: Long? = null, use: Int = 2) =
        WordEntry(word(id, use), if (status == WordStatus.New) null else WordProgress(status, stage = stage, dueDay = due))

    @Test
    fun `due words come first, most overdue first`() {
        val set = DailySet.build(
            listOf(entry("late", WordStatus.Learning, 2, today - 5), entry("now", WordStatus.Learning, 1, today), entry("new")),
            today, cardsPerDay = 5, newPerDay = 1,
        )
        assertEquals(listOf("late", "now", "new"), set.map { it.wordId })
    }

    @Test
    fun `never more than the daily cap, however long you were away`() {
        val overdue = (1..30).map { entry("w$it", WordStatus.Learning, 2, today - 5) }
        val set = DailySet.build(overdue + entry("fresh"), today, cardsPerDay = 5, newPerDay = 1)
        assertEquals(5, set.size)
        assertFalse(set.any { it.wordId == "fresh" })
    }

    @Test
    fun `new words are capped per day and most useful first`() {
        val set = DailySet.build(
            listOf(entry("meh", use = 1), entry("great", use = 3), entry("ok", use = 2)),
            today, cardsPerDay = 5, newPerDay = 2,
        )
        assertEquals(listOf("great", "ok"), set.map { it.wordId })
        assertTrue(set.all { it.kind == CardKind.Meet })
    }

    @Test
    fun `no new words while too many are early in learning`() {
        val busy = (1..DailySet.LEARNING_LOAD_CAP).map { entry("l$it", WordStatus.Learning, 1, today + 3) }
        assertTrue(DailySet.build(busy + entry("fresh"), today, cardsPerDay = 5, newPerDay = 1).isEmpty())
    }

    @Test
    fun `words not yet due are left alone`() {
        val set = DailySet.build(listOf(entry("later", WordStatus.Learning, 2, today + 1)), today, 5, newPerDay = 0)
        assertTrue(set.isEmpty())
    }

    @Test
    fun `card kind follows the stage`() {
        val kinds = (0..5).map { DailySet.kindFor(entry("x", WordStatus.Learning, it)) }
        assertEquals(
            listOf(CardKind.Meet, CardKind.PickMeaning, CardKind.PickWord, CardKind.Cloze, CardKind.Recall, CardKind.Recall),
            kinds,
        )
        assertEquals(CardKind.Check, DailySet.kindFor(entry("k", WordStatus.Known)))
    }

    @Test
    fun `one more offers the next due word, then a new one`() {
        val entries = listOf(entry("due", WordStatus.Learning, 1, today), entry("new"))
        assertEquals("due", DailySet.oneMore(entries, today, emptySet())?.wordId)
        assertEquals("new", DailySet.oneMore(entries, today, setOf("due"))?.wordId)
        assertNull(DailySet.oneMore(entries, today, setOf("due", "new")))
    }
}

class QuizTest {

    private fun w(id: String, pos: String = "adjective", gloss: String = "$id-gloss") = Word(
        id = id, term = id, pos = pos, gloss = gloss, definition = "$id def", examples = listOf("So *$id* today."),
    )

    private val pool = listOf(w("adroit"), w("droll"), w("gauche"), w("deluge", pos = "noun"), w("credo", pos = "noun"))

    @Test
    fun `three options with the right one marked`() {
        val q = Quiz.question(pool[0], CardKind.PickMeaning, pool, day = 1)!!
        assertEquals(3, q.options.size)
        assertEquals("adroit-gloss", q.options[q.correctIndex])
        assertEquals(3, q.options.toSet().size)
    }

    @Test
    fun `wrong answers share the part of speech when possible`() {
        val q = Quiz.question(pool[0], CardKind.PickWord, pool, day = 1)!!
        assertEquals(setOf("adroit", "droll", "gauche"), q.options.toSet())
    }

    @Test
    fun `options are stable for a day`() {
        assertEquals(
            Quiz.question(pool[0], CardKind.PickWord, pool, day = 9)!!.options,
            Quiz.question(pool[0], CardKind.PickWord, pool, day = 9)!!.options,
        )
    }

    @Test
    fun `adding an unrelated word doesn't reshuffle a card`() {
        val big = pool + (1..30).map { w("extra$it", pos = "noun") }
        val before = Quiz.question(pool[0], CardKind.PickWord, big, day = 3)!!.options
        val after = Quiz.question(pool[0], CardKind.PickWord, big + w("another", pos = "noun"), day = 3)!!.options
        assertEquals(before, after)
    }

    @Test
    fun `cloze blanks the marked word`() {
        val q = Quiz.question(pool[0], CardKind.Cloze, pool, day = 1)!!
        assertEquals("So _____ today.", q.prompt)
    }

    @Test
    fun `meet cards have no question`() {
        assertNull(Quiz.question(pool[0], CardKind.Meet, pool, day = 1))
    }
}
