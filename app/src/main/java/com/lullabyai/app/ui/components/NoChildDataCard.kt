package com.lullabyai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Primary

/** Shown on ข้อมูลลูก / Statistics when the account has no child yet, instead of a blank/zeroed page. */
@Composable
fun NoChildDataCard(isThai: Boolean, onAddChild: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (isThai) "ยังไม่มีข้อมูลลูกในขณะนี้" else "No child data yet",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isThai) "กรุณาเพิ่มข้อมูลลูก" else "Please add a child",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddChild, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text(if (isThai) "เพิ่มลูก" else "Add child")
            }
        }
    }
}

/** Shown instead of [NoChildDataCard] when the children list is empty because the fetch actually
 *  failed (network/auth blip), not because the account has zero children — offers retry instead of
 *  wrongly telling a user who already has a child to add one. */
@Composable
fun ChildLoadErrorCard(isThai: Boolean, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (isThai) "โหลดข้อมูลไม่สำเร็จ" else "Couldn't load your data",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isThai) "ตรวจสอบการเชื่อมต่อแล้วลองอีกครั้ง" else "Check your connection and try again",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text(if (isThai) "ลองอีกครั้ง" else "Retry")
            }
        }
    }
}
