@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.infoask.InfoAskAnswer
import com.lullabyai.app.data.infoask.InfoAskCatalog
import com.lullabyai.app.data.infoask.InfoAskKind
import com.lullabyai.app.data.infoask.InfoAskPrompt
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every currently open info-ask on one page. Save writes filled answers; blank ones stay open.
 * Skip hides the whole batch for the rest of today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoAskScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prompts = childState.infoAsks
    val promptKey = prompts.joinToString { it.fieldId }

    LaunchedEffect(childState.child?.id) {
        if (prompts.isEmpty()) childViewModel.loadInfoAsk(context, isThai)
    }

    var texts by remember(promptKey) { mutableStateOf(prompts.associate { it.fieldId to "" }) }
    var chips by remember(promptKey) { mutableStateOf(prompts.associate { it.fieldId to null as String? }) }
    var dateFieldId by remember { mutableStateOf<String?>(null) }

    ChildScreenScaffold(
        title = if (isThai) "ขอข้อมูลเพิ่มเติม" else "More information",
        currentTab = "overview",
        navController = navController
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (prompts.isEmpty()) {
                Text(
                    if (isThai) "ไม่มีคำถามในตอนนี้" else "No question right now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onBack) {
                    Text(if (isThai) "กลับ" else "Back", color = Primary)
                }
                return@Column
            }

            val childName = childState.child?.name.orEmpty()
            val subject = InfoAskCatalog.bannerSubjectName(prompts, childName, isThai)
            Text(
                if (isThai) "ขอข้อมูลเพิ่มเติม" else "Asking more information",
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isThai) "${prompts.size} คำถามเกี่ยวกับ $subject"
                else "${prompts.size} questions on $subject",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isThai) "ตอบได้ทุกข้อในหน้านี้ — ช่องที่ตรงกับสมุดสุขภาพน้องรักจะถูกเขียนลงสมุดด้วย ข้ามข้อที่ยังไม่พร้อมได้"
                else "Answer any of these here. Fields that match the NongRak Health Book are written there too. Skip ones you are not ready for.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            prompts.forEachIndexed { index, prompt ->
                InfoAskQuestionCard(
                    index = index + 1,
                    total = prompts.size,
                    prompt = prompt,
                    isThai = isThai,
                    text = texts[prompt.fieldId].orEmpty(),
                    chipId = chips[prompt.fieldId],
                    onText = { texts = texts + (prompt.fieldId to it) },
                    onChip = { chips = chips + (prompt.fieldId to it) },
                    onPickDate = { dateFieldId = prompt.fieldId }
                )
            }

            childState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            val canSave = prompts.any { p ->
                InfoAskCatalog.isFilled(
                    p,
                    InfoAskAnswer(p.fieldId, texts[p.fieldId].orEmpty(), chips[p.fieldId])
                )
            }
            PrimaryButton(
                text = if (isThai) "บันทึก" else "Save",
                onClick = {
                    val answers = prompts.map {
                        InfoAskAnswer(it.fieldId, texts[it.fieldId].orEmpty(), chips[it.fieldId])
                    }
                    childViewModel.answerInfoAsk(context, answers, isThai) { onBack() }
                },
                enabled = canSave,
                isLoading = childState.infoAskSaving
            )
            TextButton(
                onClick = { childViewModel.dismissInfoAsk(context); onBack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isThai) "ข้ามทั้งหมด" else "Skip all", color = MaterialTheme.colorScheme.outline)
            }
        }
    }

    dateFieldId?.let { fieldId ->
        val zone = ZoneId.systemDefault()
        val current = texts[fieldId].orEmpty()
        val initial = runCatching { LocalDate.parse(current).atStartOfDay(zone).toInstant().toEpochMilli() }
            .getOrDefault(LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli())
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { dateFieldId = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        texts = texts + (fieldId to Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString())
                    }
                    dateFieldId = null
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { dateFieldId = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoAskQuestionCard(
    index: Int,
    total: Int,
    prompt: InfoAskPrompt,
    isThai: Boolean,
    text: String,
    chipId: String?,
    onText: (String) -> Unit,
    onChip: (String) -> Unit,
    onPickDate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (isThai) "ข้อ $index จาก $total" else "Question $index of $total",
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
            Text(prompt.question(isThai), style = MaterialTheme.typography.titleMedium)
            if (prompt.healthBook) {
                Text(
                    if (isThai) "ช่องนี้ตรงกับสมุดสุขภาพน้องรัก"
                    else "This field is on the NongRak Health Book",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (prompt.kind) {
                InfoAskKind.CHIPS -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        prompt.chips.forEach { chip ->
                            val selected = chipId == chip.id
                            FilterChip(
                                selected = selected,
                                onClick = { onChip(chip.id) },
                                label = {
                                    Text(chip.label(isThai), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                InfoAskKind.TEXT, InfoAskKind.NUMBER -> {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onText,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isThai) "คำตอบ" else "Answer") },
                        placeholder = { prompt.hint(isThai)?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (prompt.kind == InfoAskKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text
                        ),
                        singleLine = prompt.kind == InfoAskKind.NUMBER
                    )
                }
                InfoAskKind.DATE -> {
                    val shown = text.ifBlank { if (isThai) "เลือกวันที่" else "Pick a date" }
                    OutlinedTextField(
                        value = shown,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isThai) "วันที่" else "Date") },
                        trailingIcon = {
                            TextButton(onClick = onPickDate) {
                                Text(if (isThai) "เลือก" else "Pick", color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }
    }
}
