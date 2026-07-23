@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

/**
 * Shared scaffold for the "ข้อมูลลูก" pages. [currentTab] is currently unused (the sidebar drawer
 * that highlighted it was removed — navigation between these pages now goes through the child
 * dashboard's 4-button grid / Health Log hub instead) but kept so call sites don't need updating.
 */
@Composable
fun ChildScreenScaffold(
    title: String,
    currentTab: String,
    navController: NavController,
    centerTitle: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            val titleSlot: @Composable () -> Unit =
                { Text(title, style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) }
            val navSlot: @Composable () -> Unit = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            val actionsSlot: @Composable RowScope.() -> Unit = {
                IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                    Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            val barColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            if (centerTitle) {
                CenterAlignedTopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, colors = barColors)
            } else {
                TopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, colors = barColors)
            }
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}
