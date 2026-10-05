package dev.matejgroombridge.voquab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * A pastel-tone palette used to colour word cards. Each entry has a light
 * variant (used as the card background in light mode) and a dark variant
 * (used in dark mode), plus a stronger accent for the icon tile.
 *
 * Looked up by [key] so the palette can be re-ordered or extended
 * without breaking persisted data — unknown keys fall back to [defaultEntry].
 */
data class PaletteEntry(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    val accent: Color,
    val onColor: Color,
)

object Palette {

    // The family's curated 8-colour palette (see agent.md §10.7.2). Voquab
    // doesn't let the user pick colours — each word's colour is derived
    // from its learning stage — but the entries are kept identical to the
    // other apps so the suite looks like one family.
    val palette: List<PaletteEntry> = listOf(
        PaletteEntry(
            key = "blush",
            label = "Blush",
            light = Color(0xFFFFE0E6),
            dark = Color(0xFF5A3A42),
            accent = Color(0xFFF7A6B5),
            onColor = Color(0xFF3A1F25),
        ),
        PaletteEntry(
            key = "peach",
            label = "Peach",
            light = Color(0xFFFFE3D1),
            dark = Color(0xFF5A3F30),
            accent = Color(0xFFFFB48A),
            onColor = Color(0xFF3A2418),
        ),
        PaletteEntry(
            key = "butter",
            label = "Butter",
            light = Color(0xFFFFF4C2),
            dark = Color(0xFF55502B),
            accent = Color(0xFFFFE066),
            onColor = Color(0xFF3A330A),
        ),
        PaletteEntry(
            key = "mint",
            label = "Mint",
            light = Color(0xFFD1F0DA),
            dark = Color(0xFF2E4D3A),
            accent = Color(0xFF8DD6A4),
            onColor = Color(0xFF143222),
        ),
        PaletteEntry(
            // Sits between mint (green) and sky (blue) so the palette flows
            // smoothly along the green→cyan→blue axis.
            key = "teal",
            label = "Teal",
            light = Color(0xFFCFE8E4),
            dark = Color(0xFF2F4D49),
            accent = Color(0xFF8DCDC4),
            onColor = Color(0xFF143230),
        ),
        PaletteEntry(
            key = "sky",
            label = "Sky",
            light = Color(0xFFD3E8F5),
            dark = Color(0xFF2F4756),
            accent = Color(0xFF8FC4E0),
            onColor = Color(0xFF12303F),
        ),
        PaletteEntry(
            key = "lavender",
            label = "Lavender",
            light = Color(0xFFE3DAF5),
            dark = Color(0xFF3F354F),
            accent = Color(0xFFB7A5DD),
            onColor = Color(0xFF231A38),
        ),
        PaletteEntry(
            key = "fog",
            label = "Fog",
            light = Color(0xFFE2E5EA),
            dark = Color(0xFF40454D),
            accent = Color(0xFFB6BCC6),
            onColor = Color(0xFF22262D),
        ),
    )

    private val byKey: Map<String, PaletteEntry> = palette.associateBy { it.key }

    val defaultEntry: PaletteEntry get() = palette.first()

    fun entry(key: String): PaletteEntry = byKey[key] ?: defaultEntry
}

/**
 * Resolves the appropriate background colour for the current theme.
 */
@Composable
@ReadOnlyComposable
fun PaletteEntry.containerColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.let {
        // Cheap proxy: dark theme → background luminance is low.
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }
    return if (isDark) dark else light
}

/**
 * Foreground colour suitable for text on top of [containerColor]. In light
 * mode we use the entry's [PaletteEntry.onColor]; in dark mode we use the
 * theme's onSurface so contrast stays comfortable.
 */
@Composable
@ReadOnlyComposable
fun PaletteEntry.contentColor(): Color {
    val bg = MaterialTheme.colorScheme.background
    val isDark = (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.5f
    return if (isDark) MaterialTheme.colorScheme.onSurface else onColor
}
