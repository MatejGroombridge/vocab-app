package dev.matejgroombridge.voquab.learning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.matejgroombridge.voquab.alarms.DailyAlarm
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.data.settings.QuizNotificationSettings
import dev.matejgroombridge.voquab.data.settings.SettingsRepository
import dev.matejgroombridge.voquab.domain.MeetOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Everything quiz-notification-shaped: the daily quiz alarms, the answer /
 * "Got it" / "Next card" buttons, and re-arming after a reboot or update.
 * Answers go through [ReviewService], so a card answered here is ticked off
 * in the app too. Not exported.
 */
class QuizReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ALARM -> {
                        if (QuizAlarms.enabled(app)) showNext(app)
                        QuizAlarms.rescheduleAll(app)
                    }
                    ACTION_ANSWER -> answer(app, intent)
                    ACTION_MEET -> meet(app, intent)
                    ACTION_NEXT -> showNext(app)
                    else -> QuizAlarms.rescheduleAll(app) // boot / package replaced
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showNext(context: Context) {
        val service = ReviewService(context)
        val (card, word) = service.nextCard() ?: return QuizNotifications.cancel(context)
        val remaining = service.today().cards.count { !it.answered }
        QuizNotifications.card(context, card, word, service.question(card, word), remaining)
    }

    private suspend fun answer(context: Context, intent: Intent) {
        val wordId = intent.getStringExtra(EXTRA_WORD_ID) ?: return
        val correct = intent.getBooleanExtra(EXTRA_CORRECT, false)
        val service = ReviewService(context)
        val result = service.answer(wordId, correct)
        if (!result.applied) return showNext(context) // already answered elsewhere
        val word = WordRepository(context).words().firstOrNull { it.id == wordId } ?: return
        val remaining = service.today().cards.count { !it.answered }
        QuizNotifications.result(context, word, QuizNotifications.titleFor(word, correct, result.graduated), remaining)
    }

    private suspend fun meet(context: Context, intent: Intent) {
        val wordId = intent.getStringExtra(EXTRA_WORD_ID) ?: return
        val outcome = intent.getStringExtra(EXTRA_OUTCOME)?.let { runCatching { MeetOutcome.valueOf(it) }.getOrNull() }
            ?: MeetOutcome.GotIt
        val service = ReviewService(context)
        if (!service.meet(wordId, outcome).applied) return showNext(context)
        val word = WordRepository(context).words().firstOrNull { it.id == wordId } ?: return
        val remaining = service.today().cards.count { !it.answered }
        val title = if (outcome == MeetOutcome.AlreadyKnew) "${word.term} — marked as known"
        else "${word.term} — first quiz tomorrow"
        QuizNotifications.result(context, word, title, remaining)
    }

    companion object {
        const val ACTION_ALARM = "dev.matejgroombridge.voquab.quiz.ALARM"
        const val ACTION_ANSWER = "dev.matejgroombridge.voquab.quiz.ANSWER"
        const val ACTION_MEET = "dev.matejgroombridge.voquab.quiz.MEET"
        const val ACTION_NEXT = "dev.matejgroombridge.voquab.quiz.NEXT"
        const val EXTRA_WORD_ID = "word_id"
        const val EXTRA_CORRECT = "correct"
        const val EXTRA_OUTCOME = "outcome"
    }
}

/**
 * Up to six daily quiz alarms, evenly spaced between the first and last
 * time. Each one just opens a "session" — the first unanswered card — and
 * the "Next card" button carries on from there. Passive mode forces them on.
 */
object QuizAlarms {

    private const val MAX_SLOTS = 6
    private const val REQUEST_BASE = 7_900_000

    suspend fun enabled(context: Context): Boolean {
        val s = SettingsRepository(context).settings.first()
        return s.quizzes.enabled || s.passiveMode
    }

    suspend fun rescheduleAll(context: Context) {
        for (slot in 0 until MAX_SLOTS) {
            DailyAlarm.cancel(context, QuizReceiver::class.java, QuizReceiver.ACTION_ALARM, REQUEST_BASE + slot)
        }
        if (!enabled(context)) return
        val settings = SettingsRepository(context).settings.first().quizzes
        times(settings).forEachIndexed { slot, time ->
            DailyAlarm.schedule(context, QuizReceiver::class.java, QuizReceiver.ACTION_ALARM, REQUEST_BASE + slot, time)
        }
    }

    /** Linear spacing: slot 0 at the first time, the last slot at the last time. */
    fun times(settings: QuizNotificationSettings): List<LocalTime> {
        val count = settings.timesPerDay.coerceIn(1, MAX_SLOTS)
        val first = DailyAlarm.parse(settings.firstTime, LocalTime.of(9, 0))
        val last = DailyAlarm.parse(settings.lastTime, LocalTime.of(20, 0))
        if (count == 1 || !last.isAfter(first)) return listOf(first)
        val firstMin = first.toSecondOfDay() / 60
        val step = (last.toSecondOfDay() / 60 - firstMin) / (count - 1)
        return (0 until count).map { LocalTime.ofSecondOfDay(((firstMin + step * it) * 60).toLong()) }
    }
}
