package com.lullabyai.app.ui.screens.sleep

import androidx.compose.ui.graphics.Color
import com.lullabyai.app.ui.theme.Primary

// ---------------------------------------------------------------------------
// Stitch sleep-section design tokens.
// The app-wide Material palette lives in ui/theme/Color.kt; these are the few
// extra fixed-role colors the Stitch sleep screens use that the theme has no
// slot for (hero gradient end, *-fixed containers, status green).
//
// Package-internal rather than private-per-file: all six sleep screens share
// them, and per-file copies would drift the moment one is retuned.
// ---------------------------------------------------------------------------
internal val HeroTop = Primary                 // #3757AE
internal val HeroBottom = Color(0xFF1E2B5E)    // primary-container
internal val PrimaryFixed = Color(0xFFDBE1FF)
internal val PrimaryFixedDim = Color(0xFFB4C5FF)
internal val OnPrimaryFixed = Color(0xFF00174C)
internal val SecondaryFixed = Color(0xFFD6E5EF)
internal val TertiaryFixed = Color(0xFFDAE2FF)
internal val StatusNormal = Color(0xFF2E7D32)
internal val BadgeGoodBg = Color(0xFFDCFCE7)
internal val BadgeGoodFg = Color(0xFF15803D)
internal val BadgeInfoBg = Color(0xFFDBEAFE)
internal val BadgeInfoFg = Color(0xFF1D4ED8)
