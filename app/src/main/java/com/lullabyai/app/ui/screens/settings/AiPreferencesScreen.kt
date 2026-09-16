package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.ParentAiStyle
import com.lullabyai.app.data.repository.ParentStyleRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import kotlinx.coroutines.launch

/** One option. Null key = automatic. Keys must match the parent_ai_style check constraints. */
private data class Choice(val key: String?, val th: String, val en: String, val exTh: String, val exEn: String)

private val TONES = listOf(
    Choice(null, "อัตโนมัติ", "Automatic", "ปรับตามสไตล์ที่ NongRak เรียนรู้จากการคุยกับคุณ", "Follows what NongRak learnt from your chats"),
    Choice("warm", "อบอุ่น ให้กำลังใจ", "Warm and encouraging", "\"ไม่ต้องกังวลนะคะ คุณแม่ทำได้ดีมากแล้ว…\"", "\"Don't worry, you're doing great…\""),
    Choice("cheerful", "สดใส เป็นกันเอง", "Cheerful and casual", "\"โอเคเลยค่ะ ลองแบบนี้ดูนะ…\"", "\"Sure thing! Try this…\""),
    Choice("professional", "สุภาพ ตรงประเด็น", "Calm and to the point", "\"แนะนำให้ทำดังนี้…\"", "\"Here's what to do…\"")
)

private val LENGTHS = listOf(
    Choice(null, "อัตโนมัติ", "Automatic", "ปรับตามคำถาม", "Depends on the question"),
    Choice("short", "สั้น กระชับ", "Short", "2-4 ประโยค เฉพาะสิ่งที่ต้องทำ", "2-4 sentences, just what to do"),
    Choice("balanced", "พอดี", "Balanced", "สิ่งที่ควรทำ พร้อมเหตุผลสั้นๆ", "What to do and a short why"),
    Choice("detailed", "ละเอียด เป็นขั้นตอน", "Detailed, step by step", "ขั้นตอน เหตุผล และสิ่งที่ต้องสังเกต", "Steps, reasons and what to watch for")
)

/**
 * ตั้งค่า AI เอง (Custom AI Preferences): the parent's own pick of NongRak's tone and answer length,
 * saved on parent_ai_style and applied by ai-chat above the learnt note. Each tap saves at once.
 */
@Composable
fun AiPreferencesScreen(navController: NavController) {
    val isThai = LocalStrings.current.isThai
    val scope = rememberCoroutineScope()
    val repo = remember { ParentStyleRepository() }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<ParentAiStyle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repo.get().onSuccess { profile = it }.onFailure { error = it.message }
        loading = false
    }

    fun save(tone: String?, length: String?) {
        saving = true
        error = null
        scope.launch {
            repo.setPreferences(tone, length).onSuccess { profile = it }.onFailure { error = it.message }
            saving = false
        }
    }

    Scaffold(topBar = {
        LullabyTopBar(
            title = if (isThai) "ตั้งค่า AI เอง" else "AI preferences",
            onBack = { navController.popBackStack() },
            navController = navController
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isThai) "เลือกน้ำเสียงและความยาวคำตอบของน้องรักในแชท ถ้าเลือกไว้ จะใช้แทนสไตล์ที่ AI เรียนรู้เอง " +
                    "เรื่องอาการเร่งด่วน น้องรักจะยังแนะนำให้พบแพทย์เสมอ"
                else "Choose how NongRak sounds and how long its chat answers are. Your choice overrides the style it " +
                    "learnt. For urgent symptoms it will still tell you to see a doctor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val tone = profile?.tone
            val length = profile?.answerLength
            val enabled = !loading && !saving

            ChoiceGroup(if (isThai) "น้ำเสียง" else "Tone", TONES, tone, isThai, enabled) { save(it, length) }
            ChoiceGroup(if (isThai) "สไตล์การแนะนำ" else "Answer length", LENGTHS, length, isThai, enabled) { save(tone, it) }

            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(
                    (if (isThai) "บันทึกไม่สำเร็จ: " else "Could not save: ") + it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChoiceGroup(
    title: String,
    choices: List<Choice>,
    selected: String?,
    isThai: Boolean,
    enabled: Boolean,
    onPick: (String?) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column {
            choices.forEachIndexed { index, choice ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = choice.key == selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { if (choice.key != selected) onPick(choice.key) }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = choice.key == selected, onClick = null, enabled = enabled)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) choice.th else choice.en, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (isThai) choice.exTh else choice.exEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
