package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Publishes real UI elements' on-screen positions so [TourOverlay] can draw a spotlight cutout at
 * their EXACT location instead of an approximate arrow — e.g. the ☰ sidebar icon buried inside
 * [com.lullabyai.app.ui.screens.child.ChildScreenScaffold]'s TopAppBar, which the overlay (drawn as a
 * separate sibling in AppNavigation.kt, not inside that screen) has no direct layout access to.
 * A Compose state map so the overlay recomposes the instant a tagged element reports its bounds.
 */
object TourTargetRegistry {
    val targets = mutableStateMapOf<String, Rect>()
}

/** Tag a composable so its window-space bounds are published under [key] while it's on screen. */
fun Modifier.tourAnchor(key: String): Modifier = this.onGloballyPositioned { coords ->
    TourTargetRegistry.targets[key] = coords.boundsInWindow()
}
