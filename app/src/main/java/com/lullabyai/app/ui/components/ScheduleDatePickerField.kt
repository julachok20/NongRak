package com.lullabyai.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tap-to-pick date field for appointments (defaults: today → +365 days). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "เลือกวันที่",
    isThai: Boolean = true,
    enabled: Boolean = true,
    minDate: LocalDate = LocalDate.now(),
    maxDate: LocalDate = LocalDate.now().plusYears(1)
) {
    val zone = remember { ZoneId.systemDefault() }
    val minMillis = remember(minDate) { minDate.atStartOfDay(zone).toInstant().toEpochMilli() }
    val maxMillis = remember(maxDate) { maxDate.atStartOfDay(zone).toInstant().toEpochMilli() }

    val initialMillis = remember(value) {
        runCatching { LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli() }
            .getOrNull() ?: minMillis
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in minMillis..maxMillis
        }
    )

    var showPicker by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    OutlinedTextField(
        value = if (value.isNotBlank()) formatBirthDateDisplay(value, isThai) else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = label?.let { { Text(it) } },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) {
                showPicker = true
            },
        placeholder = { Text(placeholder, color = Outline) },
        trailingIcon = {
            IconButton(onClick = { if (enabled) showPicker = true }, enabled = enabled) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = if (enabled) Primary else Outline)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                        onValueChange(date.toString())
                    }
                    showPicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
