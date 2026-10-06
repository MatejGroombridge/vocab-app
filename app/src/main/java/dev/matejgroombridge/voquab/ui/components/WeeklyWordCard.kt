package dev.matejgroombridge.voquab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.ui.theme.Palette
import dev.matejgroombridge.voquab.ui.theme.containerColor
import dev.matejgroombridge.voquab.ui.theme.contentColor
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The weekly word always wears Lavender, so it reads as "this week" anywhere in the app. */
val WeeklyPalette = Palette.entry("lavender")

/**
 * The hero card on the This Week tab: the word, what it means, and one or
 * two ready-to-say lines so using it doesn't depend on inspiration. One big
 * "I used it" button; once logged it shows when, plus the optional note.
 */
@Composable
fun WeeklyWordCard(
    week: WeekEntry,
    word: Word,
    today: Long,
    canLog: Boolean,
    canSwap: Boolean,
    onSpeak: () -> Unit,
    onLogUse: () -> Unit,
    onSwap: () -> Unit,
    onEditNote: () -> Unit,
    onUndo: () -> Unit,
) {
    val container = WeeklyPalette.containerColor()
    val content = WeeklyPalette.contentColor()

    Surface(shape = RoundedCornerShape(24.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "THIS WEEK'S WORD",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeLeft(week, today),
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = word.term,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word.pos,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.75f),
                )
                IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "Pronounce ${word.term}",
                        tint = content.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(text = word.definition, style = MaterialTheme.typography.bodyLarge, color = content)

            val lines = word.openers.ifEmpty { word.examples.map { it.replace("*", "") } }.take(2)
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Ways to use it",
                    style = MaterialTheme.typography.labelLarge,
                    color = content.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { line ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = content.copy(alpha = 0.07f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "“$line”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = content,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            val usedOn = week.usedOn
            if (usedOn != null) {
                UsedFooter(usedOn = usedOn, note = week.note, contentColor = content, onEditNote = onEditNote, onUndo = onUndo)
            } else {
                Button(
                    onClick = onLogUse,
                    enabled = canLog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WeeklyPalette.accent,
                        contentColor = WeeklyPalette.onColor,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("I used it", style = MaterialTheme.typography.titleMedium)
                }
                if (canSwap) {
                    TextButton(onClick = onSwap, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Swap for another word", color = content.copy(alpha = 0.75f))
                    }
                }
            }
        }
    }
}

@Composable
private fun UsedFooter(
    usedOn: Long,
    note: String?,
    contentColor: androidx.compose.ui.graphics.Color,
    onEditNote: () -> Unit,
    onUndo: () -> Unit,
) {
    val day = LocalDate.ofEpochDay(usedOn).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Text(
        text = "✓ Used on $day",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
    )
    if (note != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = contentColor.copy(alpha = 0.8f),
        )
    }
    Row {
        TextButton(onClick = onEditNote) {
            Text(if (note == null) "Add a note" else "Edit note", color = contentColor.copy(alpha = 0.75f))
        }
        TextButton(onClick = onUndo) {
            Text("Undo", color = contentColor.copy(alpha = 0.75f))
        }
    }
}

private fun timeLeft(week: WeekEntry, today: Long): String {
    if (week.used) return "Done ✓"
    return when (val left = week.end - today) {
        0L -> "Last day"
        1L -> "1 day left"
        else -> "$left days left"
    }
}
