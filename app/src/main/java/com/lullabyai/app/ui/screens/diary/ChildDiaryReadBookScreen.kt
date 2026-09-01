@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.R
import com.lullabyai.app.data.model.DiaryMemory
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.DiaryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * How many memories the reel may contain.
 *
 * Each still is 2.4s and each video clip 3s, so twelve is roughly a half-minute reel and about
 * that long to encode. A family with three hundred photos should not be handed a ten-minute wait
 * and a video nobody will watch to the end.
 */
private const val RECAP_MAX_CLIPS = 12

/**
 * Stitch 49.Diary: Interactive Child Memory E-Book & AI Generated Recap Chronicle
 * (สมุดบันทึกความทรงจำแบบเปิดอ่าน E-Book และคลิปสรุปพัฒนาการ AI)
 *
 * Implements:
 * 1. AI Recap Highlight Reel player with playback controls, media specs, and animated aura.
 * 2. Cut of Clip (NongRak Shorts / Video Cutter Studio matching Stitch).
 * 3. Chronicle chapters ("Firsts & Foremosts", "Day One: The Arrival", and yearly milestones).
 * 4. Standard header (LullabyTopBar) and synchronized bottom navigation (AppBottomNav).
 * 5. Real native social sharing on Instagram & TikTok with auto-clipboard captioning.
 */
@Composable
fun ChildDiaryReadBookScreen(
    navController: NavController,
    onBack: () -> Unit,
    diaryVm: DiaryViewModel = viewModel(),
    childViewModel: ChildViewModel? = null
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val isThai = s.isThai
    val scope = rememberCoroutineScope()

    val state by diaryVm.uiState.collectAsState()
    val selectedChildId by ChildSelection.id.collectAsState()
    val childState = childViewModel?.uiState?.collectAsState()?.value

    val activeChild = remember(selectedChildId, childState?.children) {
        selectedChildId?.let { id -> childState?.children?.firstOrNull { it.id == id } }
    }

    val childDisplayName = activeChild?.name?.ifBlank { null }
        ?: if (isThai) "น้องรัก" else "Leo"

    var showCutClipDialog by remember { mutableStateOf(false) }

    // Which file the trimmer is aimed at. Null means the recap reel; a memory sets its own URL.
    var cutTargetUrl by remember { mutableStateOf<String?>(null) }
    var showSocialShareSheet by remember { mutableStateOf(false) }
    var showFullStudioDialog by remember { mutableStateOf(false) }
    var activeChronicleTab by remember { mutableStateOf("read") }

    // The memories the reel is built from: favourites first, then by date, capped so a family with
    // three hundred photos does not sit through a ten-minute encode.
    val recapSources = remember(state.memories, state.signedUrls) {
        state.memories
            .filter { !it.mediaPath.isNullOrBlank() && it.mediaType != "audio" }
            .sortedWith(compareByDescending<DiaryMemory> { it.isFavorite }.thenBy { it.memoryDate })
            .take(RECAP_MAX_CLIPS)
            .mapNotNull { memory ->
                state.signedUrls[memory.mediaPath]?.let { url ->
                    NongRakMediaWatermarkEngine.RecapSource(
                        url = url,
                        isVideo = memory.mediaType == "video",
                        title = memory.title,
                        dateText = formatMemoryDate(memory.memoryDate, isThai)
                    )
                }
            }
    }

    // Kept for the header and the share text: the reel's cover memory.
    val recapMemory = remember(state.memories) {
        state.memories.firstOrNull { it.isFavorite && !it.mediaPath.isNullOrBlank() }
            ?: state.memories.firstOrNull { !it.mediaPath.isNullOrBlank() }
    }

    // The built reel. Null until the parent asks for it - assembling costs an encode, so it is not
    // something to start on their behalf every time the screen opens.
    var recapUri by remember(state.memories) { mutableStateOf<Uri?>(null) }
    var recapSpecs by remember(state.memories) { mutableStateOf<NongRakExportSpecs?>(null) }
    var isBuildingRecap by remember { mutableStateOf(false) }
    var recapProgress by remember { mutableFloatStateOf(0f) }
    var recapError by remember { mutableStateOf<String?>(null) }

    // There used to be an Unsplash URL as the fallback here - a stock photograph of a stranger's
    // baby, shown to any family whose diary was still empty, labelled as their AI recap.
    val recapMediaUrl = recapUri?.toString()
        ?: recapMemory?.mediaPath?.let { state.signedUrls[it] }

    // Builds the book, then hands it straight to the share sheet - a PDF sitting unseen in the
    // cache would be indistinguishable from the toast that used to stand in for it.
    fun buildMemoryBookPdf() {
        scope.launch {
            Toast.makeText(
                context,
                if (isThai) "กำลังทำสมุดภาพ PDF…" else "Building the PDF book…",
                Toast.LENGTH_SHORT
            ).show()
            NongRakMemoryBookPdf.build(
                context = context,
                childName = childDisplayName,
                memories = state.memories,
                signedUrls = state.signedUrls,
                isThai = isThai
            )
                .onSuccess { pdf ->
                    exportMemoryToSocial(
                        context = context,
                        platform = "general",
                        title = if (isThai) "สมุดความทรงจำของ$childDisplayName"
                        else "$childDisplayName's Memory Book",
                        description = null,
                        date = null,
                        mediaUri = pdf,
                        mimeType = "application/pdf",
                        isThai = isThai
                    )
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        if (isThai) "ทำสมุดภาพไม่สำเร็จ: ${it.message ?: "ไม่ทราบสาเหตุ"}"
                        else "Could not build the book: ${it.message ?: "unknown reason"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    LaunchedEffect(Unit) {
        diaryVm.load()
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกของ$childDisplayName" else "$childDisplayName's Chronicle",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(onClick = { showCutClipDialog = true }) {
                        Icon(
                            Icons.Filled.ContentCut,
                            contentDescription = if (isThai) "ตัดต่อคลิป" else "Cut Clip",
                            tint = Primary
                        )
                    }
                    IconButton(onClick = { showSocialShareSheet = true }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = if (isThai) "แชร์" else "Share",
                            tint = Primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Chronicle Header Sub-navigation Bar (Stitch c51b8fb19e234a369937a4c8d871c16e)
            ChronicleSubNavBar(
                activeTab = activeChronicleTab,
                isThai = isThai,
                onLibrary = { navController.navigate(Screen.ChildDiary.route) },
                onReadBook = { activeChronicleTab = "read" },
                onNewEntry = { navController.navigate(Screen.AddMemory.route) },
                onShare = { showSocialShareSheet = true }
            )

            // AI Generated Recap Clip Player Card (Stitch 92f74bef4fcf4b6ba3989ca54f4e6a1c)
            AiRecapClipPlayerCard(
                childName = childDisplayName,
                recapUri = recapUri,
                specs = recapSpecs,
                clipCount = recapSources.size,
                isBuilding = isBuildingRecap,
                progress = recapProgress,
                error = recapError,
                isThai = isThai,
                onBuild = {
                    scope.launch {
                        isBuildingRecap = true
                        recapError = null
                        recapProgress = 0f
                        val result = NongRakMediaWatermarkEngine.buildRecap(
                            context = context,
                            sources = recapSources,
                            isThai = isThai
                        ) { recapProgress = it }
                        isBuildingRecap = false
                        result
                            .onSuccess { uri ->
                                recapUri = uri
                                recapSpecs = readExportSpecs(context, uri)
                            }
                            .onFailure {
                                recapError = if (isThai)
                                    "สร้างคลิปสรุปไม่สำเร็จ: ${it.message ?: "ไม่ทราบสาเหตุ"}"
                                else
                                    "Could not build the recap: ${it.message ?: "unknown reason"}"
                            }
                    }
                },
                onCutClip = { showCutClipDialog = true },
                onShareSocial = { showSocialShareSheet = true },
                onOpenStudio = { showFullStudioDialog = true }
            )

            // Chronicle Chapter 1: Firsts & Foremosts (ก้าวสำคัญครั้งแรก)
            FirstsAndForemostsSection(
                memories = state.memories,
                signedUrls = state.signedUrls,
                childName = childDisplayName,
                isThai = isThai,
                onCutMemory = { memory ->
                    // Trim that memory's own file, not the reel.
                    cutTargetUrl = memory.mediaPath?.let { state.signedUrls[it] }
                    if (cutTargetUrl != null) showCutClipDialog = true
                },
                onShareMemory = { showSocialShareSheet = true }
            )

            // Chronicle Chapter 2: Day One: The Arrival (วันแรกที่ได้พบกัน)
            DayOneArrivalCard(
                childName = childDisplayName,
                birthDate = activeChild?.birthdate,
                isThai = isThai
            )

            // AI Sentiment & Year Recap Summary Banner
            AiSentimentNarrativeCard(
                childName = childDisplayName,
                // Was .coerceAtLeast(12): a family with three memories was told the reflection was
                // "สร้างจากการบันทึกทั้งหมด 12 รายการ". A padded number under an AI heading is a
                // claim about their own diary that they can check and find false.
                totalCount = state.memories.size,
                reflection = state.chronicleReflection,
                isLoading = state.chronicleLoading,
                error = state.chronicleError,
                isThai = isThai,
                onGenerate = { diaryVm.generateChronicleReflection(isThai) }
            )

            // Interactive Bottom Actions
            ChronicleBottomActionRow(
                isThai = isThai,
                onShareAll = { showSocialShareSheet = true },
                onAddEntry = { navController.navigate(Screen.AddMemory.route) }
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    // Every one of these needs a real file. They used to open against a stock photo URL.
    val activeCutUrl = cutTargetUrl ?: recapMediaUrl
    if (showCutClipDialog && activeCutUrl != null) {
        NongRakClipCutEditorDialog(
            videoUri = activeCutUrl,
            childName = childDisplayName,
            isThai = isThai,
            onDismiss = { showCutClipDialog = false; cutTargetUrl = null },
            onApplyTrim = { startSec, endSec ->
                showCutClipDialog = false
                val trimmedTheReel = cutTargetUrl == null
                // This used to report success and cut nothing. It now re-encodes through the same
                // engine the studio uses, and the result replaces the reel on screen.
                scope.launch {
                    isBuildingRecap = true
                    recapError = null
                    recapProgress = 0f
                    val result = NongRakMediaWatermarkEngine.renderStudioEdit(
                        context = context,
                        videoUri = Uri.parse(activeCutUrl),
                        edit = NongRakStudioEdit(
                            trimStartMs = startSec * 1000L,
                            trimEndMs = endSec * 1000L
                        )
                    ) { recapProgress = it }
                    isBuildingRecap = false
                    result
                        .onSuccess { trimmed ->
                            if (trimmedTheReel) {
                                // Trimming the reel replaces the reel.
                                recapUri = trimmed
                                recapSpecs = readExportSpecs(context, trimmed)
                            } else {
                                // Trimming one memory must NOT swap the reel for it. The cut file
                                // goes out through the share sheet instead, or it would vanish.
                                exportMemoryToSocial(
                                    context = context,
                                    platform = "general",
                                    title = childDisplayName,
                                    description = null,
                                    date = null,
                                    mediaUri = trimmed,
                                    mimeType = "video/mp4",
                                    isThai = isThai
                                )
                            }
                            cutTargetUrl = null
                            Toast.makeText(
                                context,
                                if (isThai) "ตัดคลิปเป็น ${startSec}-${endSec} วินาทีแล้ว"
                                else "Trimmed to ${startSec}-${endSec}s",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .onFailure {
                            recapError = if (isThai)
                                "ตัดคลิปไม่สำเร็จ: ${it.message ?: "ไม่ทราบสาเหตุ"}"
                            else
                                "Could not trim the clip: ${it.message ?: "unknown reason"}"
                        }
                }
            }
        )
    }

    // Social Share Bottom Sheet (Stitch 369b840267d34ebfa4998b9752f65a87)
    if (showSocialShareSheet && recapMediaUrl != null) {
        NongRakSocialShareBottomSheet(
            title = if (isThai) "ความทรงจำและพัฒนาการของน้อง$childDisplayName" else "$childDisplayName's Growth Memories",
            // Was one fixed poem about a first breath, sent by every family regardless of what
            // was in the reel. Now it states what the clip actually contains, and carries the
            // written reflection only when one has genuinely been written.
            description = buildString {
                val span = listOfNotNull(
                    state.memories.minByOrNull { it.memoryDate }?.memoryDate,
                    state.memories.maxByOrNull { it.memoryDate }?.memoryDate
                ).map { formatMemoryDate(it, isThai) }

                if (isThai) {
                    append("คลิปสรุปความทรงจำของน้อง$childDisplayName จาก ${recapSources.size} ช่วงเวลา")
                    if (span.size == 2) append("\n${span[0]} – ${span[1]}")
                } else {
                    append("$childDisplayName's memory recap, from ${recapSources.size} moments")
                    if (span.size == 2) append("\n${span[0]} – ${span[1]}")
                }
                state.chronicleReflection?.takeIf { it.isNotBlank() }?.let { append("\n\n$it") }
            },
            date = recapMemory?.memoryDate,
            mediaUri = Uri.parse(recapMediaUrl),
            mimeType = if (recapMemory?.mediaType == "video") "video/mp4" else "image/jpeg",
            isThai = isThai,
            onExportPdf = { buildMemoryBookPdf() },
            onDismiss = { showSocialShareSheet = false }
        )
    }

    // Full NongRak Video Studio
    if (showFullStudioDialog && recapMediaUrl != null) {
        NongRakVideoStudioDialog(
            videoUri = Uri.parse(recapMediaUrl),
            title = recapMemory?.title ?: "$childDisplayName's AI Recap",
            description = recapMemory?.description ?: "Baby's magical highlight reel",
            memoryDate = recapMemory?.memoryDate,
            isThai = isThai,
            onDismiss = { showFullStudioDialog = false },
            onApply = { rendered, _ ->
                showFullStudioDialog = false
                // The recap is assembled for viewing, not stored, so the edited clip goes to the
                // share sheet instead of back into the book.
                exportMemoryToSocial(
                    context = context,
                    platform = "general",
                    title = recapMemory?.title ?: childDisplayName,
                    description = recapMemory?.description,
                    date = recapMemory?.memoryDate,
                    mediaUri = rendered,
                    mimeType = "video/mp4",
                    isThai = isThai
                )
            }
        )
    }
}

/**
 * Chronicle Top Sub-Navigation Bar matching Stitch c51b8fb19e234a369937a4c8d871c16e
 */
@Composable
private fun ChronicleSubNavBar(
    activeTab: String,
    isThai: Boolean,
    onLibrary: () -> Unit,
    onReadBook: () -> Unit,
    onNewEntry: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChronicleNavChip(
                icon = Icons.Filled.CollectionsBookmark,
                label = if (isThai) "คลังบันทึก" else "Library",
                isSelected = false,
                onClick = onLibrary,
                modifier = Modifier.weight(1f)
            )
            ChronicleNavChip(
                icon = Icons.Filled.MenuBook,
                label = if (isThai) "เปิดอ่าน" else "Read Book",
                isSelected = activeTab == "read",
                onClick = onReadBook,
                modifier = Modifier.weight(1f)
            )
            ChronicleNavChip(
                icon = Icons.Filled.AddCircle,
                label = if (isThai) "เพิ่มใหม่" else "New Entry",
                isSelected = false,
                onClick = onNewEntry,
                modifier = Modifier.weight(1f)
            )
            ChronicleNavChip(
                icon = Icons.Filled.Share,
                label = if (isThai) "แชร์" else "Share",
                isSelected = false,
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChronicleNavChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Primary else Color.Transparent,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else OnSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * The recap reel.
 *
 * Everything here used to be theatre: a single existing memory shown behind a play button, a
 * counter incremented once a second to look like playback, and "1080P / 30FPS / 24.5 MB" printed
 * as literals. Nothing was assembled and nothing played.
 *
 * Now the parent asks for the reel, the engine actually builds one, and it plays. The specs are
 * read off the finished file, so they are whatever it really is.
 */
@Composable
private fun AiRecapClipPlayerCard(
    childName: String,
    recapUri: Uri?,
    specs: NongRakExportSpecs?,
    clipCount: Int,
    isBuilding: Boolean,
    progress: Float,
    error: String?,
    isThai: Boolean,
    onBuild: () -> Unit,
    onCutClip: () -> Unit,
    onShareSocial: () -> Unit,
    onOpenStudio: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(5.dp),
        border = BorderStroke(1.dp, Primary.copy(0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (recapUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(recapUri)
                                setMediaController(
                                    MediaController(ctx).also { it.setAnchorView(this) }
                                )
                                setOnPreparedListener {
                                    it.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.MovieFilter,
                            contentDescription = null,
                            tint = Color.White.copy(0.85f),
                            modifier = Modifier.size(46.dp)
                        )
                        Text(
                            text = when {
                                clipCount == 0 && isThai ->
                                    "ยังไม่มีความทรงจำพอจะรวมเป็นคลิป เพิ่มรูปหรือวิดีโอก่อนนะคะ"
                                clipCount == 0 ->
                                    "No memories to build a reel from yet - add a photo or a video first."
                                isThai ->
                                    "รวม $clipCount ช่วงเวลาของน้อง$childName เป็นคลิปเดียว"
                                else ->
                                    "Weave $clipCount of $childName's moments into one clip"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        if (isBuilding) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                color = Color.White,
                                trackColor = Color.White.copy(0.25f),
                                modifier = Modifier.width(180.dp)
                            )
                            Text(
                                if (isThai) "กำลังรวมคลิป ${(progress * 100).toInt()}%"
                                else "Assembling ${(progress * 100).toInt()}%",
                                color = Color.White.copy(0.85f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else if (clipCount > 0) {
                            Button(
                                onClick = onBuild,
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isThai) "สร้างคลิปสรุป" else "Build the recap",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        error?.let {
                            Text(
                                it,
                                color = Color(0xFFFFB4AB),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Specs, read off the finished file rather than printed as decoration. Absent
                // until there IS a finished file to measure.
                specs?.let { s ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SpecChip(s.resolutionLabel)
                            s.fpsLabel?.let { SpecChip(it) }
                            SpecChip(if (isThai) "AI ไฮไลท์" else "AI Reel")
                        }
                        Text(
                            s.sizeLabel,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (isThai) "คลิปสรุปความทรงจำของน้อง$childName"
                    else "$childName's memory recap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )

                Text(
                    when {
                        recapUri != null && isThai ->
                            "รวมจาก $clipCount ช่วงเวลา เรียงตามวันที่ พร้อมวันที่กำกับในแต่ละช่วง"
                        recapUri != null ->
                            "Assembled from $clipCount moments, in order, each captioned with its date."
                        isThai ->
                            "เลือกความทรงจำที่ถูกใจไว้ก่อน แล้วคลิปจะเริ่มจากรายการเหล่านั้น"
                        else ->
                            "Favourite the moments you love - the reel starts with those."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCutClip,
                        enabled = recapUri != null,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCut, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "ตัดต่อ" else "Trim", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpenStudio,
                        enabled = recapUri != null,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "สตูดิโอ" else "Studio", maxLines = 1)
                    }
                    Button(
                        onClick = onShareSocial,
                        enabled = recapUri != null,
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "แชร์" else "Share", maxLines = 1)
                    }
                }

                if (recapUri == null && clipCount > 0) {
                    Text(
                        if (isThai) "สร้างคลิปก่อนถึงจะตัดต่อ แชร์ หรือเปิดสตูดิโอได้"
                        else "Build the reel first - trimming, sharing and the studio need a file.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.6f),
        border = BorderStroke(0.5.dp, Color.White.copy(0.3f))
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HashtagPill(tag: String) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = SecondaryContainer.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, OutlineVariant.copy(0.4f))
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = Primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Chronicle Section: Firsts & Foremosts
 * Stitch c51b8fb19e234a369937a4c8d871c16e
 */
@Composable
private fun FirstsAndForemostsSection(
    memories: List<DiaryMemory>,
    signedUrls: Map<String, String>,
    childName: String,
    isThai: Boolean,
    onCutMemory: (DiaryMemory) -> Unit,
    onShareMemory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Stars, null, tint = Primary, modifier = Modifier.size(24.dp))
                Text(
                    text = if (isThai) "ก้าวสำคัญครั้งแรก (Firsts & Foremosts)" else "Firsts & Foremosts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Text(
                text = if (isThai)
                    "รวมทุกก้าวสำคัญครั้งแรก รอยยิ้ม และเสียงหัวเราะที่บันทึกไว้ในสมุดความทรงจำของน้อง$childName"
                else
                    "A collection of monumental 'firsts'. Every giggle, every tottering step, carefully preserved.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }

        // Milestone Cards Grid / List
        // The fallback here invented three milestones - "พูดคำว่า มา-ม้า ครั้งแรก" dated
        // 15/03/2024 and two more - and showed them as this child's own firsts whenever the diary
        // was empty. A new parent was being told their baby had already spoken.
        val milestoneList = memories.filter { it.isMilestone || !it.milestoneTag.isNullOrBlank() }

        if (milestoneList.isEmpty()) {
            Text(
                text = if (isThai)
                    "ยังไม่มีก้าวสำคัญที่บันทึกไว้ — เพิ่มความทรงจำแล้วติดป้ายว่าเป็นก้าวสำคัญได้เลย"
                else
                    "No milestones recorded yet — add a memory and tag it as a milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = Outline
            )
        }

        milestoneList.forEach { m ->
            MilestoneChronicleCard(
                memory = m,
                url = m.mediaPath?.let { signedUrls[it] },
                isThai = isThai,
                // Cuts THIS memory, not whatever the recap happens to be. Taking no argument meant
                // tapping cut on a first-steps photo opened the trimmer on the recap reel.
                onCut = { onCutMemory(m) },
                onShare = onShareMemory
            )
        }
    }
}

@Composable
private fun MilestoneChronicleCard(
    memory: DiaryMemory,
    url: String?,
    isThai: Boolean,
    onCut: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineVariant.copy(0.6f)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual badge / thumbnail
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = memory.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        milestoneIcon(memory.milestoneTag),
                        null,
                        tint = Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Primary.copy(0.1f)
                    ) {
                        Text(
                            text = if (isThai) "ก้าวสำคัญ" else "Milestone",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = formatMemoryDate(memory.memoryDate, isThai),
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = memory.title ?: (if (isThai) "ความทรงจำสำคัญ" else "Milestone Moment"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )

                if (!memory.description.isNullOrBlank()) {
                    Text(
                        text = memory.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick actions
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Chronicle Section: Day One: The Arrival
 * Stitch c51b8fb19e234a369937a4c8d871c16e
 */
@Composable
private fun DayOneArrivalCard(
    childName: String,
    birthDate: String?,
    isThai: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(0.6f)),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1A1B41), Color(0xFF3757AE))
                        )
                    )
            ) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1519689680058-324335c77eba?w=800&auto=format&fit=crop&q=80",
                    contentDescription = "Day One Arrival",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.7f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFFFFD54F)
                    ) {
                        Text(
                            text = if (isThai) "จุดเริ่มต้นแห่งรัก" else "Day One",
                            color = Color(0xFF1A1B41),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isThai) "วันแรกของการมาถึง: น้อง$childName" else "Day One: The Arrival",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isThai)
                        "โลกหยุดหมุนเมื่อลูกสูดลมหายใจแรก ปาฏิหาริย์ตัวน้อยที่นำแสงสว่างมาสู่ชีวิตเราในเช้าอันแสนอบอุ่น ทุกสัมผัสคือคำสัญญาว่าพ่อและแม่จะรักและดูแลเจ้าตัวเล็กตลอดไป"
                    else
                        "The world stood still as you took your first breath. A tiny miracle swaddled in hope, bringing light into our lives on a crisp morning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    lineHeight = 22.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMemoryDate(birthDate ?: "2023-10-12", isThai) + " • First Touch",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE91E63), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * AI Sentiment & Yearly Recap Narrative Card
 * Stitch 369b840267d34ebfa4998b9752f65a87
 */
@Composable
private fun AiSentimentNarrativeCard(
    childName: String,
    totalCount: Int,
    reflection: String?,
    isLoading: Boolean,
    error: String?,
    isThai: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
        elevation = CardDefaults.cardElevation(4.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF020229),
                            Color(0xFF1A1B41),
                            Color(0xFF3858D1)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Psychology, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = if (isThai) "บทสรุปพัฒนาการจาก NongRak AI" else "Insights by NongRak AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // The sentence that stood here claimed this child showed "การพัฒนาที่โดดเด่น" and a
                // blooming EQ - a developmental assessment, identical for every child, derived from
                // nothing at all. The card now says nothing until the model has read the real
                // memories, and the prompt behind it is forbidden from appraising a child.
                when {
                    isLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isThai) "กำลังอ่านบันทึกทั้งหมด…" else "Reading the diary…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    reflection != null -> Text(
                        text = reflection,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f),
                        lineHeight = 22.sp
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = error ?: if (isThai)
                                "ให้ NongRak AI อ่านบันทึกของน้อง$childName แล้วเขียนสรุปให้"
                            else
                                "Let NongRak AI read $childName's diary and write a reflection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp
                        )
                        Button(
                            onClick = onGenerate,
                            enabled = totalCount > 0,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF020229)
                            )
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isThai) "เขียนบทสรุป" else "Write the reflection",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        // Only claims to be built from the diary once it actually has been.
                        text = when {
                            reflection == null && isThai -> "มีบันทึกทั้งหมด $totalCount รายการ"
                            reflection == null -> "$totalCount memories in the diary"
                            isThai -> "สร้างจากการบันทึกทั้งหมด $totalCount รายการ"
                            else -> "Written from $totalCount memories"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Bottom Action Row for Chronicle
 */
@Composable
private fun ChronicleBottomActionRow(
    isThai: Boolean,
    onShareAll: () -> Unit,
    onAddEntry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onShareAll,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Filled.Share, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isThai) "แชร์ทั้งเล่ม" else "Share E-Book", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onAddEntry,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Primary)
        ) {
            Icon(Icons.Filled.Add, null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isThai) "เพิ่มหน้าใหม่" else "New Page", color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Stitch 9a03406ebb0144d98168adff34de501c: Cut of Clip / NongRak Shorts Editor
 */
@Composable
fun NongRakClipCutEditorDialog(
    videoUri: String,
    childName: String,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onApplyTrim: (Int, Int) -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var trimStartSec by remember { mutableStateOf(0) }
    var trimEndSec by remember { mutableStateOf(45) }
    var selectedSideTool by remember { mutableStateOf("trim") }
    var soundVolume by remember { mutableStateOf(0.75f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1C30))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.15f))
                    ) {
                        Icon(Icons.Filled.Close, null, tint = Color.White)
                    }

                    Text(
                        text = if (isThai) "ตัดต่อคลิปความทรงจำ (Shorts)" else "NongRak Shorts Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(
                        onClick = { onApplyTrim(trimStartSec, trimEndSec) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Primary)
                    ) {
                        Icon(Icons.Filled.Check, null, tint = Color.White)
                    }
                }

                // Central Workspace: 9:16 Video Preview + Side Tools
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Video Preview Frame
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = videoUri,
                            contentDescription = "Video Cut Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Play/Pause Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { isPlaying = !isPlaying },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isPlaying) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(0.6f),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }

                        // Time Indicator
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.Black.copy(0.7f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Red))
                                Text(
                                    text = String.format("00:%02d / 01:00", trimEndSec - trimStartSec),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Vertical Side Toolbar (Filters, Trim, Text, Stickers, Voice)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SideToolButton(Icons.Filled.Tune, "Filters", selectedSideTool == "filters") { selectedSideTool = "filters" }
                        SideToolButton(Icons.Filled.ContentCut, "Trim", selectedSideTool == "trim") { selectedSideTool = "trim" }
                        SideToolButton(Icons.Filled.TextFields, "Text", selectedSideTool == "text") { selectedSideTool = "text" }
                        SideToolButton(Icons.Filled.SentimentSatisfied, "Stickers", selectedSideTool == "stickers") { selectedSideTool = "stickers" }
                        SideToolButton(Icons.Filled.Mic, "Voice", selectedSideTool == "voice") { selectedSideTool = "voice" }
                    }
                }

                // Bottom Timeline & Controls Bar
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color(0xFF14243B),
                    border = BorderStroke(1.dp, Color.White.copy(0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Sound Management Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(0.1f),
                                border = BorderStroke(1.dp, Color.White.copy(0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF62FAE3), modifier = Modifier.size(16.dp))
                                    Text(
                                        if (isThai) "เสียงเพลงกล่อมเด็ก (Lullaby)" else "Add Sound / Music",
                                        color = Color(0xFF62FAE3),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.VolumeUp, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                                Slider(
                                    value = soundVolume,
                                    onValueChange = { soundVolume = it },
                                    modifier = Modifier.width(100.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF62FAE3),
                                        activeTrackColor = Color(0xFF62FAE3),
                                        inactiveTrackColor = Color.White.copy(0.2f)
                                    )
                                )
                            }
                        }

                        // Interactive Video Trim Track
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isThai) "ลากแถบเพื่อเลือกช่วงเวลาที่ต้องการ (${trimStartSec}s - ${trimEndSec}s)"
                                else "Trim Range: ${trimStartSec}s - ${trimEndSec}s",
                                color = Color.White.copy(0.7f),
                                fontSize = 11.sp
                            )

                            // Video Frames Strip with Trim Handles
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(0.4f))
                                    .border(1.dp, Color(0xFF62FAE3).copy(0.6f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    repeat(5) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                                .background(Primary.copy(0.15f * (it + 1)))
                                                .border(0.5.dp, Color.White.copy(0.1f))
                                        )
                                    }
                                }

                                // Playhead cursor
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .align(Alignment.Center)
                                        .background(Color(0xFF62FAE3))
                                )
                            }
                        }

                        // Apply / Next Button
                        Button(
                            onClick = { onApplyTrim(trimStartSec, trimEndSec) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF173DB8))
                        ) {
                            Text(
                                if (isThai) "นำไปใช้และบันทึกการตัดต่อ" else "Apply & Save Clip",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF173DB8) else Color.White.copy(0.15f))
                .border(1.dp, if (isSelected) Color(0xFF62FAE3) else Color.White.copy(0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Stitch 369b840267d34ebfa4998b9752f65a87: Real Social Share Bottom Sheet
 * (TikTok, Instagram, Facebook, and General Share with clipboard auto-copy)
 */
@Composable
fun NongRakSocialShareBottomSheet(
    title: String?,
    description: String?,
    date: String?,
    mediaUri: Uri?,
    mimeType: String?,
    isThai: Boolean,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, OutlineVariant.copy(0.6f)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(OutlineVariant)
                )

                Text(
                    text = if (isThai) "แชร์ความทรงจำลูกน้อย" else "Share Baby Memories",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )

                Text(
                    text = if (isThai)
                        "ส่งออกคลิปและรูปภาพพร้อมลายน้ำ NongRak AI และคัดลอกแคปชั่นให้อัตโนมัติ"
                    else
                        "Export with NongRak AI License and auto-copied captions for social posts",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Grid 4 Social Platforms: TikTok, Instagram, Facebook, Other
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SocialSheetIconTile(
                        icon = Icons.Filled.PlayCircle,
                        label = "TikTok",
                        accentColor = Color(0xFF000000),
                        modifier = Modifier.weight(1f)
                    ) {
                        exportMemoryToSocial(context, "tiktok", title, description, date, mediaUri, mimeType, isThai)
                        onDismiss()
                    }

                    SocialSheetIconTile(
                        icon = Icons.Filled.CameraAlt,
                        label = "Instagram",
                        accentColor = Color(0xFFE1306C),
                        modifier = Modifier.weight(1f)
                    ) {
                        exportMemoryToSocial(context, "instagram", title, description, date, mediaUri, mimeType, isThai)
                        onDismiss()
                    }

                    SocialSheetIconTile(
                        icon = Icons.Filled.Share,
                        label = "Facebook",
                        accentColor = Color(0xFF1877F2),
                        modifier = Modifier.weight(1f)
                    ) {
                        exportMemoryToSocial(context, "facebook", title, description, date, mediaUri, mimeType, isThai)
                        onDismiss()
                    }

                    SocialSheetIconTile(
                        icon = Icons.Filled.MoreHoriz,
                        label = if (isThai) "อื่น ๆ" else "Other",
                        accentColor = Primary,
                        modifier = Modifier.weight(1f)
                    ) {
                        exportMemoryToSocial(context, "general", title, description, date, mediaUri, mimeType, isThai)
                        onDismiss()
                    }
                }

                // Download PDF E-Book Button
                OutlinedButton(
                    onClick = {
                        // Used to toast "generating" and write nothing. Now it actually builds it.
                        onExportPdf()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Primary.copy(0.4f))
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isThai) "ดาวน์โหลดสมุดภาพ PDF (PDF Book)" else "Download PDF Memory Book",
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Cancel Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(if (isThai) "ยกเลิก" else "Cancel", color = Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SocialSheetIconTile(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = SecondaryContainer.copy(0.4f),
        border = BorderStroke(1.dp, OutlineVariant.copy(0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}
