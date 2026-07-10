package com.lullabyai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Primary

/** A pending "sync child" request awaiting the owner's confirm/reject. Shared by Dashboard and
 *  Notifications so an owner sees it wherever they happen to look, not only inside Notifications. */
@Composable
fun SyncRequestCard(childName: String, onApprove: () -> Unit, onReject: () -> Unit) {
    var resolved by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.SyncAlt, null, tint = Primary, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("มีคำขอเชื่อมข้อมูลลูก", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("ลูกชื่อ $childName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!resolved) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { resolved = true; onReject() }, modifier = Modifier.weight(1f)) { Text("ปฏิเสธ") }
                    Button(onClick = { resolved = true; onApprove() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("ยืนยัน") }
                }
            }
        }
    }
}
