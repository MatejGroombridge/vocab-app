package dev.matejgroombridge.voquab.weekly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.matejgroombridge.voquab.data.repository.WordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the buttons on weekly-word notifications — "I used it" and
 * "Swap word" — without opening the app. Not exported; only our own
 * PendingIntents can reach it.
 */
class WeeklyActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, WeeklyNotifications.ID_REMINDER)
        val weekStart = intent.getLongExtra(EXTRA_WEEK_START, Long.MIN_VALUE)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_LOG_USE -> logUse(appContext, weekStart, notificationId)
                    ACTION_SWAP -> swap(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun logUse(context: Context, weekStart: Long, notificationId: Int) {
        val service = WeeklyService(context)
        if (!service.logUse(weekStart)) {
            // Stale button (already logged in-app, or past the deadline).
            WeeklyNotifications.cancel(context, notificationId)
            return
        }
        val snap = service.snapshot()
        val word = snap.state.weeks.firstOrNull { it.start == weekStart }?.wordId
            ?.let { id -> WordRepository(context).words().firstOrNull { it.id == id } }
        // Other notifications about this word are moot now. A grace-period
        // log is about last week's word, so this week's announcement stays.
        if (notificationId != WeeklyNotifications.ID_GRACE) {
            WeeklyNotifications.cancel(
                context,
                *listOf(WeeklyNotifications.ID_ANNOUNCE, WeeklyNotifications.ID_REMINDER)
                    .filter { it != notificationId }.toIntArray(),
            )
        }
        if (word != null) WeeklyNotifications.logged(context, notificationId, word, snap.stats.current)
        else WeeklyNotifications.cancel(context, notificationId)
    }

    private suspend fun swap(context: Context) {
        val service = WeeklyService(context)
        service.swap()
        val snap = service.snapshot()
        val week = snap.current
        val word = snap.currentWord
        // Re-post with the new word; the swap button disappears because
        // the week's one re-roll is now spent.
        if (week != null && word != null) WeeklyNotifications.announce(context, week, word, canSwap = false)
    }

    companion object {
        const val ACTION_LOG_USE = "dev.matejgroombridge.voquab.weekly.LOG_USE"
        const val ACTION_SWAP = "dev.matejgroombridge.voquab.weekly.SWAP"
        const val EXTRA_WEEK_START = "week_start"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
