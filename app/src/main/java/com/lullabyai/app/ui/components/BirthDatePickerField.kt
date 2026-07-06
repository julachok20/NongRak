package com.lullabyai.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val THAI_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
private val ENG_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun formatBirthDateDisplay(iso: String, isThai: Boolean = true): String = try {
    val p = iso.split("-")
    val day = p[2].toInt()
    val month = p[1].toInt() - 1
    val year = p[0].toInt()
    if (isThai) "$day ${THAI_MONTHS[month]} ${year + 543}" else "$day ${ENG_MONTHS[month]} $year"
} catch (_: Exception) {
    iso
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "แตะเพื่อเลือกวันเกิดจากปฏิทิน",
    isThai: Boolean = true,
    enabled: Boolean = true,
    fieldColors: @Composable () -> androidx.compose.material3.TextFieldColors = {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
        )
    }
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val maxMillis = remember { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val minMillis = remember { today.minusYears(4).atStartOfDay(zone).toInstant().toEpochMilli() }

    val initialMillis = remember(value) {
        runCatching { LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli() }
            .getOrNull() ?: maxMillis
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
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) {
                showPicker = true
            },
        placeholder = { Text(placeholder, color = Outline) },
        trailingIcon = {
            IconButton(onClick = { if (enabled) showPicker = true }, enabled = enabled) {
                Icon(Icons.Filled.CalendarToday, contentDescription = "เลือกวันที่", tint = Outline)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors()
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
