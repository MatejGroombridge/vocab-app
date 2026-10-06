package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.CardKind
import dev.matejgroombridge.voquab.data.model.DailyCard
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus

/**
 * Builds the day's small, fixed set of cards (PLAN.md §3.1):
 *  1. Words that are due, most overdue first — but never more than
 *     [cardsPerDay]. Anything beyond the cap quietly waits for tomorrow;
 *     there is no backlog count.
 *  2. Then new words if there's room, most useful first, up to
 *     [newPerDay] — unless too many words are already early in learning, so
 *     the workload can't snowball.
 */
object DailySet {

    /** No new words while this many are still in the early stages (0–3). */
    const val LEARNING_LOAD_CAP = 20

    fun build(entries: List<WordEntry>, today: Long, cardsPerDay: Int, newPerDay: Int): List<DailyCard> {
        val due = dueEntries(entries, today).take(cardsPerDay)
        val room = cardsPerDay - due.size
        val load = entries.count { it.status == WordStatus.Learning && it.stage <= 3 }
        val newCount = if (load >= LEARNING_LOAD_CAP) 0 else minOf(room, newPerDay).coerceAtLeast(0)
        val fresh = newWords(entries).take(newCount)
        return (due + fresh).map { DailyCard(it.word.id, kindFor(it)) }
    }

    /** One extra card after the day is done: the next due word, else a new one. */
    fun oneMore(entries: List<WordEntry>, today: Long, exclude: Set<String>): DailyCard? {
        val candidate = dueEntries(entries, today).firstOrNull { it.word.id !in exclude }
            ?: newWords(entries).firstOrNull { it.word.id !in exclude }
            ?: return null
        return DailyCard(candidate.word.id, kindFor(candidate), bonus = true)
    }

    fun kindFor(entry: WordEntry): CardKind {
        val hasExample = entry.word.examples.isNotEmpty()
        return when (entry.status) {
            WordStatus.New, WordStatus.Shelved -> CardKind.Meet
            WordStatus.Known -> CardKind.Check
            WordStatus.Learning -> when (entry.stage) {
                0 -> CardKind.Meet
                1 -> CardKind.PickMeaning
                2 -> CardKind.PickWord
                3 -> if (hasExample) CardKind.Cloze else CardKind.PickWord
                else -> CardKind.Recall
            }
        }
    }

    private fun dueEntries(entries: List<WordEntry>, today: Long): List<WordEntry> = entries
        .filter { (it.status == WordStatus.Learning || it.status == WordStatus.Known) }
        .filter { e -> e.progress?.dueDay?.let { it <= today } == true }
        .sortedWith(compareBy({ it.progress?.dueDay }, { it.stage }, { it.word.id }))

    private fun newWords(entries: List<WordEntry>): List<WordEntry> = entries
        .filter { it.status == WordStatus.New }
        .sortedWith(
            compareByDescending<WordEntry> { it.word.usefulness }
                .thenByDescending { it.word.conversational }
                .thenBy { it.word.id },
        )
}
