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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.OnboardingViewModel

@Composable
fun LastSetupScreen(
    onboardingViewModel: OnboardingViewModel,
    authViewModel: AuthViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val state by onboardingViewModel.uiState.collectAsState()
    var username by remember { mutableStateOf(state.username) }
    var relationship by remember { mutableStateOf(state.relationship) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable content — slides up when keyboard opens so nothing is hidden
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
                    Text("Last Set-Up", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(24.dp))

                Text("ขั้นตอนสุดท้าย", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "เกือบเสร็จแล้ว! มาสร้างโปรไฟล์ที่น่ารักสำหรับคุณและลูกน้อยกัน",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(Modifier.height(32.dp))

                // Profile photo
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Outline, modifier = Modifier.size(50.dp))
                    }
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("แตะเพื่อเพิ่มรูปโปรไฟล์", style = MaterialTheme.typography.bodySmall, color = Outline)

                Spacer(Modifier.height(32.dp))

                // Relationship to child
                Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ความสัมพันธ์กับเด็ก (Relationship to Child)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    val rels = listOf("mother" to "แม่ (Mother)", "father" to "พ่อ (Father)", "guardian" to "ผู้ปกครอง (Guardian)", "other" to "อื่นๆ (Other)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rels.take(2).forEach { (k, label) -> RelChip(label, relationship == k, Modifier.weight(1f)) { relationship = k } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rels.drop(2).forEach { (k, label) -> RelChip(label, relationship == k, Modifier.weight(1f)) { relationship = k } }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("USERNAME (ชื่อผู้ใช้)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your username", color = Outline) },
                        trailingIcon = { Icon(Icons.Filled.AlternateEmail, null, tint = Outline) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                    Text("ชื่อนี้จะแสดงในหน้าแดชบอร์ดและรายงานของคุณ", style = MaterialTheme.typography.bodySmall, color = Outline)
                }

                Spacer(Modifier.height(24.dp))
            }

            // Ready card + button always pinned above the keyboard
            Card(
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEmotions, null, tint = Primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text("เกือบพร้อมแล้ว!", style = MaterialTheme.typography.labelLarge, color = Primary, fontWeight = FontWeight.Bold)
                        Text("ข้อมูลลูกน้อยของคุณได้รับการซิงค์แล้ว", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                PrimaryButton(
                    text = "Complete (เสร็จสิ้น) →",
                    onClick = {
                        onboardingViewModel.updateLastSetup(username, null, relationship)
                        onboardingViewModel.completeOnboarding(onComplete)
                    },
                    isLoading = state.isLoading
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RelChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(100.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        border = if (!selected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Text(
            label, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
