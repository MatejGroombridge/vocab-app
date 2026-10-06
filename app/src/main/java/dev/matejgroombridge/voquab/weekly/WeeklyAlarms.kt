package dev.matejgroombridge.voquab.weekly

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Two daily alarms drive every weekly-word notification: a morning one (at
 * the user's announcement time) and an evening one (18:00). Each time they
 * fire, [WeeklyAlarmReceiver] looks at the current week and decides whether
 * anything is worth sending — usually nothing. Checking daily rather than
 * scheduling specific weekdays means changes to the week-start setting,
 * pauses and swaps never leave a stale alarm behind.
 *
 * Alarms are one-shot; the receiver re-arms them after each firing.
 */
object WeeklyAlarms {

    enum class Kind(val requestCode: Int, val action: String) {
        Morning(7_800_001, "dev.matejgroombridge.voquab.weekly.ALARM_MORNING"),
        Evening(7_800_002, "dev.matejgroombridge.voquab.weekly.ALARM_EVENING"),
    }

    val EVENING_TIME: LocalTime = LocalTime.of(18, 0)

    /** Cancels both alarms and re-arms them from current settings. Safe to call any time. */
    suspend fun rescheduleAll(context: Context) {
        Kind.entries.forEach { cancel(context, it) }
        val settings = SettingsRepository(context).settings.first().weekly
        if (!settings.enabled) return
        val morning = runCatching { LocalTime.parse(settings.announceTime) }.getOrDefault(LocalTime.of(8, 0))
        schedule(context, Kind.Morning, morning)
        schedule(context, Kind.Evening, EVENING_TIME)
    }

    private fun schedule(context: Context, kind: Kind, time: LocalTime) {
        val now = LocalDateTime.now()
        // AlarmManager never fires early, so by the time a fired alarm
        // re-arms, today's slot is already in the past and rolls to
        // tomorrow. No safety margin: one would skip a whole day whenever
        // the app happened to reschedule in the minute before an alarm.
        var fireAt = now.toLocalDate().atTime(time)
        if (!fireAt.isAfter(now)) fireAt = fireAt.plusDays(1)
        val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val mgr = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, kind, create = true) ?: return
        // Exact when the OS allows it (USE_EXACT_ALARM on 13+), otherwise
        // inexact-while-idle — a few minutes late is fine for a word.
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || mgr.canScheduleExactAlarms()) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure {
            mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancel(context: Context, kind: Kind) {
        val pi = pendingIntent(context, kind, create = false) ?: return
        ContextCompat.getSystemService(context, AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(context: Context, kind: Kind, create: Boolean): PendingIntent? {
        val intent = Intent(context, WeeklyAlarmReceiver::class.java).setAction(kind.action)
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, kind.requestCode, intent, flags)
    }
}
