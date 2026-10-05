package dev.matejgroombridge.voquab.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.voquab.data.settings.Settings
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.data.settings.WeekStart
import dev.matejgroombridge.voquab.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
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

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
