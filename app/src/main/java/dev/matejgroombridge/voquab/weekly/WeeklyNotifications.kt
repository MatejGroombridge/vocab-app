package dev.matejgroombridge.voquab.weekly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.matejgroombridge.voquab.MainActivity
import dev.matejgroombridge.voquab.R
import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.Word

/**
 * Builds and posts the weekly-word notifications (PLAN.md §3.2). Each one
 * that asks for action carries an "I used it" (or "Swap") button handled by
 * [WeeklyActionReceiver], so the whole weekly loop works from the shade.
 */
object WeeklyNotifications {

    const val CHANNEL_ID = "weekly_word"
    private const val CHANNEL_NAME = "Weekly word"

    const val ID_ANNOUNCE = 4_100_001
    const val ID_REMINDER = 4_100_002
    const val ID_GRACE = 4_100_003

    /** How long the "logged ✓" confirmation lingers before clearing itself. */
    private const val CONFIRMATION_TIMEOUT_MS = 6_000L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "This week's word, plus a nudge if you haven't used it yet."
            },
        )
    }

    /** Week start: introduce the word with one ready-to-say line. */
    fun announce(context: Context, week: WeekEntry, word: Word, canSwap: Boolean) {
        val line = opener(word, 0)
        val builder = base(context)
            .setContentTitle("This week's word: ${word.term}")
            .setContentText("${word.gloss} — try: “$line”")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${word.definition}\n\nTry: “$line”\n\nUse it once in conversation this week.",
                ),
            )
        if (canSwap) {
            builder.addAction(0, "Swap word", actionIntent(context, WeeklyActionReceiver.ACTION_SWAP, week, ID_ANNOUNCE))
        }
        post(context, ID_ANNOUNCE, builder)
    }

    /** Mid-week nudge or last-day call — only sent while the word is unused. */
    fun reminder(context: Context, week: WeekEntry, word: Word, lastDay: Boolean, streak: Int) {
        val line = opener(word, if (lastDay) 2 else 1)
        val title = if (lastDay) "Last day for “${word.term}”" else "Used “${word.term}” yet?"
        val lead = when {
            lastDay && streak > 0 -> "Keep your $streak-week streak going. "
            lastDay -> "Today's your chance. "
            else -> ""
        }
        val builder = base(context)
            .setContentTitle(title)
            .setContentText("${lead}Try: “$line”")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${lead}Try: “$line”\n\n${word.term} — ${word.gloss}"))
            .addAction(0, "I used it", actionIntent(context, WeeklyActionReceiver.ACTION_LOG_USE, week, ID_REMINDER))
        post(context, ID_REMINDER, builder)
    }

    /** Morning after the week ends: last chance to log before the noon deadline. */
    fun grace(context: Context, week: WeekEntry, word: Word) {
        val builder = base(context)
            .setContentTitle("Did you use “${word.term}” last week?")
            .setContentText("Log it before noon to keep your streak.")
            .addAction(0, "I used it", actionIntent(context, WeeklyActionReceiver.ACTION_LOG_USE, week, ID_GRACE))
        post(context, ID_GRACE, builder)
    }

    /** Replaces the tapped notification with a short-lived confirmation. */
    fun logged(context: Context, notificationId: Int, word: Word, streak: Int) {
        val builder = base(context)
            .setContentTitle("Nice — “${word.term}” logged ✓")
            .setContentText(if (streak > 0) "🔥 $streak-week streak" else "Streak started.")
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
        post(context, notificationId, builder)
    }

    fun cancel(context: Context, vararg ids: Int) {
        val mgr = NotificationManagerCompat.from(context)
        ids.forEach(mgr::cancel)
    }

    /**
     * The word's [index]th conversation opener, cycling if there are fewer.
     * Falls back to an example sentence for the rare word with no openers.
     */
    private fun opener(word: Word, index: Int): String {
        val lines = word.openers.ifEmpty { word.examples.map { it.replace("*", "") } }
        return if (lines.isEmpty()) word.definition else lines[index % lines.size]
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openWeekTab(context))
            .setAutoCancel(true)
            // Re-posts (a swap, a repeat alarm) update in place without buzzing again.
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Words aren't private — show them on the lock screen so a glance is enough.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun openWeekTab(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEEK)
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(context: Context, action: String, week: WeekEntry, notificationId: Int): PendingIntent {
        val intent = Intent(context, WeeklyActionReceiver::class.java).apply {
            this.action = action
            putExtra(WeeklyActionReceiver.EXTRA_WEEK_START, week.start)
            putExtra(WeeklyActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        // Request code per notification so the extras of one never overwrite another's.
        return PendingIntent.getBroadcast(
            context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
