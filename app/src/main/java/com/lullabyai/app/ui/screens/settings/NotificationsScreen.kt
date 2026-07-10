@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.AppNotification
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.ui.components.SyncRequestCard
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.NotificationViewModel

@Composable
fun NotificationsScreen(navController: NavController, childViewModel: ChildViewModel, onBack: () -> Unit) {
    val vm: NotificationViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load(); childViewModel.loadPendingSyncRequests() }

    val tabs = listOf(
        Triple("schedule", "กำหนดการ", NotifCategory.SCHEDULE),
        Triple("warning", "คำเตือน", NotifCategory.WARNING),
        Triple("normal", "ทั่วไป", NotifCategory.NORMAL),
        Triple("sync_request", "คำขอเชื่อมข้อมูล", NotifCategory.SYNC_REQUEST)
    )
    var selectedTab by remember { mutableStateOf(0) }
    val currentCategory = tabs[selectedTab].third
    val filtered = state.all.filter { it.category == currentCategory }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("การแจ้งเตือน", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.HOME,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Category tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Primary
            ) {
                tabs.forEachIndexed { i, (key, label, cat) ->
                    val count = if (cat == NotifCategory.SYNC_REQUEST) childState.pendingSyncRequests.size
                                 else state.all.count { it.category == cat }
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(if (count > 0) "$label ($count)" else label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            if (currentCategory == NotifCategory.SYNC_REQUEST) {
                if (childState.pendingSyncRequests.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.NotificationsNone, null, tint = Outline, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("ยังไม่มีคำขอเชื่อมข้อมูล", color = Outline, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(childState.pendingSyncRequests) { req ->
                            val childName = childState.children.find { it.id == req.childId }?.name ?: "-"
                            SyncRequestCard(
                                childName = childName,
                                onApprove = { childViewModel.confirmSyncRequest(req.id, true) },
                                onReject = { childViewModel.confirmSyncRequest(req.id, false) }
                            )
                        }
                    }
                }
            } else if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.NotificationsNone, null, tint = Outline, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ยังไม่มีการแจ้งเตือน", color = Outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(filtered) { n -> NotificationCard(n) }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification) {
    val (icon, color) = when (n.category) {
        NotifCategory.SCHEDULE -> Icons.Filled.Event to Primary
        NotifCategory.WARNING -> Icons.Filled.Warning to HealthRisk
        else -> Icons.Filled.Notifications to HealthGood
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                n.body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                com.lullabyai.app.util.TimeUtils.localDate(n.createdAt)?.let { d ->
                    val t = com.lullabyai.app.util.TimeUtils.localTime(n.createdAt)
                    Text(if (t != null) "$d • $t" else d, style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
        }
    }
}
