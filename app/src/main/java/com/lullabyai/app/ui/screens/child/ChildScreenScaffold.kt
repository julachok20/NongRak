@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar

/**
 * Shared scaffold for the "ข้อมูลลูก" pages. [currentTab] is currently unused (the sidebar drawer
 * that highlighted it was removed — navigation between these pages now goes through the child
 * dashboard's 4-button grid / Health Log hub instead) but kept so call sites don't need updating.
 *
 * Header is delegated to [LullabyTopBar] — the app-wide shared header — rather than built inline,
 * so this and every other screen stay on exactly one header implementation.
 */
@Composable
fun ChildScreenScaffold(
    title: String,
    currentTab: String,
    navController: NavController,
    centerTitle: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    // Child Info is the CHILD tab's ROOT — the footer is how you leave it, so it gets no back
    // arrow. Every other child page is a sub-page and keeps one. This lives here rather than in
    // the screen because the scaffold hardcoded the arrow for all 9 of its callers.
    showBack: Boolean = true,
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            LullabyTopBar(
                title = title,
                titleContent = titleContent,
                onBack = if (showBack) ({ navController.popBackStack() }) else null,
                navController = navController,
                centerTitle = centerTitle
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}
