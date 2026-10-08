package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.OnboardingViewModel

@Composable
fun OnboardingStep2Screen(
    onboardingViewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onFillMore: () -> Unit,
    onBack: () -> Unit
) {
    val state by onboardingViewModel.uiState.collectAsState()
    var height by remember { mutableStateOf(state.heightCm) }
    var weight by remember { mutableStateOf(state.weightKg) }
    var sleepHours by remember { mutableStateOf(state.sleepHours) }
    var activityLevel by remember { mutableStateOf(state.activityLevel) }
    var bloodGroup by remember { mutableStateOf(state.bloodGroup) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.weight(1f))
                Text("ข้อมูลเพิ่มเติม", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp))
            }

            OnboardingProgressBar(currentStep = 2)
            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("ข้อมูลเพิ่มเติม", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("ช่วยให้ AI วิเคราะห์พัฒนาการของลูกน้อยได้อย่างแม่นยำยิ่งขึ้น", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ส่วนสูง (cm) (ไม่บังคับ)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = height, onValueChange = { height = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("เช่น 65", color = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("น้ำหนัก (kg) (ไม่บังคับ)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = weight, onValueChange = { weight = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("เช่น 7.2", color = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()
                        )
                    }
                }

                // Sleep hours. A slider pre-set to 14 made an optional field look already
                // answered, and left a parent dragging a thumb for a number she probably does not
                // know. This stays unset until she says otherwise: big plus/minus targets, no
                // dragging, and a clear way back to "not known".
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ชั่วโมงการนอนต่อวัน (ไม่บังคับ)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

                    val suggested = com.lullabyai.app.util.SleepTarget.forBirthdate(state.birthdate)
                    val current = sleepHours

                    if (current == null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { sleepHours = suggested.toFloat() },
                                shape = RoundedCornerShape(100.dp)
                            ) { Text("ประมาณ $suggested ชม.") }
                            Text(
                                "ข้ามได้ ถ้ายังไม่ทราบ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { sleepHours = (current - 1f).coerceAtLeast(1f) },
                                enabled = current > 1f
                            ) { Icon(Icons.Filled.Remove, contentDescription = "ลดหนึ่งชั่วโมง") }

                            Text(
                                "${current.toInt()} ชม.",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )

                            FilledTonalIconButton(
                                onClick = { sleepHours = (current + 1f).coerceAtMost(24f) },
                                enabled = current < 24f
                            ) { Icon(Icons.Filled.Add, contentDescription = "เพิ่มหนึ่งชั่วโมง") }

                            TextButton(onClick = { sleepHours = null }) { Text("ล้าง") }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ระดับกิจกรรมต่อวัน (ไม่บังคับ)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    listOf(
                        Triple("low", Icons.Filled.NightlightRound, "Low Activity\nนอนหลับมากและทำกิจกรรมน้อย"),
                        Triple("moderate", Icons.Filled.DirectionsWalk, "Moderate Activity\nมีการพักผ่อนและเล่นตามปกติ"),
                        Triple("high", Icons.Filled.DirectionsRun, "High Activity\nวิ่งเล่นเคลื่อนไหวตลอดเวลา")
                    ).forEach { (key, icon, label) ->
                        ActivityCard(
                            icon = icon,
                            label = label,
                            selected = activityLevel == key,
                            onClick = { activityLevel = key }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("กรุ๊ปเลือด (ไม่บังคับ)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("A", "B", "AB", "O").forEach { bg ->
                            val sel = bloodGroup == bg
                            Surface(
                                onClick = { bloodGroup = if (sel) "" else bg },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) Primary else MaterialTheme.colorScheme.surface,
                                border = if (!sel) ButtonDefaults.outlinedButtonBorder else null
                            ) {
                                Text(bg, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary)
                        Column {
                            Text("AI พร้อมวิเคราะห์ทันที", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("สามารถเลือกเข้าสู่หน้าหลักได้ทันที หรือกรอกข้อมูลเพิ่มเติมเพื่อการวิเคราะห์ที่ละเอียดขึ้น", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (state.addMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OnboardingVaccineBookSection(
                                onboardingViewModel = onboardingViewModel,
                                pendingCount = state.pendingDocs.size,
                                enabled = !state.isLoading
                            )
                            OnboardingVaccineBookPendingList(
                                onboardingViewModel = onboardingViewModel,
                                pendingDocs = state.pendingDocs,
                                enabled = !state.isLoading
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Button 1 (MAIN): เข้าสู่หน้าหลักเลย
                PrimaryButton(
                    text = when {
                        state.isLoading -> "..."
                        else -> "เข้าสู่หน้าหลักเลย ✓"
                    },
                    enabled = !state.isLoading,
                    onClick = {
                        onboardingViewModel.updateStep2(height, weight, sleepHours, activityLevel, bloodGroup)
                        onboardingViewModel.setNoKnownAllergies(true)
                        onboardingViewModel.updateStep3(false, "", emptyList(), "")
                        onComplete()
                    }
                )

                // Button 2 (SECOND): กรอกข้อมูลเพิ่มเติม -> linking to Step 3
                OutlinedButton(
                    onClick = {
                        onboardingViewModel.updateStep2(height, weight, sleepHours, activityLevel, bloodGroup)
                        onFillMore()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.5.dp, Primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    enabled = !state.isLoading
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "กรอกข้อมูลเพิ่มเติม (วัคซีน / สุขภาพ) →",
                            style = MaterialTheme.typography.labelLarge,
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    "Secure and private health data processing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ActivityCard(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) ButtonDefaults.outlinedButtonBorder else null,
        colors = CardDefaults.cardColors(containerColor = if (selected) Primary.copy(0.06f) else MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(if (selected) Primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (selected) Primary else Outline, modifier = Modifier.size(22.dp))
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 20.sp
            )
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Primary))
        }
    }
}
