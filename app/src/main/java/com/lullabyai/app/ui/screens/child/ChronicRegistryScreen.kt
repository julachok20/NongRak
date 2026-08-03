@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.ChronicRegistryViewModel

/**
 * Stitch **Chronic Disease & Prevention** / Chronic Conditions Registry (#14).
 * Named conditions CRUD against `chronic_conditions` + prevention tips + emergency dial (1669).
 * Replaces the retired AI Disease Risk % screen.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ChronicRegistryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    registryViewModel: ChronicRegistryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val registryState by registryViewModel.uiState.collectAsState()
    val child = childState.child
    val canDelete = childState.isChildOwner
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var prevention by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<ChronicCondition?>(null) }

    fun resetForm() {
        editingId = null
        name = ""
        prevention = ""
    }

    fun loadIntoForm(c: ChronicCondition) {
        editingId = c.id
        name = c.name
        prevention = c.prevention ?: c.notes.orEmpty()
    }

    LaunchedEffect(child?.id) {
        child?.id?.let { registryViewModel.load(it) }
    }

    val syncChildVm = {
        // Keep ChildHealth / Summary chronic list in sync after registry mutations.
        child?.id?.let { id ->
            // reload via public add/delete path: re-fetch by reusing loadChild side-effect
            childViewModel.refreshChronicConditionsFromRegistry()
        }
        Unit
    }

    ChildScreenScaffold(
        title = if (s.isThai) "โรคประจำตัวและการป้องกัน" else "Chronic Conditions",
        currentTab = "chronic",
        navController = navController,
        centerTitle = true
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Emergency call — Stitch registry requirement (ACTION_DIAL, no CALL_PHONE).
            item {
                Card(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:1669")))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Error)
                ) {
                    Row(
                        Modifier.padding(18.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (s.isThai) "โทรฉุกเฉิน 1669" else "Emergency call 1669",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                if (s.isThai) "สายด่วนการแพทย์ฉุกเฉิน ฟรี ตลอด 24 ชม."
                                else "Free 24-hour Thai emergency medical hotline",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.9f)
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(0.8f))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (s.isThai) "ทะเบียนโรคประจำตัว" else "Conditions registry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (s.isThai) "บันทึกชื่อโรคประจำตัวและวิธีป้องกันของลูก"
                        else "Track named chronic conditions and prevention tips",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add / edit form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (editingId == null) Icons.Filled.AddCircle else Icons.Filled.Edit,
                                null,
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                if (editingId == null) {
                                    if (s.isThai) "เพิ่มโรคประจำตัว" else "Add condition"
                                } else {
                                    if (s.isThai) "แก้ไขโรคประจำตัว" else "Edit condition"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            label = { Text(if (s.isThai) "ชื่อโรค / ภาวะ" else "Condition name") },
                            placeholder = {
                                Text(
                                    if (s.isThai) "เช่น หอบหืด, ธาลัสซีเมีย" else "e.g. Asthma, Thalassemia",
                                    color = Outline
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )
                        OutlinedTextField(
                            value = prevention,
                            onValueChange = { prevention = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            label = { Text(if (s.isThai) "คำแนะนำการป้องกัน" else "Prevention tip") },
                            placeholder = {
                                Text(
                                    if (s.isThai) "เช่น พกยาพ่น, หลีกเลี่ยงฝุ่นควัน"
                                    else "e.g. Carry inhaler, avoid smoke",
                                    color = Outline
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editingId != null) {
                                OutlinedButton(
                                    onClick = { resetForm() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (s.isThai) "ยกเลิก" else "Cancel")
                                }
                            }
                            Button(
                                onClick = {
                                    if (name.isBlank() || registryState.isSaving) return@Button
                                    val tip = prevention.trim().ifBlank { null }
                                    val after = {
                                        resetForm()
                                        syncChildVm()
                                    }
                                    val editId = editingId
                                    if (editId != null) {
                                        registryViewModel.update(editId, name, tip, onSynced = after)
                                    } else {
                                        registryViewModel.add(name, tip, onSynced = after)
                                    }
                                },
                                enabled = name.isNotBlank() && !registryState.isSaving,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                if (registryState.isSaving) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        if (editingId == null) {
                                            if (s.isThai) "บันทึก" else "Save"
                                        } else {
                                            if (s.isThai) "อัปเดต" else "Update"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    if (s.isThai) "รายการที่บันทึก" else "Registered conditions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                registryState.isLoading && registryState.conditions.isEmpty() -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                registryState.conditions.isEmpty() -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.HealthAndSafety, null, tint = Outline, modifier = Modifier.size(36.dp))
                                Text(
                                    if (s.isThai) "ยังไม่มีข้อมูลโรคประจำตัว" else "No chronic conditions recorded",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Outline
                                )
                            }
                        }
                    }
                }
                else -> {
                    items(registryState.conditions, key = { it.id }) { c ->
                        ConditionCard(
                            condition = c,
                            isThai = s.isThai,
                            canDelete = canDelete,
                            onEdit = { loadIntoForm(c) },
                            onDelete = { toDelete = c }
                        )
                    }
                }
            }

            // Prevention summary from registered tips + general baseline (not AI risk %).
            item {
                Text(
                    if (s.isThai) "คำแนะนำการป้องกัน" else "Prevention tips",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            val tipCards = buildPreventionTips(registryState.conditions, s.isThai)
            items(tipCards) { tip ->
                PreventionTipCard(icon = tip.icon, title = tip.title, detail = tip.detail)
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = {
                Text(
                    if (s.isThai) "ลบโรคประจำตัว?" else "Delete condition?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (s.isThai) "ต้องการลบ \"${target.name}\" หรือไม่?"
                    else "Remove \"${target.name}\"?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        registryViewModel.delete(target.id) {
                            if (editingId == target.id) resetForm()
                            syncChildVm()
                        }
                        toDelete = null
                    }
                ) {
                    Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(if (s.isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

}

@Composable
private fun ConditionCard(
    condition: ChronicCondition,
    isThai: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tip = condition.prevention ?: condition.notes
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MedicalInformation, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Text(
                    condition.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, if (isThai) "แก้ไข" else "Edit", tint = Primary, modifier = Modifier.size(18.dp))
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.DeleteOutline, if (isThai) "ลบ" else "Delete", tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (!tip.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, null, tint = Primary, modifier = Modifier.size(16.dp))
                    Text(
                        tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    if (isThai) "ยังไม่มีคำแนะนำการป้องกัน" else "No prevention tip yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline
                )
            }
        }
    }
}

private data class TipItem(val icon: ImageVector, val title: String, val detail: String)

private fun buildPreventionTips(conditions: List<ChronicCondition>, isThai: Boolean): List<TipItem> {
    val fromRegistry = conditions.mapNotNull { c ->
        val tip = c.prevention ?: c.notes
        if (tip.isNullOrBlank()) null
        else TipItem(
            Icons.Filled.Shield,
            c.name,
            tip
        )
    }
    val baseline = listOf(
        TipItem(
            Icons.Filled.Vaccines,
            if (isThai) "ฉีดวัคซีนตามกำหนด" else "Keep vaccines on schedule",
            if (isThai) "วัคซีนพื้นฐานช่วยลดความเสี่ยงโรคติดเชื้อรุนแรงในเด็กเล็ก"
            else "Routine vaccines lower the risk of serious infections in young children"
        ),
        TipItem(
            Icons.Filled.WaterDrop,
            if (isThai) "ล้างมือและสุขอนามัย" else "Hand hygiene",
            if (isThai) "ล้างมือก่อนกินอาหารและหลังเล่น ช่วยลดการติดเชื้อ"
            else "Wash hands before meals and after play to reduce infections"
        ),
        TipItem(
            Icons.Filled.LocalHospital,
            if (isThai) "พกข้อมูลโรคประจำตัว" else "Carry condition info",
            if (isThai) "แจ้งแพทย์/สถานรับเลี้ยงเด็กเกี่ยวกับโรคประจำตัวและยาที่ใช้"
            else "Tell doctors and caregivers about chronic conditions and medicines"
        )
    )
    return if (fromRegistry.isEmpty()) baseline else fromRegistry + baseline.take(2)
}

@Composable
private fun PreventionTipCard(icon: ImageVector, title: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
