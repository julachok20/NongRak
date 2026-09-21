package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*

/**
 * First step of adding a child. Almost every new parent wants "add my baby", so that is one big
 * button that says how little it asks (nickname + birthday). The other two ways in (auto-fill from
 * a birth-certificate photo, or join a child a spouse/relative already created) are smaller rows
 * underneath, worded as the situation the parent is in rather than a feature name. The three were
 * equal-weight cards before, and "เชื่อมข้อมูลลูก" read like a required step.
 */
@Composable
fun AddChildChoiceScreen(onAddNew: () -> Unit, onSync: () -> Unit, onUploadCertificate: () -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val isThai = s.isThai

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) }
                Spacer(Modifier.width(4.dp))
                Text(if (isThai) "เพิ่มข้อมูลลูก" else "Add a child", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAddNew),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChildCare, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text(if (isThai) "เพิ่มลูกของฉัน" else "Add my child", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (isThai) "ใช้แค่ชื่อเล่นกับวันเกิด ใช้เวลาไม่ถึง 1 นาที ข้อมูลอื่นเพิ่มทีหลังได้" else "Just a nickname and birthday, under a minute. Everything else can wait.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.9f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(100.dp), color = Color.White) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isThai) "เริ่มเลย" else "Start", color = Primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowForward, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(if (isThai) "หรือ" else "Or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            SecondaryRow(
                icon = Icons.Filled.PhotoCamera,
                title = if (isThai) "มีสูติบัตรอยู่ใกล้ๆ?" else "Birth certificate at hand?",
                subtitle = if (isThai) "ถ่ายรูป แล้วระบบกรอกชื่อและวันเกิดให้" else "Snap it and we fill in name and birthday",
                onClick = onUploadCertificate
            )
            Spacer(Modifier.height(10.dp))
            SecondaryRow(
                icon = Icons.Filled.Group,
                title = if (isThai) "คู่สมรสหรือญาติเพิ่มลูกไว้แล้ว?" else "Spouse or family already added your child?",
                subtitle = if (isThai) "ขอเข้าร่วมดูแล ใช้ข้อมูลชุดเดียวกัน ไม่ต้องกรอกซ้ำ" else "Join them and share the same records",
                onClick = onSync
            )
        }
    }
}

@Composable
private fun SecondaryRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}
