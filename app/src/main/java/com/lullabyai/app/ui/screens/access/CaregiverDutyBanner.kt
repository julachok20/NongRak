package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.handoff.CareHandoff
import com.lullabyai.app.data.repository.HandoffRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * What a caregiver has to do for this child, on their own home screen.
 *
 * Three states, because a caregiver opening the app asks one question — "what am I supposed to do
 * now?": a handover waiting to be accepted, a shift in progress with its remaining ticks, or no
 * handover at all, in which case their job is the quick record.
 *
 * Only rows addressed to this user are considered, so a parent who is also ticked as a caregiver
 * never sees a briefing they sent themselves as a duty.
 */
@Composable
fun CaregiverDutyBanner(
    childId: String,
    thai: Boolean,
    onOpenChecklist: () -> Unit,
    onQuickRecord: () -> Unit
) {
    var mine by remember(childId) { mutableStateOf<CareHandoff?>(null) }
    var failed by remember(childId) { mutableStateOf(false) }

    LaunchedEffect(childId) {
        if (childId.isBlank()) return@LaunchedEffect
        val repo = HandoffRepository()
        while (true) {
            try {
                val me = repo.userId
                mine = repo.list(childId).firstOrNull {
                    it.recipientId == me && it.status in listOf("pending", "active")
                }
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never claim "nothing to do" when the check itself failed — say it is unknown.
                mine = null
                failed = true
            }
            delay(30000)
        }
    }

    val handoff = mine
    val title = when {
        failed -> if (thai) "ยังตรวจสอบงานดูแลไม่ได้" else "Could not check your care tasks"
        handoff == null -> if (thai) "บันทึกกิจกรรมด่วนสำหรับผู้ดูแล" else "Caregiver quick record"
        handoff.status == "pending" -> if (thai) "คุณได้รับการส่งต่อการดูแล" else "A handover was sent to you"
        else -> if (thai) "กำลังดูแลอยู่ตอนนี้" else "Shift in progress"
    }
    val detail = when {
        failed -> if (thai) "แตะเพื่อเปิดรายการและลองอีกครั้ง" else "Tap to open the list and try again"
        handoff == null ->
            if (thai) "ยังไม่มีรายการส่งต่อ บันทึกสิ่งที่น้องทำระหว่างวันได้เลย"
            else "No handover right now — log what the child is doing"
        handoff.status == "pending" ->
            if (thai) "กดรับช่วงดูแลเพื่อเริ่มติ๊กรายการ ${handoff.tasks.size} ข้อ"
            else "Take over to start ticking ${handoff.tasks.size} items"
        else -> {
            val done = handoff.tasks.count { it.done }
            if (thai) "ทำแล้ว $done / ${handoff.tasks.size} รายการ" else "$done of ${handoff.tasks.size} done"
        }
    }
    val action = if (handoff == null && !failed) {
        if (thai) "บันทึกเลย" else "Record"
    } else {
        if (thai) "ดูรายการ" else "Open list"
    }
    val onClick = if (handoff == null && !failed) onQuickRecord else onOpenChecklist

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (handoff?.status == "pending") MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (handoff == null) Icons.Filled.EditNote else Icons.Filled.AssignmentTurnedIn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
