package dev.matejgroombridge.voquab.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.voquab.data.model.DailyCard
import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.data.repository.DailyRepository
import dev.matejgroombridge.voquab.data.repository.ProgressRepository
import dev.matejgroombridge.voquab.data.repository.WeeklyRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.domain.Day
import dev.matejgroombridge.voquab.domain.MeetOutcome
import dev.matejgroombridge.voquab.domain.Question
import dev.matejgroombridge.voquab.domain.Quiz
import dev.matejgroombridge.voquab.domain.WeeklyEngine
import dev.matejgroombridge.voquab.learning.AnswerResult
import dev.matejgroombridge.voquab.learning.ReviewService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The card on screen: its word, and the question for multiple-choice kinds. */
data class CurrentCard(
    val card: DailyCard,
    val word: Word,
    val question: Question?,
)

data class TodayUiState(
    val loaded: Boolean = false,
    val passive: Boolean = false,
    val cards: List<DailyCard> = emptyList(),
    val current: CurrentCard? = null,
    val weeklyWord: Word? = null,
    val weeklyWeek: WeekEntry? = null,
) {
    val answered: Int get() = cards.count { it.answered }
    val correct: Int get() = cards.count { it.correct == true }
    val done: Boolean get() = cards.isNotEmpty() && current == null
}

class TodayViewModel(
    private val service: ReviewService,
    daily: DailyRepository,
    progress: ProgressRepository,
    words: WordRepository,
    weekly: WeeklyRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        daily.state,
        progress.progress,
        words.all,
        weekly.state,
        settings.settings,
    ) { state, progressById, all, weeklyState, s ->
        val byId = all.associateBy { it.id }
        val today = Day.today()
        val pool = all.filter { WordEntry(it, progressById[it.id]).status != WordStatus.Shelved }
        val current = if (state.day == today) state.next?.let { card ->
            byId[card.wordId]?.let { word -> CurrentCard(card, word, Quiz.question(word, card.kind, pool, today)) }
        } else null
        val week = WeeklyEngine.current(weeklyState, today)
        TodayUiState(
            loaded = state.day == today,
            passive = s.passiveMode,
            cards = if (state.day == today) state.cards else emptyList(),
            current = current,
            weeklyWord = week?.wordId?.let(byId::get),
            weeklyWeek = week,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState(),
    )

    init {
        refresh()
    }

    /** Builds today's set if the day has rolled over. */
    fun refresh() {
        viewModelScope.launch { service.today() }
    }

    fun answer(wordId: String, correct: Boolean, onResult: (AnswerResult) -> Unit = {}) {
        viewModelScope.launch { onResult(service.answer(wordId, correct)) }
    }

    fun meet(wordId: String, outcome: MeetOutcome) {
        viewModelScope.launch { service.meet(wordId, outcome) }
    }

    fun oneMore() {
        viewModelScope.launch { service.oneMore() }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                TodayViewModel(
                    ReviewService(ctx), DailyRepository(ctx), ProgressRepository(ctx),
                    WordRepository(ctx), WeeklyRepository(ctx), SettingsRepository(ctx),
                )
            }
        }
    }
}
