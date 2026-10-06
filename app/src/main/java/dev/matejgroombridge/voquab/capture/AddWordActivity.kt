package dev.matejgroombridge.voquab.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.matejgroombridge.voquab.data.repository.WordRepository
import dev.matejgroombridge.voquab.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * "Add to Voquab" in the text-selection menu of Chrome and most other apps
 * (Android's PROCESS_TEXT), and in the share sheet. Shows one small dialog
 * over whatever app the user was in, looks the word up, and saves it as a
 * new word. If no definition is found it's kept on a "waiting" list that can
 * be shared from Settings to be written up properly.
 */
class AddWordActivity : ComponentActivity() {

    private sealed interface Step {
        data class Confirm(val term: String) : Step
        data object Looking : Step
        data class Done(val message: String) : Step
    }

    private var step by mutableStateOf<Step?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val term = selectedText(intent)
        if (term == null) {
            finish()
            return
        }
        step = Step.Confirm(term)
        setContent {
            AppTheme {
                when (val s = step) {
                    is Step.Confirm -> AlertDialog(
                        onDismissRequest = ::finish,
                        title = { Text("Add “${s.term}” to Voquab?") },
                        text = { Text("It'll join your new words and come up in your daily cards.") },
                        confirmButton = { TextButton(onClick = { add(s.term) }) { Text("Add") } },
                        dismissButton = { TextButton(onClick = ::finish) { Text("Cancel") } },
                    )
                    Step.Looking -> AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Looking it up…") },
                        text = {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        },
                        confirmButton = {},
                    )
                    is Step.Done -> AlertDialog(
                        onDismissRequest = ::finish,
                        text = { Text(s.message, style = MaterialTheme.typography.bodyLarge) },
                        confirmButton = { TextButton(onClick = ::finish) { Text("Done") } },
                    )
                    null -> Unit
                }
            }
        }
    }

    /** A second word selected while the dialog is still up replaces the first. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        selectedText(intent)?.let { step = Step.Confirm(it) }
    }

    private fun add(term: String) {
        step = Step.Looking
        lifecycleScope.launch {
            val repo = WordRepository(applicationContext)
            val existing = repo.words().firstOrNull { w ->
                w.term.equals(term, ignoreCase = true) || w.forms.any { it.equals(term, ignoreCase = true) } ||
                    w.lookedUp?.equals(term, ignoreCase = true) == true
            }
            step = Step.Done(
                when {
                    existing != null -> "“${existing.term}” is already in your library."
                    else -> {
                        val word = DictionaryLookup.lookup(term)
                        if (word != null) {
                            repo.addCustom(word)
                            "Added “${word.term}”: ${word.gloss}. It'll come up as a new word soon."
                        } else {
                            repo.addPending(term)
                            "Couldn't find a definition for “$term” (or you're offline). " +
                                "It's saved under Settings → Words waiting, ready to send off."
                        }
                    }
                },
            )
        }
    }

    /** One short word or phrase from PROCESS_TEXT or a share; null if it doesn't look like one. */
    private fun selectedText(intent: Intent?): String? {
        val raw = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: return null
        val cleaned = raw.toString().trim().trim('.', ',', ';', ':', '!', '?', '"', '“', '”', '\'')
        return cleaned.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH && it.count { c -> c == ' ' } <= 2 }
    }

    private companion object {
        const val MAX_LENGTH = 40
    }
}
