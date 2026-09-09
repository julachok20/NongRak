@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
    val canDelete = childState.isChildOwner

    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("food") }
    var severity by remember { mutableStateOf("moderate") }
    var symptoms by remember { mutableStateOf("") }
    var allergyToDelete by remember { mutableStateOf<Allergy?>(null) }

    // #3 fidelity fix: search + type/severity filters (list previously had no way to narrow it down).
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var severityFilter by remember { mutableStateOf<String?>(null) }
    val filteredAllergies = remember(allergies, query, typeFilter, severityFilter) {
        allergies.filter { a ->
            val matchesQuery = query.isBlank() ||
                a.name.contains(query, ignoreCase = true) ||
                a.symptoms?.contains(query, ignoreCase = true) == true
            val matchesType = typeFilter == null || a.type == typeFilter
            val matchesSeverity = severityFilter == null || a.severity == severityFilter
            matchesQuery && matchesType && matchesSeverity
        }
    }

    fun resetForm() { editingId = null; name = ""; type = "food"; severity = "moderate"; symptoms = "" }
    fun loadIntoForm(a: Allergy) { editingId = a.id; name = a.name; type = a.type; severity = a.severity; symptoms = a.symptoms ?: "" }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "แก้ไขประวัติภูมิแพ้" else "Edit Allergy History",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            ChildBottomNav(navController, "CHILD")
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

            if (allergies.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (s.isThai) "ค้นหาสิ่งที่แพ้หรืออาการ..." else "Search allergen or symptom...", color = Outline) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                        singleLine = true, shape = RoundedCornerShape(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AllergyFilterChip(if (s.isThai) "ทุกประเภท" else "All types", typeFilter == null) { typeFilter = null }
                        ALLERGY_TYPE_KEYS.forEach { key ->
                            AllergyFilterChip(typeLabel(key, s.isThai), typeFilter == key) { typeFilter = if (typeFilter == key) null else key }
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AllergyFilterChip(if (s.isThai) "ทุกระดับ" else "All severities", severityFilter == null) { severityFilter = null }
                        SEVERITIES.forEach { (key, label) ->
                            AllergyFilterChip(label, severityFilter == key) { severityFilter = if (severityFilter == key) null else key }
                        }
                    }
                }
            }

            if (allergies.isEmpty()) {
                item {
                    val noKnown = childState.child?.noKnownAllergies == true
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { childViewModel.setNoKnownAllergies(!noKnown) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (noKnown) Primary.copy(0.08f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Checkbox(checked = noKnown, onCheckedChange = { childViewModel.setNoKnownAllergies(it) })
                            Column(Modifier.weight(1f)) {
                                Text(if (s.isThai) "ยืนยันว่าไม่มีประวัติการแพ้" else "Confirm: no known allergies", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    if (s.isThai) "ติ๊กถ้าลูกไม่มีประวัติแพ้อะไรเลย จะได้ไม่ต้องเตือนซ้ำ" else "Check this if your child has no known allergies, so we stop asking",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (filteredAllergies.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ไม่พบรายการที่ตรงกับตัวกรอง" else "No allergies match this filter", color = Outline)
                    }
                }
            } else {
                items(filteredAllergies, key = { it.id }) { a ->
                    AllergyCard(a, canDelete = canDelete, onEdit = { loadIntoForm(a) }, onDelete = { allergyToDelete = a })
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
private fun AllergyFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(100.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.White)
    )
}

@Composable
private fun AllergyCard(a: Allergy, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                if (canDelete) {
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, "ลบ", tint = Error, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}
