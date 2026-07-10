package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var childName by remember { mutableStateOf(state.childName) }
    var birthdate by remember { mutableStateOf(state.birthdate) }
    var gender by remember { mutableStateOf(state.gender) }
    var nationality by remember { mutableStateOf(state.nationality) }
    var ethnicity by remember { mutableStateOf(state.ethnicity) }
    var religion by remember { mutableStateOf(state.religion) }
    // Allergies + notes are collected later (Step 3: list-based allergies & notes), not here.

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

            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("male" to "ชาย", "female" to "หญิง", "unspecified" to "ไม่ระบุ").forEach { (value, label) ->
                            GenderChip(label = label, selected = gender == value, onClick = { gender = value })
                        }
                    }
                }

                // Hospital-record demographics — optional, not required to proceed (matches
                // allergies/notes below, which are also optional at this step).
                LabelField("สัญชาติ") {
                    OutlinedTextField(
                        value = nationality, onValueChange = { nationality = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("เช่น ไทย", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                }
                LabelField("เชื้อชาติ") {
                    OutlinedTextField(
                        value = ethnicity, onValueChange = { ethnicity = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("เช่น ไทย", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                }
                LabelField("ศาสนา") {
                    OutlinedTextField(
                        value = religion, onValueChange = { religion = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("เช่น พุทธ", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = "Next >",
                    onClick = {
                        onboardingViewModel.updateStep1(childName, birthdate, gender, "", "", nationality, ethnicity, religion)
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
fun GenderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        border = if (!selected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
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
