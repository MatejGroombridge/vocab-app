package dev.matejgroombridge.voquab.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.data.repository.ProgressRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.domain.Day
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** Every word, alphabetical by term. Empty until the asset has loaded. */
    val entries: List<WordEntry> = emptyList(),
    val todayEpochDay: Long = Day.today(),
    val loaded: Boolean = false,
) {
    fun count(status: WordStatus): Int = entries.count { it.status == status }
}

class LibraryViewModel(
    words: WordRepository,
    private val progress: ProgressRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        flow { emit(words.words()) },
        progress.progress,
    ) { all, progressById ->
        LibraryUiState(
            entries = all
                .map { WordEntry(it, progressById[it.id]) }
                .sortedBy { it.word.term.lowercase() },
            todayEpochDay = Day.today(),
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setStatus(wordId: String, status: WordStatus) {
        viewModelScope.launch { progress.setStatus(wordId, status) }
    }

    fun toggleUsedToday(wordId: String) {
        viewModelScope.launch { progress.toggleUse(wordId, Day.today()) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                LibraryViewModel(WordRepository(ctx), ProgressRepository(ctx))
            }
        }
    }
}
