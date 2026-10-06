package dev.matejgroombridge.voquab.weekly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.domain.Day
import dev.matejgroombridge.voquab.domain.WeeklyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires on the two daily [WeeklyAlarms] and decides what, if anything, to
 * post. Also restores the alarms after a reboot or an app update (both of
 * which clear AlarmManager).
 *
 *  Morning: roll the week over; announce the word once per week; remind
 *           about last week's word if it's still in its grace period.
 *  Evening: if the word is still unused, nudge mid-week and on the last day.
 */
class WeeklyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    WeeklyAlarms.Kind.Morning.action -> morning(appContext)
                    WeeklyAlarms.Kind.Evening.action -> evening(appContext)
                }
                // Boot / package-replaced land here too: just re-arm.
                WeeklyAlarms.rescheduleAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun morning(context: Context) {
        val settings = SettingsRepository(context).settings.first().weekly
        if (!settings.enabled) return
        val service = WeeklyService(context)
        val snap = service.refresh()

        if (settings.reminders && snap.grace != null && snap.graceWord != null) {
            WeeklyNotifications.grace(context, snap.grace, snap.graceWord)
        }

        val week = snap.current ?: return
        val word = snap.currentWord ?: return
        if (week.announced || week.paused || week.used) return
        WeeklyNotifications.announce(context, week, word, canSwap = WeeklyEngine.canSwap(week))
        service.markAnnounced(week.start)
    }

    private suspend fun evening(context: Context) {
        val settings = SettingsRepository(context).settings.first().weekly
        if (!settings.enabled || !settings.reminders) return
        val snap = WeeklyService(context).refresh()
        val week = snap.current ?: return
        val word = snap.currentWord ?: return
        if (week.used || week.paused) return

        val today = Day.today()
        when (today) {
            week.end -> WeeklyNotifications.reminder(context, week, word, lastDay = true, streak = snap.stats.current)
            // Mid-week: Thursday for a Monday-start week.
            week.start + MID_WEEK_OFFSET -> WeeklyNotifications.reminder(context, week, word, lastDay = false, streak = snap.stats.current)
        }
    }

    private companion object {
        const val MID_WEEK_OFFSET = 3L
    }
}
