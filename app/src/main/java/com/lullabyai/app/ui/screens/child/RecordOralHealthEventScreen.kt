@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.OralHealthViewModel
import java.time.LocalDate

@Composable
fun RecordOralHealthEventScreen(
    oralHealthViewModel: OralHealthViewModel,
    navController: NavController
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val state by oralHealthViewModel.uiState.collectAsState()

    var selectedCategory by remember { mutableStateOf("first_tooth") }
    var eventName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var eventNotes by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = (selectedImages + uris).take(3)
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกเหตุการณ์" else "Record Oral Event",
                onBack = { navController.popBackStack() }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    PrimaryButton(
                        text = if (isThai) "บันทึกข้อมูล" else "Save Event",
                        isLoading = state.isSaving,
                        enabled = eventName.isNotBlank() && !state.isSaving,
                        onClick = {
                            if (eventName.isBlank()) {
                                Toast.makeText(context, if (isThai) "กรุณาระบุชื่อเหตุการณ์" else "Please enter event title", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }
                            oralHealthViewModel.saveEvent(
                                category = selectedCategory,
                                eventName = eventName.trim(),
                                eventDate = eventDate,
                                notes = eventNotes.trim().ifBlank { null },
                                photoUrls = selectedImages.map { it.toString() }
                            ) {
                                Toast.makeText(context, if (isThai) "บันทึกสำเร็จ" else "Saved successfully", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Category Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isThai) "ประเภทเหตุการณ์" else "Event Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryTile(
                        icon = Icons.Filled.ChildCare,
                        label = if (isThai) "ฟันซี่แรก" else "First Tooth",
                        selected = selectedCategory == "first_tooth",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedCategory = "first_tooth"
                            if (eventName.isBlank()) eventName = if (isThai) "ฟันซี่แรกขึ้นแล้ว" else "First tooth erupted"
                        }
                    )
                    CategoryTile(
                        icon = Icons.Filled.MedicalServices,
                        label = if (isThai) "พบหมอฟัน" else "Dentist",
                        selected = selectedCategory == "dentist_visit",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedCategory = "dentist_visit"
                            if (eventName.isBlank()) eventName = if (isThai) "พบทันตแพทย์เด็ก" else "Dentist checkup"
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryTile(
                        icon = Icons.Filled.Healing,
                        label = if (isThai) "การรักษา" else "Treatment",
                        selected = selectedCategory == "treatment",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedCategory = "treatment"
                            if (eventName.isBlank()) eventName = if (isThai) "เคลือบฟลูออไรด์" else "Fluoride treatment"
                        }
                    )
                    CategoryTile(
                        icon = Icons.Filled.Category,
                        label = if (isThai) "อื่นๆ" else "Other",
                        selected = selectedCategory == "other",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedCategory = "other" }
                    )
                }
            }

            // Event Details Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Event Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isThai) "ชื่อเหตุการณ์/รายละเอียด *" else "Event Name *",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            placeholder = { Text(if (isThai) "เช่น ฟันน้ำนมซี่แรกขึ้นแล้ว" else "e.g. First tooth erupted") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Event Date
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isThai) "วันที่ *" else "Date *",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = { eventDate = it },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, tint = Primary) },
                            singleLine = true
                        )
                    }

                    // Notes
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isThai) "บันทึกเพิ่มเติม" else "Notes / Symptoms",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = eventNotes,
                            onValueChange = { eventNotes = it },
                            placeholder = { Text(if (isThai) "ระบุอาการ ความรู้สึก หรือคำแนะนำจากหมอฟัน..." else "Notes, feelings, or dental instructions...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                }
            }

            // Image Upload Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isThai) "แนบรูปภาพ" else "Attach Photos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isThai) "สูงสุด 3 รูป" else "Max 3 photos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Upload Picker Tile
                    if (selectedImages.size < 3) {
                        Surface(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, null, tint = Primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(2.dp))
                                Text(if (isThai) "เพิ่มรูป" else "Add", style = MaterialTheme.typography.labelSmall, color = Primary, fontSize = 10.sp)
                            }
                        }
                    }

                    // Attached Previews
                    selectedImages.forEachIndexed { index, uri ->
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedImages = selectedImages.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
