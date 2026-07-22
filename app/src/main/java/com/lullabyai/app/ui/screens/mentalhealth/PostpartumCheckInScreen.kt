package com.lullabyai.app.ui.screens.mentalhealth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.PostpartumCatalog
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel

/** One-question-per-screen monthly postpartum physical-recovery check-in (see PostpartumCatalog).
 *  Same UI structure as MentalHealthAssessmentScreen — a separate axis (bleeding/pain/fever/fatigue),
 *  not duplicated to a shared component given the two catalogs' distinct clinical scope. */
@Composable
fun PostpartumCheckInScreen(authViewModel: AuthViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val authState by authViewModel.uiState.collectAsState()
    val questions = PostpartumCatalog.questions
    var index by remember { mutableStateOf(0) }
    val answers = remember { mutableStateListOf<Int?>(*arrayOfNulls(questions.size)) }
    val q = questions[index]

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (index > 0) index-- else onBack() }) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) }
                Text(if (isThai) "แบบประเมินการฟื้นตัวหลังคลอด" else "Postpartum Recovery Check-in", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(if (isThai) "ความคืบหน้า" else "Progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${index + 1}/${questions.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (index + 1) / questions.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(100.dp)), color = Primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Favorite, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        if (isThai) "ร่างกายคุณเพิ่งผ่านเรื่องใหญ่มา ตอบตามจริงเพื่อให้เราช่วยดูแลคุณได้ดีที่สุด" else "Your body just went through something big — answer honestly so we can help you best",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                        Text(
                            if (isThai) "ร่างกายคุณ" else "Your body", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isThai) "ในช่วง 7 วันที่ผ่านมา" else "In the past 7 days",
                        style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isThai) q.textTh else q.textEn,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEach { opt ->
                    val selected = answers[index] == opt.score
                    Card(
                        onClick = { answers[index] = opt.score },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) Primary.copy(0.1f) else MaterialTheme.colorScheme.surface),
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Primary) else null,
                        elevation = CardDefaults.cardElevation(if (selected) 0.dp else 1.dp)
                    ) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(selected = selected, onClick = { answers[index] = opt.score }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                            Text(if (isThai) opt.labelTh else opt.labelEn, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (index < questions.lastIndex) index++
                    else {
                        val finalAnswers = answers.map { it ?: 0 }
                        authViewModel.submitPostpartumCheckIn(finalAnswers, onDone)
                    }
                },
                enabled = answers[index] != null && !authState.postpartumSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (authState.postpartumSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(
                    if (index < questions.lastIndex) (if (isThai) "ถัดไป" else "Next") else (if (isThai) "ส่งแบบประเมิน" else "Submit"),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
