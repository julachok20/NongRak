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

/** First step of adding a child: create a brand new profile, sync into one another parent made,
 *  or auto-fill a new profile from a photo of the birth certificate. */
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

            Spacer(Modifier.height(24.dp))
            Text(
                if (isThai) "คุณต้องการเพิ่มลูกใหม่ หรือเชื่อมข้อมูลลูกที่มีอยู่แล้ว?" else "Add a new child, or sync one that already exists?",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            ChoiceCard(
                icon = Icons.Filled.Add,
                title = if (isThai) "เพิ่มลูกใหม่" else "Add new child",
                subtitle = if (isThai) "สร้างโปรไฟล์ลูกคนใหม่ตั้งแต่ต้น" else "Create a brand new child profile",
                onClick = onAddNew
            )
            Spacer(Modifier.height(16.dp))
            ChoiceCard(
                icon = Icons.Filled.SyncAlt,
                title = if (isThai) "เชื่อมข้อมูลลูก" else "Sync child",
                subtitle = if (isThai) "เข้าถึงข้อมูลลูกที่ผู้ปกครองอีกท่านสร้างไว้แล้ว" else "Get access to a child another parent already created",
                onClick = onSync
            )
            Spacer(Modifier.height(16.dp))
            ChoiceCard(
                icon = Icons.Filled.CloudUpload,
                title = if (isThai) "อัปโหลดสูติบัตร" else "Upload birth certificate",
                subtitle = if (isThai) "ถ่ายรูปสูติบัตรเพื่อกรอกข้อมูลลูกให้อัตโนมัติ" else "Snap a photo to auto-fill your child's info",
                onClick = onUploadCertificate
            )
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}
