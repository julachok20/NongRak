package com.lullabyai.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Primary
import java.time.LocalTime

/**
 * Time picker popup used by every doctor-side "เลือกเวลา" field.
 *
 * The bare Material3 [TimePicker] dial was slow for clinic hours, which are always on the half
 * hour: dragging a dial to 18:30 takes several passes. This shows tappable half-hour chips as the
 * primary path and keeps keyboard entry behind a toggle for the odd 18:15 case.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeSelectDialog(
    initial: LocalTime?,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    title: String = if (isThai) "เลือกเวลา" else "Select time",
    stepMinutes: Int = 30,
    rangeStart: LocalTime = LocalTime.of(6, 0),
    rangeEnd: LocalTime = LocalTime.of(21, 30)
) {
    val start = initial ?: LocalTime.of(9, 0)
    var selected by remember { mutableStateOf(start) }
    var keyboardMode by remember { mutableStateOf(start.minute % stepMinutes != 0) }
    val keyboardState = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = true
    )

    val slots = remember(stepMinutes, rangeStart, rangeEnd) {
        buildList {
            var t = rangeStart
            while (!t.isAfter(rangeEnd)) {
                add(t)
                val next = t.plusMinutes(stepMinutes.toLong())
                if (next <= t) break // wrapped past midnight
                t = next
            }
        }
    }

    val current = if (keyboardMode) LocalTime.of(keyboardState.hour, keyboardState.minute) else selected

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text(if (isThai) "ตกลง" else "OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AccessTime, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "%02d:%02d".format(current.hour, current.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                if (keyboardMode) {
                    TimeInput(state = keyboardState)
                } else {
                    FlowRow(
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slots.forEach { slot ->
                            val label = "%02d:%02d".format(slot.hour, slot.minute)
                            FilterChip(
                                selected = slot == selected,
                                onClick = { selected = slot },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = { keyboardMode = !keyboardMode }) {
                    Icon(
                        if (keyboardMode) Icons.Filled.AccessTime else Icons.Filled.Keyboard,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (keyboardMode) {
                            if (isThai) "เลือกจากช่วงเวลา" else "Pick from slots"
                        } else {
                            if (isThai) "ระบุเวลาเอง" else "Enter exact time"
                        }
                    )
                }
            }
        }
    )
}
