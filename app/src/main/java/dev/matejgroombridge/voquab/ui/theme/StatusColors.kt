package dev.matejgroombridge.voquab.ui.theme

import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus

/**
 * Voquab colours words by where they are in the learning lifecycle rather
 * than letting the user pick, so the Library reads as a progress map at a
 * glance: calm blue for new, warm yellow while learning (deepening to
 * peach in the later recall stages), green once known, grey when shelved.
 */
fun WordStatus.paletteEntry(): PaletteEntry = Palette.entry(
    when (this) {
        WordStatus.New -> "sky"
        WordStatus.Learning -> "butter"
        WordStatus.Known -> "mint"
        WordStatus.Shelved -> "fog"
    },
)

fun WordEntry.paletteEntry(): PaletteEntry =
    if (status == WordStatus.Learning && stage >= LATE_STAGE) Palette.entry("peach") else status.paletteEntry()

private const val LATE_STAGE = 4
