package dev.matejgroombridge.voquab.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One-shot "next time it's HH:MM" alarms, shared by the weekly word and the
 * quiz notifications. Receivers re-arm after firing, so the pattern repeats
 * daily without the deprecated inexact-repeating API.
 */
object DailyAlarm {

    fun schedule(context: Context, receiver: Class<*>, action: String, requestCode: Int, time: LocalTime) {
        val now = LocalDateTime.now()
        // AlarmManager never fires early, so by the time a fired alarm
        // re-arms, today's slot is already in the past and rolls to
        // tomorrow. No safety margin: one would skip a whole day whenever
        // the app happened to reschedule in the minute before an alarm.
        var fireAt = now.toLocalDate().atTime(time)
        if (!fireAt.isAfter(now)) fireAt = fireAt.plusDays(1)
        val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val mgr = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, receiver, action, requestCode, create = true) ?: return
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

    fun cancel(context: Context, receiver: Class<*>, action: String, requestCode: Int) {
        val pi = pendingIntent(context, receiver, action, requestCode, create = false) ?: return
        ContextCompat.getSystemService(context, AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    fun parse(raw: String, fallback: LocalTime): LocalTime = runCatching { LocalTime.parse(raw) }.getOrDefault(fallback)

    private fun pendingIntent(
        context: Context,
        receiver: Class<*>,
        action: String,
        requestCode: Int,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, receiver).setAction(action)
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
