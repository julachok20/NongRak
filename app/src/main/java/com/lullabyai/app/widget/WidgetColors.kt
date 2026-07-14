package com.lullabyai.app.widget

import androidx.compose.ui.graphics.Color

/** Color tokens ported from the 25.Widget design mockup, for use across all home-screen widgets. */
object WidgetColors {
    val Primary = Color(0xFF005AB5)
    val PrimaryContainer = Color(0xFF1B73DB)
    val OnPrimaryContainer = Color(0xFFFEFCFF)
    val Secondary = Color(0xFFA43A39)
    val SecondaryContainer = Color(0xFFFD7C77)
    /** SecondaryContainer is a *light* coral — needs dark text/icons on it, not OnPrimaryContainer
     *  (white), which is only readable on the dark/medium containers (Primary/TertiaryContainer). */
    val OnSecondaryContainer = Color(0xFF721418)
    val Tertiary = Color(0xFF006579)
    val TertiaryContainer = Color(0xFF1A7F97)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFEFF4FF)
    val OnSurface = Color(0xFF0B1C30)
    val OnSurfaceVariant = Color(0xFF414753)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val Green = Color(0xFF17A34A)
}
