package com.lullabyai.app.ui.screens.community

import androidx.compose.ui.graphics.Color
import com.lullabyai.app.data.model.Reaction

/**
 * Colour for the community.
 *
 * The app theme is a single blue (Primary 0xFF3757AE, PrimaryContainer 0xFF5270C9, Tertiary
 * 0xFF4B5B86 - all the same hue), so every chip, tab and icon came out the same shade. These give
 * each thing its own colour while staying in the same soft, rounded family as the rest of the app.
 *
 * Each accent has a strong version for icons and text, and a [soft] wash for the pill behind it.
 * The soft one is the strong one at low alpha rather than a separately picked pastel, so the pair
 * always agrees and both sit correctly on whatever surface they land on.
 */
object CommunityPalette {

    // --- Footer tabs -----------------------------------------------------------------------
    val Home = Color(0xFF3757AE)      // the app blue, kept for "back to NongRak"
    val Feed = Color(0xFFE85D9E)      // pink
    val Group = Color(0xFF16A394)     // teal
    val Profile = Color(0xFF8B5CF6)   // violet

    // --- Story rail topics -----------------------------------------------------------------
    val Food = Color(0xFFF08C2E)      // orange
    val Sleep = Color(0xFF6366F1)     // indigo
    val Play = Color(0xFF22A45D)      // green
    val Health = Color(0xFFE05252)    // red
    val Doctor = Color(0xFF0EA5E9)    // sky
    val Story = Color(0xFFE85D9E)     // pink, matching Feed

    // --- Composer entries ------------------------------------------------------------------
    val Photo = Color(0xFF22A45D)
    val AskDoctor = Color(0xFF0EA5E9)
    val Poll = Color(0xFF8B5CF6)

    // --- Sort chips ------------------------------------------------------------------------
    val Hot = Color(0xFFF2542D)       // flame
    val New = Color(0xFF16A394)

    /** The wash behind an icon or chip. 12% is what the child info pages use. */
    fun soft(color: Color): Color = color.copy(alpha = 0.12f)

    /**
     * Reaction colours, matched to what each face means rather than picked to look nice: love is
     * red, sad is a muted blue, laugh is the warm amber people already expect from it.
     */
    fun of(reaction: Reaction): Color = when (reaction) {
        Reaction.LIKE -> Color(0xFF2E81F2)
        Reaction.LOVE -> Color(0xFFE0245E)
        Reaction.LAUGH -> Color(0xFFF7B125)
        Reaction.SAD -> Color(0xFF5A7DA8)
    }
}
