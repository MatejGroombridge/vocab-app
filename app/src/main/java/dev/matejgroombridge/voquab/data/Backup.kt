package dev.matejgroombridge.voquab.data

import android.content.Context
import dev.matejgroombridge.voquab.data.model.WeeklyState
import dev.matejgroombridge.voquab.data.model.WordProgress
import dev.matejgroombridge.voquab.data.repository.CustomWords
import dev.matejgroombridge.voquab.data.repository.ProgressRepository
import dev.matejgroombridge.voquab.data.repository.WeeklyRepository
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.widget.Widgets
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything worth keeping in one JSON file: learning progress, the
 * weekly-word history, and words the user added. The bundled dictionary
 * isn't included — it ships with the app. Today's card set isn't either; it
 * just rebuilds.
 */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val progress: Map<String, WordProgress> = emptyMap(),
    val weekly: WeeklyState = WeeklyState(),
    val custom: CustomWords = CustomWords(),
)

class Backup(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun export(): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            progress = ProgressRepository(context).progress.first(),
            weekly = WeeklyRepository(context).snapshot(),
            custom = WordRepository(context).custom.first(),
        ),
    )

    /** Replaces current data with the backup. Returns the number of words with progress, or null if unreadable. */
    suspend fun restore(raw: String): Int? {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), raw) }.getOrNull() ?: return null
        ProgressRepository(context).replaceAll(file.progress)
        WeeklyRepository(context).update { file.weekly }
        WordRepository(context).replaceCustom(file.custom)
        Widgets.refresh(context)
        return file.progress.size
    }
}
