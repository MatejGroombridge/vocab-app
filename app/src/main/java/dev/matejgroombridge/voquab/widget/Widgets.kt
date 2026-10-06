package dev.matejgroombridge.voquab.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.matejgroombridge.voquab.MainActivity
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.data.repository.ProgressRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.domain.Day
import dev.matejgroombridge.voquab.domain.WeeklyEngine
import dev.matejgroombridge.voquab.ui.theme.Palette
import dev.matejgroombridge.voquab.weekly.WeeklyService
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId

/** Redraws every home-screen widget after data changes. Never throws. */
object Widgets {
    suspend fun refresh(context: Context) {
        runCatching { WordWidget().updateAll(context) }
        runCatching { WeeklyWidget().updateAll(context) }
    }
}

private fun sp(v: Float) = TextUnit(v, TextUnitType.Sp)

/**
 * "Word of the moment" (PLAN.md §3.4): one word you're learning, its short
 * meaning and an example, changing every few hours. Seeing it while
 * unlocking the phone is repetition without a review. Tap to open the app.
 */
class WordWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val word = pickWord(context)
        provideContent { GlanceTheme { WordBody(word) } }
    }

    private suspend fun pickWord(context: Context): Word? {
        val words = WordRepository(context).words()
        val progress = ProgressRepository(context).progress.first()
        val entries = words.map { WordEntry(it, progress[it.id]) }
        val learning = entries.filter { it.status == WordStatus.Learning }.map { it.word }
        val pool = learning.ifEmpty {
            entries.filter { it.status == WordStatus.New }.sortedByDescending { it.word.usefulness }.take(20).map { it.word }
        }
        if (pool.isEmpty()) return null
        // A new word every three hours, stable in between.
        val slot = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond() / ROTATE_SECONDS
        return pool.sortedBy { it.id }[(slot % pool.size).toInt()]
    }

    private companion object {
        const val ROTATE_SECONDS = 3 * 60 * 60L
    }
}

@Composable
private fun WordBody(word: Word?) {
    val palette = Palette.entry("butter")
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.light))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            val ink = ColorProvider(palette.onColor)
            if (word == null) {
                Text("Voquab", style = TextStyle(color = ink, fontSize = sp(16f), fontWeight = FontWeight.Medium))
                return@Column
            }
            Text(word.term, style = TextStyle(color = ink, fontSize = sp(22f), fontWeight = FontWeight.Bold), maxLines = 1)
            Text(word.gloss, style = TextStyle(color = ColorProvider(palette.onColor.copy(alpha = 0.75f)), fontSize = sp(14f)), maxLines = 1)
            word.examples.firstOrNull()?.let {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    it.replace("*", ""),
                    style = TextStyle(color = ink, fontSize = sp(13f)),
                    maxLines = 3,
                )
            }
        }
    }
}

/**
 * The weekly word on the home screen with the streak and a one-tap
 * "Used it" — logging a use without opening the app or the shade.
 */
class WeeklyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WeeklyService(context).snapshot()
        val week = snap.current
        val today = Day.today()
        val model = WeeklyModel(
            word = snap.currentWord,
            streak = snap.stats.current,
            paused = snap.state.paused && week?.used != true,
            used = week?.used == true,
            canLog = week != null && WeeklyEngine.canLog(week, today, LocalDateTime.now()),
            weekStart = week?.start,
        )
        provideContent { GlanceTheme { WeeklyBody(model) } }
    }
}

private data class WeeklyModel(
    val word: Word?,
    val streak: Int,
    val paused: Boolean,
    val used: Boolean,
    val canLog: Boolean,
    val weekStart: Long?,
)

@Composable
private fun WeeklyBody(m: WeeklyModel) {
    val palette = Palette.entry("lavender")
    val ink = ColorProvider(palette.onColor)
    val soft = ColorProvider(palette.onColor.copy(alpha = 0.7f))
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.light))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = "THIS WEEK" + if (m.streak > 0) "  🔥 ${m.streak}" else "",
                style = TextStyle(color = soft, fontSize = sp(11f), fontWeight = FontWeight.Medium),
            )
            when {
                m.paused -> Text("On a break", style = TextStyle(color = ink, fontSize = sp(18f), fontWeight = FontWeight.Bold))
                m.word == null -> Text("No word yet", style = TextStyle(color = ink, fontSize = sp(18f)))
                else -> {
                    Text(m.word.term, style = TextStyle(color = ink, fontSize = sp(22f), fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(m.word.gloss, style = TextStyle(color = soft, fontSize = sp(14f)), maxLines = 1)
                    Spacer(GlanceModifier.height(8.dp))
                    val start = m.weekStart
                    if (m.used) {
                        Text("✓ Used", style = TextStyle(color = ink, fontSize = sp(14f), fontWeight = FontWeight.Medium))
                    } else if (m.canLog && start != null) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(palette.accent))
                                .cornerRadius(14.dp)
                                .clickable(actionRunCallback<LogWeeklyUseAction>(LogWeeklyUseAction.params(start)))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("I used it", style = TextStyle(color = ink, fontSize = sp(14f), fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

/** The widget's "I used it": logs through the same service as the app and notifications. */
class LogWeeklyUseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val start = parameters[KEY_WEEK_START] ?: return
        WeeklyService(context).logUse(start)
        Widgets.refresh(context)
    }

    companion object {
        val KEY_WEEK_START = ActionParameters.Key<Long>("week_start")
        fun params(weekStart: Long): ActionParameters = actionParametersOf(KEY_WEEK_START to weekStart)
    }
}

class WordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WordWidget()
}

class WeeklyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyWidget()
}

