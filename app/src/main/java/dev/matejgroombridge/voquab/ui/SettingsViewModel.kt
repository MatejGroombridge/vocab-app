package dev.matejgroombridge.voquab.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.voquab.data.Backup
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.data.settings.Settings
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.data.settings.WeekStart
import dev.matejgroombridge.voquab.learning.QuizAlarms
import dev.matejgroombridge.voquab.ui.theme.ThemeMode
import dev.matejgroombridge.voquab.weekly.WeeklyAlarms
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
    private val words: WordRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    /** Captured words still waiting for a definition. */
    val pendingWords: StateFlow<List<String>> = words.custom.map { it.pending }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }
    fun setAmoled(enabled: Boolean) = launch { repository.setAmoled(enabled) }
    fun setWeekStart(weekStart: WeekStart) = launch { repository.setWeekStart(weekStart) }
    fun setSwipeToNavigate(enabled: Boolean) = launch { repository.setSwipeToNavigate(enabled) }

    fun setWeeklyEnabled(enabled: Boolean) = launch {
        repository.setWeeklyEnabled(enabled)
        WeeklyAlarms.rescheduleAll(appContext)
    }

    fun setWeeklyAnnounceTime(time: String) = launch {
        repository.setWeeklyAnnounceTime(time)
        WeeklyAlarms.rescheduleAll(appContext)
    }

    // Reminders are decided at fire time, so the alarms needn't change.
    fun setWeeklyReminders(enabled: Boolean) = launch { repository.setWeeklyReminders(enabled) }

    fun setPassiveMode(enabled: Boolean) = launch {
        repository.setPassiveMode(enabled)
        QuizAlarms.rescheduleAll(appContext)
    }

    fun setQuizEnabled(enabled: Boolean) = launch {
        repository.setQuizEnabled(enabled)
        QuizAlarms.rescheduleAll(appContext)
    }

    fun setQuizTimes(n: Int) = launch {
        repository.setQuizTimes(n)
        QuizAlarms.rescheduleAll(appContext)
    }

    fun setQuizFirstTime(time: String) = launch {
        repository.setQuizFirstTime(time)
        QuizAlarms.rescheduleAll(appContext)
    }

    fun setQuizLastTime(time: String) = launch {
        repository.setQuizLastTime(time)
        QuizAlarms.rescheduleAll(appContext)
    }

    // Takes effect from tomorrow's set; today's is already built.
    fun setCardsPerDay(n: Int) = launch { repository.setCardsPerDay(n) }
    fun setNewPerDay(n: Int) = launch { repository.setNewPerDay(n) }

    fun clearPending() = launch { words.clearPending() }

    suspend fun exportBackup(): String = Backup(appContext).export()
    suspend fun restoreBackup(raw: String): Int? = Backup(appContext).restore(raw)

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                SettingsViewModel(ctx, SettingsRepository(ctx), WordRepository(ctx))
            }
        }
    }
}
