package dev.matejgroombridge.voquab.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.voquab.data.settings.Settings
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.data.settings.WeekStart
import dev.matejgroombridge.voquab.ui.theme.ThemeMode
import dev.matejgroombridge.voquab.weekly.WeeklyAlarms
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { repository.setAmoled(enabled) }
    }

    fun setWeekStart(weekStart: WeekStart) {
        viewModelScope.launch { repository.setWeekStart(weekStart) }
    }

    fun setSwipeToNavigate(enabled: Boolean) {
        viewModelScope.launch { repository.setSwipeToNavigate(enabled) }
    }

    fun setWeeklyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWeeklyEnabled(enabled)
            WeeklyAlarms.rescheduleAll(appContext)
        }
    }

    fun setWeeklyAnnounceTime(time: String) {
        viewModelScope.launch {
            repository.setWeeklyAnnounceTime(time)
            WeeklyAlarms.rescheduleAll(appContext)
        }
    }

    fun setWeeklyReminders(enabled: Boolean) {
        // Reminders are decided at fire time, so the alarms needn't change.
        viewModelScope.launch { repository.setWeeklyReminders(enabled) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                SettingsViewModel(ctx, SettingsRepository(ctx))
            }
        }
    }
}
