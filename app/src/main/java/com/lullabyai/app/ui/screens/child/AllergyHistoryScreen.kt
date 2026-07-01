@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

private val ALLERGY_TYPE_KEYS = listOf("food", "environment", "medication")
private val SEVERITIES = listOf("mild" to "Mild", "moderate" to "Moderate", "severe" to "Severe")
private fun typeLabel(t: String, isThai: Boolean) = when (t) {
    "food" -> if (isThai) "อาหาร" else "Food"
    "environment" -> if (isThai) "สภาพแวดล้อม" else "Environment"
    "medication" -> if (isThai) "ยา" else "Medication"
    else -> t
}
private fun severityColor(s: String) = when (s) {
    "severe" -> Error
    "moderate" -> Color(0xFFFF9800)
    else -> Color(0xFFFBC02D)
}

@Composable
fun AllergyHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val allergies = childState.allergies

    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("food") }
    var severity by remember { mutableStateOf("moderate") }
    var symptoms by remember { mutableStateOf("") }
    var allergyToDelete by remember { mutableStateOf<Allergy?>(null) }

    fun resetForm() { editingId = null; name = ""; type = "food"; severity = "moderate"; symptoms = "" }
    fun loadIntoForm(a: Allergy) { editingId = a.id; name = a.name; type = a.type; severity = a.severity; symptoms = a.symptoms ?: "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "แก้ไขประวัติภูมิแพ้" else "Edit Allergy History", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
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
                Column {
                    Text(if (s.isThai) "ประวัติภูมิแพ้" else "Allergy History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (s.isThai) "ประวัติและการจัดการการแพ้" else "Allergy History and Management", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Add / edit form
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (editingId == null) Icons.Filled.AddCircle else Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(if (editingId == null) (if (s.isThai) "เพิ่มรายการ" else "Add entry") else (if (s.isThai) "แก้ไขรายการ" else "Edit entry"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        // Type dropdown
                        Text(if (s.isThai) "ประเภทหลักการแพ้" else "Allergy type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                            OutlinedTextField(
                                value = typeLabel(type, s.isThai), onValueChange = {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                            )
                            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                ALLERGY_TYPE_KEYS.forEach { key ->
                                    DropdownMenuItem(text = { Text(typeLabel(key, s.isThai)) }, onClick = { type = key; typeExpanded = false })
                                }
                            }
                        }

                        // Name
                        Text(if (s.isThai) "ชื่อสิ่งที่แพ้" else "Allergen name", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (s.isThai) "เช่น ไข่ไก่, ไรฝุ่น" else "e.g. Egg, Dust mite", color = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )

                        // Severity toggle
                        Text(if (s.isThai) "ระดับความรุนแรง" else "Severity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SEVERITIES.forEach { (key, label) ->
                                val sel = severity == key
                                Surface(
                                    onClick = { severity = key },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) Primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(label, modifier = Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }

                        // Symptoms
                        Text(if (s.isThai) "อาการที่พบ / ปฏิกิริยา" else "Symptoms / reaction", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = symptoms, onValueChange = { symptoms = it },
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            placeholder = { Text(if (s.isThai) "เช่น ผื่นแดง, อาเจียน, หายใจติดขัด" else "e.g. Rash, vomiting, difficulty breathing", color = Outline) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (editingId != null) {
                                OutlinedButton(onClick = { resetForm() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) { Text(if (s.isThai) "ยกเลิก" else "Cancel") }
                            }
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        if (editingId == null) childViewModel.addAllergy(name.trim(), type, severity, symptoms.ifBlank { null })
                                        else childViewModel.updateAllergy(editingId!!, name.trim(), type, severity, symptoms.ifBlank { null })
                                        resetForm()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) { Text(if (s.isThai) "บันทึกข้อมูล" else "Save", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // History list header
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.History, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(if (s.isThai) "รายการประวัติ" else "History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (allergies.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ยังไม่มีประวัติการแพ้" else "No allergy records yet", color = Outline, style = MaterialTheme.typography.bodyMedium) } }
            } else {
                items(allergies, key = { it.id }) { a ->
                    AllergyCard(a, onEdit = { loadIntoForm(a) }, onDelete = { allergyToDelete = a })
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    allergyToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { allergyToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = { Text(if (s.isThai) "ลบรายการแพ้?" else "Delete allergy?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "คุณแน่ใจหรือไม่ว่าต้องการลบ \"${target.name}\" ออกจากประวัติการแพ้?" else "Are you sure you want to remove \"${target.name}\" from the allergy history?") },
            confirmButton = { TextButton(onClick = { childViewModel.deleteAllergy(target.id); allergyToDelete = null }) { Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { allergyToDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun AllergyCard(a: Allergy, onEdit: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val color = severityColor(a.severity)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(modifier = Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.15f)) {
                        Text(a.severity.uppercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                    }
                }
                a.symptoms?.let { Text((if (s.isThai) "อาการ: " else "Symptoms: ") + it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(typeLabel(a.type, s.isThai), style = MaterialTheme.typography.labelSmall, color = Primary)
                    Text(com.lullabyai.app.util.TimeUtils.localDate(a.recordedAt) ?: "", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "แก้ไข", tint = Primary, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, "ลบ", tint = Error, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
