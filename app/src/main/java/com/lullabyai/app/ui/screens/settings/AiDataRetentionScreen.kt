package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.MedicalDataVaultRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

/**
 * PDPA transparency: "What is the AI keeping about my child?" — moved out of MedicalDataVault
 * so Vault can match the Stitch Document Vault (file hub). Linked from Settings.
 */
@Composable
fun AiDataRetentionScreen(
    childViewModel: ChildViewModel,
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val childId = childState.child?.id
    val canDelete = childState.isChildOwner
    val repo = remember { MedicalDataVaultRepository() }
    val scope = rememberCoroutineScope()

    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var categoryToDelete by remember { mutableStateOf<MedicalDataVaultRepository.Category?>(null) }
    var deleting by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            counts = repo.categories.associate { it.table to repo.getCount(it, childId) }
            loading = false
        }
    }
    LaunchedEffect(childId) { reload() }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ข้อมูลที่ AI เก็บไว้" else "Data the AI keeps",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.SETTINGS,
                onHome = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.MyDoctorsHub.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Storage, null, tint = Primary)
                    Text(
                        if (isThai) "รายการนี้คือข้อมูลที่ AI ใช้วิเคราะห์และให้คำแนะนำ (สำหรับลูกที่เลือกอยู่) คุณสามารถลบเป็นรายหมวดได้ทุกเมื่อ"
                        else "This is the data the AI uses to analyze and advise (for the currently selected child). You can delete any category at any time.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(repo.categories, key = { it.table }) { cat ->
                        val count = counts[cat.table] ?: 0
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isThai) cat.labelTh else cat.labelEn,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "$count " + (if (isThai) "รายการ" else "records") +
                                            if (!cat.childScoped) (if (isThai) " (ทุกลูก)" else " (all children)") else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { categoryToDelete = cat }, enabled = count > 0 && canDelete) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        null,
                                        tint = if (count > 0) Error else Outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { if (!deleting) categoryToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = { Text(if (isThai) "ลบ${cat.labelTh}?" else "Delete ${cat.labelEn}?") },
            text = {
                Text(
                    if (isThai) "ข้อมูลนี้จะถูกลบถาวรและ AI จะไม่สามารถใช้ข้อมูลนี้วิเคราะห์ได้อีก การกระทำนี้ย้อนกลับไม่ได้"
                    else "This will be permanently deleted and the AI will no longer use it for analysis. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!canDelete) return@TextButton
                        deleting = true
                        scope.launch {
                            repo.deleteCategory(cat, childId)
                            deleting = false
                            categoryToDelete = null
                            reload()
                        }
                    },
                    enabled = !deleting
                ) {
                    if (deleting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Error)
                    } else {
                        Text(
                            if (isThai) "ลบถาวร" else "Delete forever",
                            color = Error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }, enabled = !deleting) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }
}
