@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import kotlinx.coroutines.launch

private data class ChildTab(val key: String, val icon: ImageVector, val label: String, val route: String)

private val childTabs = listOf(
    ChildTab("overview", Icons.Filled.GridView, "ภาพรวม", Screen.ChildSummary.route),
    ChildTab("development", Icons.Filled.TrendingUp, "พัฒนาการ", Screen.ChildDevelopment.route),
    ChildTab("health", Icons.Filled.FavoriteBorder, "สุขภาพ", Screen.ChildHealth.route),
    ChildTab("nutrition", Icons.Filled.Restaurant, "โภชนาการ", Screen.Nutrition.route),
    ChildTab("exercise", Icons.Filled.FitnessCenter, "ออกกำลังกาย", Screen.Exercise.route),
    ChildTab("vaccine", Icons.Filled.Vaccines, "สมุดวัคซีน", Screen.VaccineBook.route),
    ChildTab("risk", Icons.Filled.Warning, "ความเสี่ยงโรค", Screen.DiseaseRisk.route)
)

/**
 * Shared scaffold for all 5 "ข้อมูลลูก" pages.
 * The hamburger icon opens a sidebar drawer; tapping an item navigates to that page.
 */
@Composable
fun ChildScreenScaffold(
    title: String,
    currentTab: String,
    navController: NavController,
    centerTitle: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Text("ข้อมูลลูก", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
                childTabs.forEach { tab ->
                    val selected = tab.key == currentTab
                    NavigationDrawerItem(
                        label = { Text(tab.label) },
                        selected = selected,
                        icon = { Icon(tab.icon, null) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (!selected) navController.navigate(tab.route) { launchSingleTop = true }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Primary.copy(0.12f))
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                val titleSlot: @Composable () -> Unit =
                    { Text(title, style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) }
                val navSlot: @Composable () -> Unit = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, "เมนู", tint = MaterialTheme.colorScheme.onSurface)
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
            containerColor = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}
