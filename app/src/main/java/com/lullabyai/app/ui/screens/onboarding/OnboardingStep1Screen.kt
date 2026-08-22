package com.lullabyai.app.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.BirthDatePickerField
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.OnboardingViewModel

@Composable
fun OnboardingStep1Screen(
    onboardingViewModel: OnboardingViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit = {}
) {
    val state by onboardingViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var childName by remember { mutableStateOf(state.childName) }
    var birthdate by remember { mutableStateOf(state.birthdate) }
    var gender by remember { mutableStateOf(state.gender) }
    var relationship by remember { mutableStateOf(state.relationship.ifBlank { "mother" }) }
    var nationality by remember { mutableStateOf(state.nationality) }
    var ethnicity by remember { mutableStateOf(state.ethnicity) }
    var religion by remember { mutableStateOf(state.religion) }
    var avatarUri by remember { mutableStateOf(state.childAvatarUri) }
    var avatarBytes by remember { mutableStateOf(state.childAvatarBytes) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarUri = uri.toString()
            avatarBytes = com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
        }
    }

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("ข้อมูลเบื้องต้นของเด็ก", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Outline, modifier = Modifier.size(20.dp))
                }
            }

            // Progress
            OnboardingProgressBar(currentStep = 1)

            Spacer(Modifier.height(16.dp))

            // Child Avatar Picker (Optional)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = "รูปโปรไฟล์เด็ก",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.AddAPhoto,
                            contentDescription = "เพิ่มรูปโปรไฟล์",
                            tint = Primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (avatarUri != null) "เปลี่ยนรูปโปรไฟล์น้อง" else "เพิ่มรูปโปรไฟล์น้อง (ไม่บังคับ)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LabelField("คุณคือใคร (ความสัมพันธ์กับเด็ก)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "mother" to "คุณแม่",
                            "father" to "คุณพ่อ",
                            "guardian" to "ผู้ปกครอง",
                            "other" to "ญาติ/อื่นๆ"
                        ).forEach { (value, label) ->
                            Box(modifier = Modifier.weight(1f)) {
                                GenderChip(
                                    label = label,
                                    selected = relationship == value,
                                    onClick = { relationship = value },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                LabelField("ชื่อเด็ก") {
                    OutlinedTextField(
                        value = childName, onValueChange = { childName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("กรอกชื่อเด็ก", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                }

                LabelField("วันเกิด") {
                    BirthDatePickerField(
                        value = birthdate,
                        onValueChange = { birthdate = it },
                        placeholder = "แตะเพื่อเลือกวันเกิดจากปฏิทิน",
                        fieldColors = { fieldColors() }
                    )
                }

                LabelField("เพศ") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("male" to "ชาย", "female" to "หญิง", "unspecified" to "ไม่ระบุ").forEach { (value, label) ->
                            Box(modifier = Modifier.weight(1f)) {
                                GenderChip(
                                    label = label,
                                    selected = gender == value,
                                    onClick = { gender = value },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Collapsible Additional Information (สัญชาติ / เชื้อชาติ / ศาสนา)
                var showAdditionalInfo by remember { mutableStateOf(nationality.isNotBlank() || ethnicity.isNotBlank() || religion.isNotBlank()) }

                Surface(
                    onClick = { showAdditionalInfo = !showAdditionalInfo },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                if (showAdditionalInfo) "ข้อมูลเพิ่มเติม (สัญชาติ / เชื้อชาติ / ศาสนา)"
                                else "ข้อมูลเพิ่มเติม (ไม่บังคับ)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (showAdditionalInfo) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = Outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = showAdditionalInfo) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        LabelField("สัญชาติ (ไม่บังคับ)") {
                            OutlinedTextField(
                                value = nationality, onValueChange = { nationality = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("เช่น ไทย", color = Outline) },
                                singleLine = true, shape = RoundedCornerShape(12.dp),
                                colors = fieldColors()
                            )
                        }
                        LabelField("เชื้อชาติ (ไม่บังคับ)") {
                            OutlinedTextField(
                                value = ethnicity, onValueChange = { ethnicity = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("เช่น ไทย", color = Outline) },
                                singleLine = true, shape = RoundedCornerShape(12.dp),
                                colors = fieldColors()
                            )
                        }
                        LabelField("ศาสนา (ไม่บังคับ)") {
                            OutlinedTextField(
                                value = religion, onValueChange = { religion = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("เช่น พุทธ", color = Outline) },
                                singleLine = true, shape = RoundedCornerShape(12.dp),
                                colors = fieldColors()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = "ถัดไป →",
                    onClick = {
                        onboardingViewModel.updateStep1(
                            name = childName,
                            birthdate = birthdate,
                            gender = gender,
                            nationality = nationality,
                            ethnicity = ethnicity,
                            religion = religion,
                            relationship = relationship,
                            avatarUri = avatarUri,
                            avatarBytes = avatarBytes
                        )
                        onNext()
                    },
                    enabled = childName.isNotBlank() && birthdate.isNotBlank()
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun OnboardingProgressBar(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..3).forEach { step ->
            val isActive = step == currentStep
            val isDone = step < currentStep
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(if (isActive || isDone) Primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    color = if (isActive || isDone) Color.White else Outline,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (step < 3) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = if (isDone) Primary else MaterialTheme.colorScheme.outlineVariant,
                    thickness = 2.dp
                )
            }
        }
    }
}

@Composable
fun GenderChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        border = if (!selected) ButtonDefaults.outlinedButtonBorder else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                label,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun LabelField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface
)
