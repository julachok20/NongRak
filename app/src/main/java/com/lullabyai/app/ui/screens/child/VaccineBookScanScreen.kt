@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.VaccineSetupCatalog
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.ImageUtils
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

/** Route constant — registered by AppNavigation. */
const val VACCINE_BOOK_SCAN_ROUTE = "vaccine_book_scan"

/**
 * #10 — read a สมุดวัคซีนกระทรวงสาธารณสุข from photos and pre-tick what it found.
 *
 * The AI **proposes**, the parent **decides**: nothing is written until they confirm the ticks, and
 * extracted names are filtered against the NIP catalogue so a hallucinated vaccine can never reach
 * a child's medical record. OCR of a handwritten Thai vaccine book is not reliable enough to be
 * authoritative on its own.
 *
 * ponytail: one vision call per photo rather than batching — a vaccine book is 1–4 pages, and
 * per-photo calls degrade gracefully (one unreadable page doesn't lose the others).
 */
@Composable
fun VaccineBookScanScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by childViewModel.uiState.collectAsState()

    var pages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var doneCount by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ticked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanned by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(8)
    ) { uris -> if (uris.isNotEmpty()) { pages = pages + uris; scanned = false } }

    // Only names in the NIP catalogue can be ticked — hallucination guard.
    val catalogue = remember { VaccineSetupCatalog.ALL_DB_NAMES }

    fun runScan() {
        if (pages.isEmpty()) return
        scanning = true
        error = null
        doneCount = 0
        scope.launch {
            val hits = mutableSetOf<String>()
            pages.forEach { uri ->
                val bytes = ImageUtils.downscaleToJpeg(context, uri, maxDim = 1600, quality = 85)
                if (bytes != null) {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val prompt = buildString {
                        append("This is a photo of a Thai Ministry of Public Health child vaccine book (สมุดวัคซีน). ")
                        append("List ONLY vaccines recorded as ALREADY GIVEN (a date or signature is present). ")
                        append("Reply as a JSON array of strings using EXACTLY these names where they match: ")
                        append(catalogue.joinToString(", "))
                        append(". Omit anything not in that list. If nothing is legible, reply [].")
                    }
                    val reply = runCatching {
                        ClaudeClient.generateContent(
                            prompt = prompt,
                            useSearch = false,
                            maxTokens = 1024,
                            jsonMode = true,
                            imageBase64 = b64,
                            imageMimeType = "image/jpeg"
                        )
                    }.getOrNull()
                    // Substring match against the catalogue — tolerant of the model wrapping the
                    // array in prose, and inherently rejects anything off-schedule.
                    if (reply != null) catalogue.forEach { name -> if (reply.contains(name)) hits += name }
                }
                doneCount += 1
            }
            found = hits
            ticked = hits          // pre-ticked, per "tick for user"
            scanning = false
            scanned = true
            if (hits.isEmpty()) {
                error = if (isThai) "อ่านไม่พบวัคซีนในภาพ ลองถ่ายให้ชัดขึ้น" else "No vaccines found. Try a clearer photo."
            }
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "อ่านจากสมุดวัคซีน" else "Scan vaccine book",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                if (isThai) "เพิ่มรูปหน้าสมุดวัคซีนได้หลายหน้า ระบบจะอ่านและติ๊กให้ จากนั้นคุณตรวจสอบก่อนบันทึก"
                else "Add one or more pages. The app reads them and pre-ticks what it finds — you confirm before anything is saved.",
                style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
            )

            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.AddAPhoto, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pages.isEmpty()) (if (isThai) "เพิ่มรูปหน้าสมุดวัคซีน" else "Add vaccine book photos")
                    else (if (isThai) "เพิ่มรูปอีก (${pages.size} รูป)" else "Add more (${pages.size})"),
                    fontWeight = FontWeight.Bold, color = Primary
                )
            }

            if (pages.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (isThai) "${pages.size} รูปพร้อมอ่าน" else "${pages.size} page(s) ready",
                        style = MaterialTheme.typography.labelMedium, color = Outline,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        pages = emptyList(); found = emptySet(); ticked = emptySet(); scanned = false
                    }) { Text(if (isThai) "ล้าง" else "Clear", color = Error) }
                }
                Button(
                    onClick = { runScan() },
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("$doneCount/${pages.size}", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.DocumentScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "อ่านข้อมูลจากรูป" else "Read from photos", fontWeight = FontWeight.Bold)
                    }
                }
            }

            error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            if (scanned && found.isNotEmpty()) {
                Text(
                    if (isThai) "พบ ${found.size} รายการ — ตรวจสอบก่อนบันทึก"
                    else "Found ${found.size} — check before saving",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF57C00).copy(0.10f)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                        Text(
                            if (isThai) "AI อาจอ่านผิด กรุณาตรวจสอบกับสมุดวัคซีนจริงทุกครั้ง"
                            else "AI can misread. Always check against the real book.",
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                        )
                    }
                }
                found.sortedBy { n -> VaccineSetupCatalog.SCHEDULE.firstOrNull { it.dbName == n }?.ageMonths ?: 0 }
                    .forEach { name ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = name in ticked,
                                onCheckedChange = { on -> ticked = if (on) ticked + name else ticked - name }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                VaccineSetupCatalog.SCHEDULE.firstOrNull { it.dbName == name }?.let { d ->
                                    Text(d.description, style = MaterialTheme.typography.labelSmall, color = Outline)
                                }
                            }
                        }
                    }

                Button(
                    onClick = {
                        saveFailed = false
                        childViewModel.applyScannedVaccines(ticked) { n ->
                            if (n > 0) onBack() else saveFailed = true
                        }
                    },
                    enabled = ticked.isNotEmpty() && state.child != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "บันทึก ${ticked.size} รายการ" else "Save ${ticked.size}",
                        fontWeight = FontWeight.Bold
                    )
                }
                if (saveFailed) {
                    Text(
                        if (isThai) "บันทึกไม่สำเร็จ" else "Save failed",
                        color = Error, style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
