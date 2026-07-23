@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.MedicalDocument
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun MedicalDocumentHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<MedicalDocument?>(null) }

    LaunchedEffect(state.child?.id) { childViewModel.loadMedicalDocuments() }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                val cr = context.contentResolver
                var fname = "document"
                cr.query(it, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) fname = c.getString(idx) ?: fname
                }
                val mime = cr.getType(it)
                val bytes = cr.openInputStream(it)?.use { st -> st.readBytes() }
                if (bytes != null && bytes.size <= 10 * 1024 * 1024) childViewModel.uploadMedicalDocument(fname, mime, bytes)
            }
        }
    }

    val filtered = state.medicalDocuments.filter { query.isBlank() || it.fileName.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "เอกสารทางการแพทย์" else "Medical Documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { docPicker.launch("*/*") }, containerColor = Primary, contentColor = Color.White) {
                if (state.docUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Add, null)
            }
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (s.isThai) "จำนวนเอกสารทั้งหมด" else "Total documents", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.medicalDocuments.size} " + (if (s.isThai) "ไฟล์" else "files"), style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาชื่อเอกสาร..." else "Search documents...", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }

            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ยังไม่มีเอกสาร" else "No documents yet", color = Outline)
                    }
                }
            } else {
                items(filtered, key = { it.id }) { doc ->
                    DocumentRow(doc, onDelete = { toDelete = doc })
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = { Text(if (s.isThai) "ลบเอกสาร?" else "Delete document?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "ลบ \"${target.fileName}\" อย่างถาวรใช่หรือไม่?" else "Permanently delete \"${target.fileName}\"?") },
            confirmButton = { TextButton(onClick = { childViewModel.deleteMedicalDocument(target.id, target.filePath); toDelete = null }) { Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun DocumentRow(doc: MedicalDocument, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val isPdf = doc.mimeType?.contains("pdf") == true
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background((if (isPdf) Error else Primary).copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image, null, tint = if (isPdf) Error else Primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(doc.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(com.lullabyai.app.util.TimeUtils.localDate(doc.createdAt) ?: "", style = MaterialTheme.typography.labelSmall, color = Outline)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, if (s.isThai) "ลบ" else "Delete", tint = Error, modifier = Modifier.size(20.dp)) }
        }
    }
}
