package dev.matejgroombridge.voquab.weekly

import android.content.Context
import dev.matejgroombridge.voquab.alarms.DailyAlarm
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Two daily alarms drive every weekly-word notification: a morning one (at
 * the user's announcement time) and an evening one (18:00). Each time they
 * fire, [WeeklyAlarmReceiver] looks at the current week and decides whether
 * anything is worth sending — usually nothing. Checking daily rather than
 * scheduling specific weekdays means changes to the week-start setting,
 * pauses and swaps never leave a stale alarm behind.
 */
object WeeklyAlarms {

    enum class Kind(val requestCode: Int, val action: String) {
        Morning(7_800_001, "dev.matejgroombridge.voquab.weekly.ALARM_MORNING"),
        Evening(7_800_002, "dev.matejgroombridge.voquab.weekly.ALARM_EVENING"),
    }

    val EVENING_TIME: LocalTime = LocalTime.of(18, 0)

    /** Cancels both alarms and re-arms them from current settings. Safe to call any time. */
    suspend fun rescheduleAll(context: Context) {
        Kind.entries.forEach { DailyAlarm.cancel(context, WeeklyAlarmReceiver::class.java, it.action, it.requestCode) }
        val settings = SettingsRepository(context).settings.first().weekly
        if (!settings.enabled) return
        val morning = DailyAlarm.parse(settings.announceTime, LocalTime.of(8, 0))
        schedule(context, Kind.Morning, morning)
        schedule(context, Kind.Evening, EVENING_TIME)
    }

    private fun schedule(context: Context, kind: Kind, time: LocalTime) =
        DailyAlarm.schedule(context, WeeklyAlarmReceiver::class.java, kind.action, kind.requestCode, time)
}
