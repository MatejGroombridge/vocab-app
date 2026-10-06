package dev.matejgroombridge.voquab.learning

import android.content.Context
import dev.matejgroombridge.voquab.data.model.DailyCard
import dev.matejgroombridge.voquab.data.model.DailyState
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.data.repository.DailyRepository
import dev.matejgroombridge.voquab.data.repository.ProgressRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.domain.DailySet
import dev.matejgroombridge.voquab.domain.Day
import dev.matejgroombridge.voquab.domain.MeetOutcome
import dev.matejgroombridge.voquab.domain.Question
import dev.matejgroombridge.voquab.domain.Quiz
import dev.matejgroombridge.voquab.domain.Scheduler
import dev.matejgroombridge.voquab.widget.Widgets
import kotlinx.coroutines.flow.first

/** What happened when a card was answered. */
data class AnswerResult(
    /** False if the card was already answered elsewhere (e.g. the app and a notification raced). */
    val applied: Boolean,
    /** The answer moved the word to Known. */
    val graduated: Boolean = false,
)

/**
 * The one place that turns answers into progress (via [Scheduler]) and
 * keeps today's set (via [DailySet]). Shared by the Today tab, notification
 * quizzes and widgets, so a card answered anywhere counts everywhere.
 */
class ReviewService(private val context: Context) {

    private val progress = ProgressRepository(context)
    private val daily = DailyRepository(context)
    private val words = WordRepository(context)
    private val settings = SettingsRepository(context)

    /** Today's set, built on the first call of the day (4am rollover). */
    suspend fun today(): DailyState {
        val today = Day.today()
        val learning = settings.settings.first().learning
        return daily.update { current ->
            if (current.day == today) current
            else DailyState(today, DailySet.build(entries(), today, learning.cardsPerDay, learning.newPerDay))
        }
    }

    /** The next unanswered card with its word and question, or null when the day is done. */
    suspend fun nextCard(): Pair<DailyCard, Word>? {
        val card = today().next ?: return null
        val word = words.words().firstOrNull { it.id == card.wordId } ?: return null
        return card to word
    }

    suspend fun question(card: DailyCard, word: Word): Question? =
        Quiz.question(word, card.kind, pool(), Day.today())

    suspend fun answer(wordId: String, correct: Boolean): AnswerResult {
        val today = Day.today()
        if (!markAnswered(wordId, correct)) return AnswerResult(applied = false)
        var graduated = false
        progress.update(wordId) { p ->
            Scheduler.review(p, today, correct, seed = wordId.hashCode().toLong() + today).also {
                graduated = p.status != WordStatus.Known && it.status == WordStatus.Known
            }
        }
        Widgets.refresh(context)
        return AnswerResult(applied = true, graduated = graduated)
    }

    suspend fun meet(wordId: String, outcome: MeetOutcome): AnswerResult {
        if (!markAnswered(wordId, correct = true)) return AnswerResult(applied = false)
        progress.update(wordId) { Scheduler.meet(it, Day.today(), outcome) }
        Widgets.refresh(context)
        return AnswerResult(applied = true, graduated = outcome == MeetOutcome.AlreadyKnew)
    }

    /** Adds one bonus card after the day is done. Returns false if there's nothing left to offer. */
    suspend fun oneMore(): Boolean {
        val today = Day.today()
        val all = entries()
        var added = false
        daily.update { current ->
            val extra = DailySet.oneMore(all, today, current.cards.map { it.wordId }.toSet())
                ?: return@update current
            added = true
            current.copy(cards = current.cards + extra)
        }
        return added
    }

    private suspend fun markAnswered(wordId: String, correct: Boolean): Boolean {
        var marked = false
        daily.update { current ->
            val index = current.cards.indexOfFirst { it.wordId == wordId && !it.answered }
            if (index < 0) return@update current
            marked = true
            current.copy(cards = current.cards.toMutableList().also { it[index] = it[index].copy(correct = correct) })
        }
        return marked
    }

    private suspend fun entries(): List<WordEntry> {
        val byId = progress.progress.first()
        return words.words().map { WordEntry(it, byId[it.id]) }
    }

    /** Words wrong answers can be drawn from: anything not set aside. */
    private suspend fun pool(): List<Word> {
        val byId = progress.progress.first()
        return words.words().filter { WordEntry(it, byId[it.id]).status != WordStatus.Shelved }
    }
}
