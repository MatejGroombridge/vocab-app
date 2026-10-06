package dev.matejgroombridge.voquab.ui.theme

import dev.matejgroombridge.voquab.data.model.WordStatus

/**
 * Voquab colours words by where they are in the learning lifecycle rather
 * than letting the user pick, so the Library reads as a progress map at a
 * glance: calm blue for new, warm yellow while learning, green once known,
 * neutral grey when shelved.
 */
fun WordStatus.paletteEntry(): PaletteEntry = Palette.entry(
    when (this) {
        WordStatus.New -> "sky"
        WordStatus.Learning -> "butter"
        WordStatus.Known -> "mint"
        WordStatus.Shelved -> "fog"
    },
)
