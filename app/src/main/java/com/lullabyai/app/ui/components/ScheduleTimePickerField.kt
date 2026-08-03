package com.lullabyai.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Tap-to-pick time field — stores `HH:mm`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "เลือกเวลา",
    isThai: Boolean = true,
    enabled: Boolean = true
) {
    val initial = remember(value) {
        runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm")) }
            .getOrDefault(LocalTime.of(9, 0))
    }
    var showPicker by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    OutlinedTextField(
        value = value,
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
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = if (enabled) Primary else Outline)
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
        TimeSelectDialog(
            initial = initial,
            isThai = isThai,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                onValueChange("%02d:%02d".format(picked.hour, picked.minute))
                showPicker = false
            }
        )
    }
}
