package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.AssistantMemoryRepository
import com.lullabyai.app.data.repository.MemoryFact
import com.lullabyai.app.data.repository.MemorySource
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

/**
 * สิ่งที่น้องรักจำได้ — everything the assistant has learned about this family, and the controls to
 * take any of it away.
 *
 * This screen is not an extra. A memory a parent cannot inspect is a memory she cannot correct, and
 * an assistant quietly holding a wrong fact about her child is worse than one holding none. It
 * ships with the memory, never after it.
 *
 * The source badge is the other half of that. A fact the assistant GUESSED must never look like one
 * she stated: she is the only person who can tell the difference, and only if the screen shows her.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantMemoryScreen(
    navController: NavController,
    childViewModel: ChildViewModel,
    repo: AssistantMemoryRepository = remember { AssistantMemoryRepository() }
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val childState by childViewModel.uiState.collectAsState()

    var facts by remember { mutableStateOf<List<MemoryFact>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf<MemoryFact?>(null) }

    suspend fun reload() {
        // Muted rows are included deliberately: a fact she silenced is still remembered, and hiding
        // it here would leave her no way to bring it back or delete it for good.
        repo.recall(includeMuted = true)
            .onSuccess { facts = it }
            .onFailure {
                snackbar.showSnackbar(
                    if (s.isThai) "โหลดความทรงจำไม่สำเร็จค่ะ" else "Could not load memories."
                )
            }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (s.isThai) "ลืมเรื่องนี้?" else "Forget this?") },
            text = {
                Text(
                    if (s.isThai) "น้องรักจะลืม \"${target.value}\" อย่างถาวร กู้คืนไม่ได้ค่ะ"
                    else "NongRak will permanently forget \"${target.value}\". This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    confirmDelete = null
                    scope.launch {
                        repo.forget(id)
                            .onSuccess { reload() }
                            .onFailure {
                                snackbar.showSnackbar(
                                    if (s.isThai) "ลบไม่สำเร็จค่ะ" else "Could not delete."
                                )
                            }
                    }
                }) {
                    Text(
                        if (s.isThai) "ลืมเลย" else "Forget",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(if (s.isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (s.isThai) "สิ่งที่น้องรักจำได้" else "What NongRak remembers",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = if (s.isThai) "ย้อนกลับ" else "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            facts.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (s.isThai) "ยังไม่ได้จำอะไรเลยค่ะ" else "Nothing remembered yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (s.isThai)
                            "เมื่อคุณแม่คุยกับน้องรัก สิ่งที่สำคัญจะถูกจำไว้ที่นี่ และลบได้ทุกเมื่อ"
                        else
                            "As you talk with NongRak, what matters is kept here - and you can delete any of it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        if (s.isThai)
                            "น้องรักใช้สิ่งเหล่านี้เพื่อตอบให้ตรงกับครอบครัวคุณแม่ ลบหรือปิดอันไหนก็ได้ค่ะ"
                        else
                            "NongRak uses these to answer in a way that fits your family. Delete or silence any of them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                items(facts, key = { it.id }) { fact ->
                    MemoryRow(
                        fact = fact,
                        childName = childState.children.firstOrNull { it.id == fact.childId }?.name,
                        isThai = s.isThai,
                        onToggleMute = {
                            scope.launch {
                                repo.setMuted(fact.id, !fact.muted)
                                    .onSuccess { reload() }
                                    .onFailure {
                                        snackbar.showSnackbar(
                                            if (s.isThai) "เปลี่ยนไม่สำเร็จค่ะ" else "Could not change that."
                                        )
                                    }
                            }
                        },
                        onDelete = { confirmDelete = fact }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(
    fact: MemoryFact,
    childName: String?,
    isThai: Boolean,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            fact.value,
            style = MaterialTheme.typography.bodyMedium,
            // A silenced fact is still shown, but visibly out of use.
            color = if (fact.muted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SourceBadge(fact.source, isThai)
            childName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (fact.muted) {
                Text(
                    if (isThai) "· ปิดอยู่" else "· silenced",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onToggleMute, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (fact.muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                    contentDescription = if (isThai) "ปิดหรือเปิดการใช้" else "Silence or use",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = if (isThai) "ลืมเรื่องนี้" else "Forget this",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * Where the fact came from, and the whole reason this screen can be trusted.
 *
 * INFERRED is styled as the odd one out on purpose: it is the assistant's own guess, and a parent
 * has to see at a glance which lines she actually said and which ones it worked out for itself.
 */
@Composable
private fun SourceBadge(source: MemorySource, isThai: Boolean) {
    val (label, tint) = when (source) {
        MemorySource.SAID ->
            (if (isThai) "คุณแม่บอกไว้" else "you told it") to Color(0xFF16A394)
        MemorySource.LOGGED ->
            (if (isThai) "จากบันทึก" else "from your records") to Color(0xFF3757AE)
        MemorySource.INFERRED ->
            (if (isThai) "น้องรักเดาเอง" else "its own guess") to Color(0xFFF08C2E)
    }
    Surface(shape = RoundedCornerShape(100.dp), color = tint.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold
        )
    }
}
