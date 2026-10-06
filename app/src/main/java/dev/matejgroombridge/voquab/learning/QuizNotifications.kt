package dev.matejgroombridge.voquab.learning

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
import dev.matejgroombridge.voquab.data.model.CardKind
import dev.matejgroombridge.voquab.data.model.DailyCard
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.domain.MeetOutcome
import dev.matejgroombridge.voquab.domain.Question

/**
 * Today's cards as one-tap questions in the notification shade (PLAN.md
 * §3.3): up to three answer buttons, then the notification turns into the
 * result with a "Next card" button until the day's set is done.
 *
 * Recall cards (think-of-the-word) become "which word means…?" here, since
 * there's no honest way to self-grade from a notification.
 */
object QuizNotifications {

    private const val CHANNEL_ID = "quizzes"
    private const val CHANNEL_NAME = "Quizzes"
    const val ID = 4_200_001
    private const val RESULT_TIMEOUT_MS = 8_000L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            // Low importance: shows in the shade and on the lock screen, but
            // never buzzes — quizzes wait quietly to be noticed.
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Today's cards as one-tap questions."
            },
        )
    }

    fun card(context: Context, card: DailyCard, word: Word, question: Question?, remaining: Int) {
        val builder = base(context).setSubText("$remaining left today")
        if (card.kind == CardKind.Meet || question == null) {
            builder
                .setContentTitle("New word: ${word.term}")
                .setContentText(word.gloss)
                .setStyle(NotificationCompat.BigTextStyle().bigText(meetText(word)))
                .addAction(0, "Got it", meetIntent(context, word.id, MeetOutcome.GotIt, 0))
                .addAction(0, "Knew it", meetIntent(context, word.id, MeetOutcome.AlreadyKnew, 1))
        } else {
            val (title, body) = when (question.kind) {
                CardKind.PickMeaning, CardKind.Check ->
                    "${word.term} — which fits?" to (question.support?.replace("*", "") ?: word.term)
                CardKind.Cloze -> "Fill the gap" to question.prompt
                else -> "Which word means “${word.gloss}”?" to word.definition
            }
            builder.setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            question.options.forEachIndexed { i, option ->
                builder.addAction(0, option, answerIntent(context, word.id, i == question.correctIndex, i))
            }
        }
        post(context, builder)
    }

    /** Replaces the question with how it went, plus "Next card" while any are left. */
    fun result(context: Context, word: Word, title: String, remaining: Int) {
        val example = word.examples.firstOrNull()?.replace("*", "") ?: word.definition
        val builder = base(context)
            .setContentTitle(title)
            .setContentText(example)
            .setStyle(NotificationCompat.BigTextStyle().bigText(example))
        if (remaining > 0) builder.addAction(0, "Next card ($remaining left)", nextIntent(context))
        else builder.setSubText("That's today ✓").setTimeoutAfter(RESULT_TIMEOUT_MS)
        post(context, builder)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    fun titleFor(word: Word, correct: Boolean, graduated: Boolean): String = when {
        graduated -> "✨ Learned! ${word.term} — ${word.gloss}"
        correct -> "✓ ${word.term} — ${word.gloss}"
        else -> "✗ ${word.term} means “${word.gloss}”"
    }

    private fun meetText(word: Word): String = buildString {
        append(word.definition)
        word.examples.firstOrNull()?.let { append("\n\n").append(it.replace("*", "")) }
        word.hook?.let { append("\n\n").append(it) }
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openToday(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

    private fun post(context: Context, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(ID, builder.build())
    }

    private fun openToday(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TODAY)
        }
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun answerIntent(context: Context, wordId: String, correct: Boolean, slot: Int) =
        broadcast(context, QuizReceiver.ACTION_ANSWER, REQUEST_ANSWER + slot) {
            putExtra(QuizReceiver.EXTRA_WORD_ID, wordId)
            putExtra(QuizReceiver.EXTRA_CORRECT, correct)
        }

    private fun meetIntent(context: Context, wordId: String, outcome: MeetOutcome, slot: Int) =
        broadcast(context, QuizReceiver.ACTION_MEET, REQUEST_MEET + slot) {
            putExtra(QuizReceiver.EXTRA_WORD_ID, wordId)
            putExtra(QuizReceiver.EXTRA_OUTCOME, outcome.name)
        }

    private fun nextIntent(context: Context) = broadcast(context, QuizReceiver.ACTION_NEXT, REQUEST_NEXT) {}

    private fun broadcast(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, QuizReceiver::class.java).setAction(action).apply(extras)
        // A request code per button so one button's extras never overwrite another's.
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val REQUEST_ANSWER = 4_210_000
    private const val REQUEST_MEET = 4_220_000
    private const val REQUEST_NEXT = 4_230_000

}
