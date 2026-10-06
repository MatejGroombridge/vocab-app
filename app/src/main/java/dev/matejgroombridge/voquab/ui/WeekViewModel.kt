package dev.matejgroombridge.voquab.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.data.repository.WeeklyRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.domain.Day
import dev.matejgroombridge.voquab.domain.StreakStats
import dev.matejgroombridge.voquab.domain.WeeklyEngine
import dev.matejgroombridge.voquab.weekly.WeeklyService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** One past week as the history list shows it. */
data class PastWeek(val week: WeekEntry, val word: Word?)

data class WeekUiState(
    val loaded: Boolean = false,
    val today: Long = Day.today(),
    val paused: Boolean = false,
    val current: WeekEntry? = null,
    val currentWord: Word? = null,
    val canLogCurrent: Boolean = false,
    val canSwap: Boolean = false,
    /** Last week, while its word can still be logged before the noon deadline. */
    val grace: WeekEntry? = null,
    val graceWord: Word? = null,
    val stats: StreakStats = StreakStats(),
    /** Every week before the current one, newest first. */
    val history: List<PastWeek> = emptyList(),
)

class WeekViewModel(
    private val service: WeeklyService,
    weekly: WeeklyRepository,
    words: WordRepository,
) : ViewModel() {

    val uiState: StateFlow<WeekUiState> = combine(
        weekly.state,
        flow { emit(words.words().associateBy { it.id }) },
    ) { state, byId ->
        val today = Day.today()
        val now = LocalDateTime.now()
        val current = WeeklyEngine.current(state, today)
        val grace = WeeklyEngine.graceWeek(state, today, now)
        WeekUiState(
            loaded = true,
            today = today,
            paused = state.paused,
            current = current,
            currentWord = current?.wordId?.let(byId::get),
            canLogCurrent = current != null && WeeklyEngine.canLog(current, today, now),
            canSwap = current != null && WeeklyEngine.canSwap(current),
            grace = grace,
            graceWord = grace?.wordId?.let(byId::get),
            stats = WeeklyEngine.stats(state.weeks, today, now),
            history = state.weeks
                .filter { current == null || it.start < current.start }
                .reversed()
                .map { PastWeek(it, it.wordId?.let(byId::get)) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeekUiState(),
    )

    init {
        refresh()
    }

    /** Rolls the week over if needed. Called on start and whenever the app resumes. */
    fun refresh() {
        viewModelScope.launch { service.refresh() }
    }

    /** Logs a use; [onLogged] runs only if it actually counted (for confetti + the note prompt). */
    fun logUse(weekStart: Long, onLogged: () -> Unit) {
        viewModelScope.launch { if (service.logUse(weekStart)) onLogged() }
    }

    fun undoUse(weekStart: Long) {
        viewModelScope.launch { service.undoUse(weekStart) }
    }

    fun saveNote(weekStart: Long, note: String) {
        viewModelScope.launch { service.setNote(weekStart, note) }
    }

    fun swap() {
        viewModelScope.launch { service.swap() }
    }

    fun setPaused(paused: Boolean) {
        viewModelScope.launch { service.setPaused(paused) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                WeekViewModel(WeeklyService(ctx), WeeklyRepository(ctx), WordRepository(ctx))
            }
        }
    }
}
