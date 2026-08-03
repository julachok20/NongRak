package com.lullabyai.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.model.ChatMode
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.RelationshipLabels

/**
 * Chatbot welcome (empty) state, matched to Stitch "AI Chat Assistant (Updated Footer)":
 * mascot in a ring → greeting + subtitle → "Call with me" card → four suggested questions.
 *
 * Two deliberate deviations from the Stitch mock, both because the app already owns the behaviour:
 * the greeting uses the parent's chosen relationship term (RelationshipLabels) instead of a
 * hard-coded "คุณแม่", and the mode selector is added below the call card (the parent needs a way
 * to reach โหมดเล่านิทาน / คุยกับเด็ก).
 */
@Composable
fun ChatWelcomeState(
    isThai: Boolean,
    relationship: String?,
    isSpeaking: Boolean,
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onStartCall: () -> Unit,
    onSuggestion: (String) -> Unit
) {
    val term = if (isThai) RelationshipLabels.thaiAddress(relationship) else RelationshipLabels.englishAddress(relationship)
    val suggestions: List<Pair<ImageVector, String>> = if (isThai) listOf(
        Icons.Filled.Restaurant to "ลูกไม่ค่อยทานข้าวทำอย่างไรดี?",
        Icons.Filled.Bedtime to "ขอตารางการนอนที่เหมาะสม",
        Icons.Filled.MedicalServices to "ลูกมีไข้ต่ำๆ ควรดูแลเบื้องต้นอย่างไร?",
        Icons.Filled.ChildCare to "พัฒนาการวัย 2 ขวบเป็นอย่างไร?"
    ) else listOf(
        Icons.Filled.Restaurant to "My child barely eats — what should I do?",
        Icons.Filled.Bedtime to "Suggest a good sleep schedule",
        Icons.Filled.MedicalServices to "My child has a mild fever — first steps?",
        Icons.Filled.ChildCare to "What milestones should a 2-year-old hit?"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AiAvatarView(isSpeaking = isSpeaking, modifier = Modifier.size(128.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (isThai) "สวัสดีค่ะ$term! น้องรัก AI พร้อมเป็นผู้ช่วยดูแลลูกน้อยของคุณแล้วค่ะ"
                else "Hello, $term! NongRak AI is ready to help you care for your little one.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                if (isThai) "ปรึกษาปัญหาการเลี้ยงดู สุขภาพ หรือโภชนาการได้ตลอด 24 ชั่วโมง"
                else "Ask about parenting, health or nutrition — any time, 24 hours a day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // "Call with me" card
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .background(Primary.copy(0.06f), RoundedCornerShape(12.dp))
                .border(1.dp, Primary.copy(0.16f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isThai) "โทรคุยกับน้องรัก" else "Call with me",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Button(
                onClick = onStartCall,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isThai) "เริ่มโทร" else "Start call",
                    fontWeight = FontWeight.Bold, color = Color.White
                )
            }
            ChatModeChips(mode = mode, onSelect = onModeChange, isThai = isThai)
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            suggestions.forEach { (icon, text) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSuggestion(text) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Mode selector — same row on the welcome card and on the call screen. */
@Composable
fun ChatModeChips(mode: ChatMode, onSelect: (ChatMode) -> Unit, isThai: Boolean, onDark: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChatMode.entries.forEach { m ->
            val selected = m == mode
            FilterChip(
                selected = selected,
                onClick = { onSelect(m) },
                label = { Text(m.label(isThai), style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = Color.White,
                    labelColor = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = if (onDark) Color.White.copy(0.15f) else Color.Transparent
                )
            )
        }
    }
}
