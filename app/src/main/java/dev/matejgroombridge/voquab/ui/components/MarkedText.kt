package dev.matejgroombridge.voquab.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Example sentences in words.json wrap the target word in `*asterisks*`.
 * This renders those spans with [highlight] (bold by default) and drops the
 * asterisks. Unbalanced markers are rendered literally rather than guessed.
 */
fun markedText(
    raw: String,
    highlight: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold),
): AnnotatedString = buildAnnotatedString {
    var rest = raw
    while (true) {
        val open = rest.indexOf('*')
        val close = if (open >= 0) rest.indexOf('*', open + 1) else -1
        if (open < 0 || close < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, open))
        withStyle(highlight) { append(rest.substring(open + 1, close)) }
        rest = rest.substring(close + 1)
    }
}
