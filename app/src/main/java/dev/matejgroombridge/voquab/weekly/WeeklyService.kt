package dev.matejgroombridge.voquab.weekly

import android.content.Context
import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.WeeklyState
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordProgress
import dev.matejgroombridge.voquab.data.repository.ProgressRepository
import dev.matejgroombridge.voquab.data.repository.WeeklyRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.domain.Day
import dev.matejgroombridge.voquab.domain.PickCandidate
import dev.matejgroombridge.voquab.domain.StreakStats
import dev.matejgroombridge.voquab.domain.WeeklyEngine
import dev.matejgroombridge.voquab.domain.WeeklyPicker
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/** Everything a screen or notification needs to know about the weekly word right now. */
data class WeeklySnapshot(
    val state: WeeklyState,
    val current: WeekEntry?,
    val currentWord: Word?,
    /** Last week, if its word can still be logged (grace period). */
    val grace: WeekEntry?,
    val graceWord: Word?,
    val stats: StreakStats,
)

/**
 * The one place that applies [WeeklyEngine]'s rules to stored data. Shared by
 * the Week screen, the notification buttons and the daily alarms, so a use
 * logged from the notification shade behaves exactly like one logged in the
 * app (including marking the word Known).
 */
class WeeklyService(context: Context) {

    private val weekly = WeeklyRepository(context)
    private val progress = ProgressRepository(context)
    private val words = WordRepository(context)
    private val settings = SettingsRepository(context)

    /** Rolls the history forward to today (picking a word if a new week began). */
    suspend fun refresh(): WeeklySnapshot {
        val today = Day.today()
        val weekStart = settings.settings.first().weekStart
        val picker = picker()
        val state = weekly.update { WeeklyEngine.rollOver(it, today, weekStart) { exclude -> picker(exclude, today) } }
        return snapshot(state)
    }

    suspend fun snapshot(state: WeeklyState? = null): WeeklySnapshot {
        val s = state ?: weekly.snapshot()
        val today = Day.today()
        val now = LocalDateTime.now()
        val byId = words.words().associateBy { it.id }
        val current = WeeklyEngine.current(s, today)
        val grace = WeeklyEngine.graceWeek(s, today, now)
        return WeeklySnapshot(
            state = s,
            current = current,
            currentWord = current?.wordId?.let(byId::get),
            grace = grace,
            graceWord = grace?.wordId?.let(byId::get),
            stats = WeeklyEngine.stats(s.weeks, today, now),
        )
    }

    /**
     * Logs a real-life use of the word for the week starting [weekStart].
     * Returns false if that week can't be logged any more (already logged,
     * paused, or past its grace period).
     */
    suspend fun logUse(weekStart: Long): Boolean {
        val today = Day.today()
        var loggedWord: String? = null
        weekly.update { s ->
            s.copy(weeks = s.weeks.map { w ->
                if (w.start == weekStart && WeeklyEngine.canLog(w, today, LocalDateTime.now())) {
                    loggedWord = w.wordId
                    w.copy(usedOn = today)
                } else w
            })
        }
        loggedWord?.let { progress.setUsed(it, today, used = true) }
        return loggedWord != null
    }

    suspend fun undoUse(weekStart: Long) {
        var undone: WeekEntry? = null
        weekly.update { s ->
            s.copy(weeks = s.weeks.map { w ->
                if (w.start == weekStart && w.used) {
                    undone = w
                    w.copy(usedOn = null, note = null)
                } else w
            })
        }
        undone?.let { w -> if (w.wordId != null && w.usedOn != null) progress.setUsed(w.wordId, w.usedOn, used = false) }
    }

    suspend fun setNote(weekStart: Long, note: String) {
        val trimmed = note.trim().takeIf { it.isNotEmpty() }
        weekly.update { s -> s.copy(weeks = s.weeks.map { if (it.start == weekStart) it.copy(note = trimmed) else it }) }
    }

    /** Uses the week's one free re-roll. Returns false if it wasn't available. */
    suspend fun swap(): Boolean {
        val today = Day.today()
        val picker = picker()
        var swapped = false
        weekly.update { s ->
            val current = WeeklyEngine.current(s, today)
            if (current == null || !WeeklyEngine.canSwap(current)) return@update s
            // Seeded differently from the week's original pick so a swap
            // can't land on the same word even before exclusion.
            val replacement = picker(WeeklyEngine.usedWordIds(s.weeks), today + 1) ?: return@update s
            swapped = true
            s.copy(weeks = s.weeks.map {
                if (it.start == current.start) it.copy(wordId = replacement, swappedOut = current.wordId) else it
            })
        }
        return swapped
    }

    /**
     * Turns holiday mode on or off. Pausing freezes the current week unless
     * its word was already used; resuming gives the current week a word if
     * it was paused from the start.
     */
    suspend fun setPaused(paused: Boolean) {
        val today = Day.today()
        val picker = picker()
        weekly.update { s ->
            s.copy(paused = paused, weeks = s.weeks.map { w ->
                when {
                    !w.covers(today) || w.used -> w
                    paused -> w.copy(paused = true)
                    else -> w.copy(
                        paused = false,
                        wordId = w.wordId ?: picker(WeeklyEngine.usedWordIds(s.weeks), today),
                    )
                }
            })
        }
    }

    suspend fun markAnnounced(weekStart: Long) {
        weekly.update { s -> s.copy(weeks = s.weeks.map { if (it.start == weekStart) it.copy(announced = true) else it }) }
    }

    /** Snapshot of the words and their statuses, as a pick function seeded by day. */
    private suspend fun picker(): (Set<String>, Long) -> String? {
        val all = words.words()
        val progressById: Map<String, WordProgress> = progress.progress.first()
        val candidates = all.map { word ->
            PickCandidate(
                id = word.id,
                conversational = word.conversational,
                usefulness = word.usefulness,
                status = WordEntry(word, progressById[word.id]).status,
            )
        }
        return { exclude, seed -> WeeklyPicker.pick(candidates, exclude, seed) }
    }
}
