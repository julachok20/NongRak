@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.infoask.InfoAskAnswer
import com.lullabyai.app.data.infoask.InfoAskCatalog
import com.lullabyai.app.data.infoask.InfoAskKind
import com.lullabyai.app.data.infoask.InfoAskPrompt
import com.lullabyai.app.data.infoask.InfoAskSubject
import com.lullabyai.app.ui.theme.LocalDarkTheme
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val QBlue = Color(0xFF2563EB)
private val QBlueDeep = Color(0xFF1D4ED8)
private val QNavy = Color(0xFF1E3A8A)
private val QBlue50 = Color(0xFFEFF6FF)
private val QBlue100 = Color(0xFFDBEAFE)
private val QBlue200 = Color(0xFFBFDBFE)
private val QSlate700 = Color(0xFF334155)
private val QSlate500 = Color(0xFF64748B)
private val QSlate400 = Color(0xFF94A3B8)

/**
 * Stitch "ask-question-banner" (Child Hub screen 1447586054197311646): the open info-asks answered
 * in place, one question per page. Answers are held until the last page and saved together through
 * [ChildViewModel.answerInfoAsk]; questions left blank stay open for later.
 */
@Composable
fun ChildHubQuestionCard(childViewModel: ChildViewModel, childName: String, isThai: Boolean) {
    val state by childViewModel.uiState.collectAsState()
    val prompts = state.infoAsks
    if (prompts.isEmpty()) return
    val context = LocalContext.current
    val dark = LocalDarkTheme.current

    val key = prompts.joinToString { it.fieldId }
    var page by remember(key) { mutableIntStateOf(0) }
    var texts by remember(key) { mutableStateOf(mapOf<String, String>()) }
    var chips by remember(key) { mutableStateOf(mapOf<String, String>()) }
    var pickDate by remember(key) { mutableStateOf(false) }
    var triedSave by remember(key) { mutableStateOf(false) }

    val prompt = prompts[page.coerceIn(0, prompts.lastIndex)]
    val isLast = page >= prompts.lastIndex
    fun answersWithout(skipId: String?) = prompts.map {
        if (it.fieldId == skipId) InfoAskAnswer(it.fieldId)
        else InfoAskAnswer(it.fieldId, texts[it.fieldId].orEmpty(), chips[it.fieldId])
    }
    fun anyFilled(answers: List<InfoAskAnswer>) = prompts.indices.any { InfoAskCatalog.isFilled(prompts[it], answers[it]) }
    fun finish(answers: List<InfoAskAnswer>) {
        if (anyFilled(answers)) {
            triedSave = true
            childViewModel.answerInfoAsk(context, answers, isThai)
        } else {
            childViewModel.dismissInfoAsk(context)
        }
    }
    val currentFilled = InfoAskCatalog.isFilled(
        prompt, InfoAskAnswer(prompt.fieldId, texts[prompt.fieldId].orEmpty(), chips[prompt.fieldId])
    )

    val shape = RoundedCornerShape(16.dp)
    val ink = if (dark) MaterialTheme.colorScheme.onSurface else QNavy
    val soft = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else QSlate500
    val tileBg = if (dark) MaterialTheme.colorScheme.surface else Color.White
    val tileBorder = if (dark) QBlue.copy(alpha = 0.3f) else QBlue100

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = QBlue.copy(alpha = 0.10f), spotColor = QBlue.copy(alpha = 0.10f))
            .clip(shape)
            .background(
                if (dark) Brush.linearGradient(listOf(QBlue.copy(alpha = 0.18f), QBlue.copy(alpha = 0.08f)))
                else Brush.linearGradient(listOf(QBlue50, Color(0xFFE0F2FE), Color(0xFFF4F8FD)))
            )
            .border(1.dp, QBlue200.copy(alpha = if (dark) 0.25f else 0.6f), shape)
    ) {
        Box(Modifier.align(Alignment.BottomEnd).offset(16.dp, 16.dp).size(80.dp).clip(CircleShape).background(QBlue200.copy(alpha = if (dark) 0.08f else 0.45f)))

        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: icon + title + chip, then the actual question
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).shadow(1.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(QBlue, QBlueDeep)))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Feedback, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            questionTitle(prompt, childName, isThai),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            questionTag(prompt, isThai),
                            fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = QBlue, maxLines = 1,
                            modifier = Modifier.clip(CircleShape).background(QBlue100.copy(alpha = if (dark) 0.2f else 1f)).padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    Text(prompt.question(isThai), fontSize = 11.sp, lineHeight = 15.sp, color = soft, modifier = Modifier.padding(top = 2.dp))
                }
            }

            // Answer area
            when (prompt.kind) {
                InfoAskKind.CHIPS -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    prompt.chips.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { chip ->
                                val selected = chips[prompt.fieldId] == chip.id
                                val tileShape = RoundedCornerShape(12.dp)
                                Row(
                                    Modifier.weight(1f).fillMaxHeight().clip(tileShape)
                                        .background(if (selected) (if (dark) QBlue.copy(alpha = 0.25f) else QBlue50) else tileBg.copy(alpha = 0.9f))
                                        .border(if (selected) 1.5.dp else 1.dp, if (selected) QBlue else tileBorder, tileShape)
                                        .clickable { chips = chips + (prompt.fieldId to chip.id) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        chip.label(isThai), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        color = if (selected) QBlue else if (dark) MaterialTheme.colorScheme.onSurface else QSlate700,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                    )
                                    if (selected) Icon(Icons.Filled.Check, null, tint = QBlue, modifier = Modifier.size(14.dp))
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                InfoAskKind.TEXT, InfoAskKind.NUMBER -> {
                    val value = texts[prompt.fieldId].orEmpty()
                    BasicTextField(
                        value = value,
                        onValueChange = { texts = texts + (prompt.fieldId to it) },
                        singleLine = prompt.kind == InfoAskKind.NUMBER,
                        textStyle = TextStyle(fontSize = 12.sp, color = if (dark) MaterialTheme.colorScheme.onSurface else QSlate700),
                        cursorBrush = SolidColor(QBlue),
                        keyboardOptions = KeyboardOptions(keyboardType = if (prompt.kind == InfoAskKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tileBg)
                                    .border(1.dp, tileBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                if (value.isEmpty()) {
                                    Text(
                                        prompt.hint(isThai) ?: if (isThai) "หรือระบุเพิ่มเติม เช่น งอแงเล็กน้อย..." else "Or add a note, e.g. a little fussy...",
                                        fontSize = 12.sp, color = QSlate400
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                InfoAskKind.DATE -> {
                    val value = texts[prompt.fieldId].orEmpty()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tileBg)
                            .border(1.dp, tileBorder, RoundedCornerShape(12.dp))
                            .clickable { pickDate = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            value.ifBlank { if (isThai) "เลือกวันที่" else "Pick a date" },
                            fontSize = 12.sp, color = if (value.isBlank()) QSlate400 else if (dark) MaterialTheme.colorScheme.onSurface else QSlate700,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.CalendarMonth, null, tint = QBlue, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (triedSave && !state.infoAskSaving) {
                state.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
            }

            // Footer: progress + skip + next / save
            HorizontalDivider(color = if (dark) QBlue.copy(alpha = 0.2f) else QBlue100)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (prompts.size in 2..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        prompts.indices.forEach { i ->
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == page) QBlue else QBlue200))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (isThai) "คำถาม ${page + 1} จาก ${prompts.size}" else "Question ${page + 1} of ${prompts.size}",
                    fontSize = 10.sp, fontWeight = FontWeight.Medium, color = soft, modifier = Modifier.weight(1f)
                )
                Text(
                    if (isThai) "ข้าม" else "Skip",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = QSlate400,
                    modifier = Modifier.clip(CircleShape)
                        .clickable(enabled = !state.infoAskSaving) {
                            if (isLast) finish(answersWithout(prompt.fieldId)) else page++
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                val enabled = !state.infoAskSaving && (if (isLast) anyFilled(answersWithout(null)) else currentFilled)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) QBlue else QBlue.copy(alpha = 0.4f))
                        .clickable(enabled = enabled) { if (isLast) finish(answersWithout(null)) else page++ }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (state.infoAskSaving) {
                        CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isLast) (if (isThai) "บันทึก" else "Save") else (if (isThai) "คำถามถัดไป" else "Next question"),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (pickDate) {
        val zone = ZoneId.systemDefault()
        val initial = runCatching { LocalDate.parse(texts[prompt.fieldId].orEmpty()).atStartOfDay(zone).toInstant().toEpochMilli() }
            .getOrDefault(LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli())
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        texts = texts + (prompt.fieldId to Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString())
                    }
                    pickDate = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

private fun questionTitle(prompt: InfoAskPrompt, childName: String, isThai: Boolean): String = when (prompt.subject) {
    InfoAskSubject.USER -> if (isThai) "ขอข้อมูลของคุณเพิ่มอีกนิดนะคะ" else "A little more about you"
    InfoAskSubject.CHILD -> if (isThai) "วันนี้ น้อง $childName เป็นอย่างไรบ้าง?" else "How is $childName today?"
}

private fun questionTag(prompt: InfoAskPrompt, isThai: Boolean): String = when {
    prompt.healthBook -> if (isThai) "สมุดสุขภาพ" else "Health book"
    prompt.subject == InfoAskSubject.USER -> if (isThai) "ข้อมูลผู้ปกครอง" else "Guardian"
    else -> if (isThai) "เช็กสภาวะประจำวัน" else "Daily check"
}
