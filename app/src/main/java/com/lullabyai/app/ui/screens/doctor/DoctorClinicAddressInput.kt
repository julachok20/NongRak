package com.lullabyai.app.ui.screens.doctor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

enum class ClinicAddressInputMode { MANUAL, MAPS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorClinicAddressInput(
    isThai: Boolean,
    clinicName: String,
    address: String,
    onAddressChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ClinicAddressInputMode.MANUAL) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (isThai) "ที่อยู่สถานพยาบาล" else "Practice address",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == ClinicAddressInputMode.MANUAL,
                onClick = { mode = ClinicAddressInputMode.MANUAL },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Filled.EditLocationAlt, null, modifier = Modifier.size(18.dp)) }
            ) {
                Text(if (isThai) "กรอกที่อยู่" else "Type address", style = MaterialTheme.typography.labelSmall)
            }
            SegmentedButton(
                selected = mode == ClinicAddressInputMode.MAPS,
                onClick = { mode = ClinicAddressInputMode.MAPS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp)) }
            ) {
                Text(if (isThai) "Google Maps" else "Google Maps", style = MaterialTheme.typography.labelSmall)
            }
        }
        when (mode) {
            ClinicAddressInputMode.MANUAL -> {
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            if (isThai) "เลขที่ ถนน แขวง/ตำบล จังหวัด รหัสไปรษณีย์"
                            else "Street, district, province, postal code"
                        )
                    }
                )
            }
            ClinicAddressInputMode.MAPS -> {
                Text(
                    if (isThai)
                        "เปิด Google Maps เพื่อค้นหาหรือปักหมุดคลินิก แล้วคัดลอกที่อยู่มาวางด้านล่าง"
                    else
                        "Open Google Maps to search or pin your clinic, then paste the address below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Outline
                )
                OutlinedButton(
                    onClick = {
                        val query = listOf(clinicName, address).filter { it.isNotBlank() }.joinToString(", ")
                            .ifBlank { if (isThai) "คลินิก" else "clinic" }
                        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Map, null, tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "เปิด Google Maps" else "Open Google Maps", color = Primary)
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(if (isThai) "ที่อยู่จากแผนที่" else "Address from map") }
                )
            }
        }
    }
}
