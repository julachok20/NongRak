package com.lullabyai.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.model.Child

/**
 * Which of [child]'s fields are missing that the AI analysis screens actually read
 * (see ChatRepository.buildChildContext) — weight/height feed Growth, allergies feed
 * Health/Disease Risk. Empty when nothing is missing, so callers can skip showing the dialog.
 */
fun missingChildInfo(child: Child?, hasAllergyRecords: Boolean, isThai: Boolean): List<String> {
    if (child == null) return emptyList()
    return buildList {
        if (child.weightKg == null) add(if (isThai) "น้ำหนัก" else "Weight")
        if (child.heightCm == null) add(if (isThai) "ส่วนสูง" else "Height")
        if (!hasAllergyRecords) add(if (isThai) "ประวัติการแพ้" else "Allergy history")
    }
}

/**
 * Nudges the parent to fill in gaps that make AI analysis less accurate. Per product decision,
 * this has no permanent dismissal — "Skip" only closes it for the current screen visit, and it
 * reappears the next time this screen loads until the fields are actually filled in.
 */
@Composable
fun MissingChildInfoPrompt(
    missingFields: List<String>,
    isThai: Boolean,
    onFillIn: () -> Unit,
    onSkip: () -> Unit
) {
    if (missingFields.isEmpty()) return
    AlertDialog(
        onDismissRequest = onSkip,
        icon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (isThai) "ข้อมูลยังไม่ครบ" else "Some info is missing", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isThai) "การกรอกข้อมูลต่อไปนี้จะช่วยให้ AI วิเคราะห์ได้แม่นยำขึ้น:"
                    else "Filling these in helps the AI analyze more accurately:",
                    style = MaterialTheme.typography.bodyMedium
                )
                missingFields.forEach { field -> Text("• $field", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onFillIn) { Text(if (isThai) "กรอกข้อมูล" else "Fill in") } },
        dismissButton = { TextButton(onClick = onSkip) { Text(if (isThai) "ข้าม" else "Skip") } }
    )
}
