@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun EditChildScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val canDelete = state.isChildOwner
    val child = state.child
    val context = LocalContext.current

    // Image picker → read bytes → upload
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull()
            if (bytes != null) childViewModel.uploadChildAvatar(bytes)
        }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Medical-document picker (PDF/JPG/PNG) → upload to the private medical-docs bucket.
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
    LaunchedEffect(child?.id) { childViewModel.loadMedicalDocuments() }

    var name by remember(child) { mutableStateOf(child?.name ?: "") }
    var birthdate by remember(child) { mutableStateOf(child?.birthdate ?: "") }
    var weight by remember(child) { mutableStateOf(child?.weightKg?.toString() ?: "") }
    var height by remember(child) { mutableStateOf(child?.heightCm?.toString() ?: "") }
    var notes by remember(child) { mutableStateOf(child?.notes ?: "") }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "แก้ไขข้อมูลลูก" else "Edit Child Info",
                onBack = onBack,
                actions = {
                    Button(
                        onClick = {
                            childViewModel.updateChildInfo(
                                name = name.trim(),
                                birthdate = birthdate.trim(),
                                weightKg = weight.toDoubleOrNull(),
                                heightCm = height.toDoubleOrNull(),
                                notes = notes.ifBlank { null },
                                onDone = onBack
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(if (s.isThai) "บันทึก" else "Save", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            // Avatar with camera badge
            val avatarUrl = state.childAvatarSignedUrl
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(SecondaryContainer)
                        .clickable { pickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.avatarUploading -> CircularProgressIndicator(color = Primary, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                        !avatarUrl.isNullOrBlank() -> AsyncImage(
                            model = avatarUrl, contentDescription = if (s.isThai) "รูปลูก" else "Child photo",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                        else -> Text("👶", fontSize = 50.sp)
                    }
                }
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Primary).clickable { pickImage() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Text(
                if (s.isThai) "เปลี่ยนรูปภาพ" else "Change photo",
                style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { pickImage() }
            )

            Spacer(Modifier.height(12.dp))

            FieldLabel(if (s.isThai) "ชื่อเล่น" else "Nickname")
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = editFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            FieldLabel(if (s.isThai) "วันเกิด" else "Birthdate")
            OutlinedTextField(
                value = birthdate, onValueChange = { birthdate = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD", color = Outline) },
                trailingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = Outline) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = editFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(if (s.isThai) "น้ำหนักล่าสุด (กก.)" else "Latest weight (kg)")
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = editFieldColors()
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(if (s.isThai) "ส่วนสูงล่าสุด (ซม.)" else "Latest height (cm)")
                    OutlinedTextField(
                        value = height, onValueChange = { height = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = editFieldColors()
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            FieldLabel(if (s.isThai) "บันทึกเพิ่มเติม" else "Additional notes")
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text(if (s.isThai) "ระบุรายละเอียดเพิ่มเติมเกี่ยวกับลูกของคุณ..." else "Add more details about your child...", color = Outline) },
                shape = RoundedCornerShape(12.dp),
                colors = editFieldColors()
            )

            // Medical Documents
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Medical Documents " + (if (s.isThai) "(เอกสารทางการแพทย์)" else ""), style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                Text(
                    if (s.isThai) "อัปโหลดประวัติสุขภาพ ใบสั่งยา หรือผลแล็บ เพื่อการวิเคราะห์ของ AI ที่แม่นยำขึ้น" else "Upload health records, prescriptions, or laboratory results for more personalized AI analysis.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Outline.copy(0.5f)), RoundedCornerShape(16.dp))
                        .clickable { docPicker.launch("*/*") }
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.docUploading) CircularProgressIndicator(color = Primary, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.CloudUpload, null, tint = Primary, modifier = Modifier.size(32.dp))
                        Text(if (s.isThai) "แตะเพื่ออัปโหลดไฟล์" else "Tap to upload files", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("PDF, JPG, PNG (Max 10MB)", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
                state.medicalDocuments.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Description, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(d.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        if (canDelete) {
                            IconButton(onClick = { childViewModel.deleteMedicalDocument(d.id, d.filePath) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, if (s.isThai) "ลบไฟล์" else "Remove", tint = Error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            state.error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)
