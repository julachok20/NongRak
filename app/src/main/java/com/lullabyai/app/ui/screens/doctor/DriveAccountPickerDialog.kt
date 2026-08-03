package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Primary

@Composable
fun DriveAccountPickerDialog(
    accounts: List<String>,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onAccountSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isThai) "เลือกบัญชี Google สำหรับ Drive" else "Choose Google account for Drive",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    if (isThai) "เลือกบัญชีที่ต้องการใช้สำรองรายละเอียดคนไข้"
                    else "Pick the account to store patient detail backups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                accounts.forEach { email ->
                    TextButton(
                        onClick = { onAccountSelected(email) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(email, color = Primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ยกเลิก" else "Cancel")
            }
        }
    )
}
