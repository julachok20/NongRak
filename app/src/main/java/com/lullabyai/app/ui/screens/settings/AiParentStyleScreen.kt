@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.ParentAiStyle
import com.lullabyai.app.data.repository.ParentStyleRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import kotlinx.coroutines.launch

/**
 * "NongRak AI รู้จักคุณ": the note Typhoon keeps on how to talk to this parent, shown word for word.
 *
 * A profile of a person has to be visible and in their control (PDPA), so this screen is the
 * parent's view of it — read it, switch learning off, or wipe it.
 */
@Composable
fun AiParentStyleScreen(navController: NavController) {
    val isThai = LocalStrings.current.isThai
    val scope = rememberCoroutineScope()
    val repo = remember { ParentStyleRepository() }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<ParentAiStyle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.get().onSuccess { profile = it }.onFailure { error = it.message }
        loading = false
    }

    Scaffold(topBar = {
        LullabyTopBar(
            title = if (isThai) "NongRak AI รู้จักคุณ" else "How NongRak AI knows you",
            onBack = { navController.popBackStack() },
            navController = navController
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai)
                    "ระหว่างแชท Typhoon จะสรุปสไตล์การคุยของคุณสั้นๆ เช่น ชอบคำตอบสั้น หรืออยากได้ขั้นตอนละเอียด " +
                        "เพื่อให้น้องรักตอบได้ถูกใจขึ้น บันทึกนี้เก็บในบัญชีของคุณ ใช้เฉพาะในแชท ไม่แชร์ให้ใคร " +
                        "และคุณปิดหรือล้างได้ทุกเมื่อ"
                else
                    "While you chat, Typhoon keeps a short note on how you like to talk — short answers or " +
                        "step-by-step detail, for example — so NongRak answers the way you prefer. It stays in your " +
                        "account, is used only in chat, is never shared, and you can switch it off or clear it any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val enabled = profile?.enabled ?: true
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isThai) "ให้ NongRak AI เรียนรู้สไตล์ของฉัน" else "Let NongRak AI learn my style",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    enabled = !loading && !saving,
                    onCheckedChange = { wanted ->
                        saving = true
                        error = null
                        scope.launch {
                            repo.setEnabled(wanted).onSuccess { profile = it }.onFailure { error = it.message }
                            saving = false
                        }
                    }
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (isThai) "สิ่งที่ NongRak AI รู้เกี่ยวกับคุณ" else "What NongRak AI knows about you",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val note = profile?.style.orEmpty()
                    Text(
                        when {
                            loading -> if (isThai) "กำลังโหลด…" else "Loading…"
                            note.isNotBlank() -> note
                            !enabled -> if (isThai) "ปิดการเรียนรู้อยู่" else "Learning is switched off"
                            else -> if (isThai) "ยังไม่มีข้อมูล คุยกับน้องรักอีกสักพักนะคะ" else "Nothing yet — chat a little more."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    profile?.updatedAt?.takeIf { note.isNotBlank() }?.let {
                        Text(
                            (if (isThai) "อัปเดตล่าสุด " else "Updated ") + it.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = !loading && !saving && !profile?.style.isNullOrBlank()
            ) { Text(if (isThai) "ล้างข้อมูลที่เรียนรู้" else "Clear what was learnt") }

            error?.let {
                Text(
                    (if (isThai) "บันทึกไม่สำเร็จ: " else "Could not save: ") + it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(if (isThai) "ล้างข้อมูลที่เรียนรู้?" else "Clear what was learnt?") },
            text = {
                Text(
                    if (isThai) "น้องรักจะเริ่มเรียนรู้สไตล์ของคุณใหม่ตั้งแต่ต้น"
                    else "NongRak will start learning your style from scratch."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    saving = true
                    error = null
                    scope.launch {
                        repo.reset().onSuccess { profile = it }.onFailure { error = it.message }
                        saving = false
                    }
                }) { Text(if (isThai) "ล้าง" else "Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") }
            }
        )
    }
}
