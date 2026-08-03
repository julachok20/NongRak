@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.VaccineSetupCatalog
import com.lullabyai.app.data.sleep.SleepWakeWindows
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.util.WhiteNoisePlayer
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.SleepMonitorViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Route constant — registered by AppNavigation (screens don't touch the nav graph). */
const val PRO_SLEEP_HUB_ROUTE = "pro_sleep_hub"

/**
 * Stitch "Pro Sleep Hub with Sound Tracking" — MAIN's `ติดตามการนอน` tile.
 *
 * Sections follow the design top-to-bottom: awake/wake-window status, AI insight, sound tracking,
 * 24h timeline, mood check-in, white-noise player, then the four stat tiles.
 *
 * The timeline, mood grid, player and stat tiles are the same composables MAIN uses (now `internal`
 * in SleepTrackerScreen) rather than re-implementations — the design shows identical components on
 * both pages, so a second copy would only drift.
 */
@Composable
fun ProSleepHubScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    monitorViewModel: SleepMonitorViewModel = viewModel()
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val monitor by monitorViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val moodSavedMsg = if (isThai) "บันทึกอารมณ์แล้ว" else "Mood saved"

    val child = childState.child
    LaunchedEffect(child?.id) {
        child?.id?.let {
            monitorViewModel.bind(it, null)
            monitorViewModel.loadEvents(it)
        }
    }
    // Never leave the microphone running once the parent leaves this screen.
    DisposableEffect(Unit) { onDispose { monitorViewModel.stopMonitoring() } }

    val noisePlayer = remember { WhiteNoisePlayer() }
    DisposableEffect(Unit) { onDispose { noisePlayer.release() } }

    val active = childState.activeSleepSession
    val logs = childState.sleepLogs

    // Re-read the clock every 30s so the wake-window figure stays live, same cadence as MAIN.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); tick++ } }

    val today = LocalDate.now()
    val todayLogs = logs.filter {
        TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == today
    }
    val totalMinutesToday = todayLogs.sumOf { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    }
    val napCount = todayLogs.count { it.sleepType == "nap" }

    val last7Start = today.minusDays(6)
    val last7Logs = logs.filter { log ->
        val d = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate()
        d != null && !d.isBefore(last7Start) && !d.isAfter(today)
    }
    val weeklyAvgMinutes = last7Logs.sumOf { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    } / 7
    val daysWithMain = (0..6).count { offset ->
        val day = today.minusDays(offset.toLong())
        last7Logs.any {
            it.sleepType == "main" &&
                TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == day
        }
    }
    val consistencyPct = daysWithMain * 100 / 7

    val lastCompleted = logs.filter { it.endTime != null }.maxByOrNull { it.startTime }
    val lastSleepText = lastCompleted?.let { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) }
        if (start != null && end != null) {
            val d = Duration.between(start, end)
            val kind = if (log.sleepType == "nap") (if (isThai) "งีบ " else "Nap ") else ""
            "$kind${d.toHours()}${if (isThai) "ชม." else "h"} ${d.toMinutes() % 60}${if (isThai) "น." else "m"}"
        } else "-"
    } ?: "-"

    val ageMonths = VaccineSetupCatalog.ageMonthsFromBirthdate(child?.birthdate ?: "")
    val wakeBand = SleepWakeWindows.forAgeMonths(ageMonths)

    // "ช่วงเวลาตื่นปัจจุบัน" — minutes since the last sleep ended. Null while a session is running
    // (the child isn't awake) or before any sleep has been logged.
    val awakeMinutes = remember(tick, active?.id, lastCompleted?.endTime) {
        if (active != null) null
        else lastCompleted?.endTime?.let { TimeUtils.parseInstant(it) }
            ?.let { Duration.between(it, Instant.now()).toMinutes().coerceAtLeast(0) }
    }

    val todaysMoodTarget = active ?: todayLogs.maxByOrNull { it.startTime }
    val todaysMood = todaysMoodTarget?.mood

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ติดตามการนอน" else "Sleep Tracking",
                onBack = onBack,
                navController = navController
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
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            AwakeStatusCard(
                isThai = isThai,
                sleeping = active != null,
                awakeMinutes = awakeMinutes,
                targetMinutes = wakeBand.maxWakeMinutes,
                targetLabel = if (isThai) wakeBand.windowTh else wakeBand.windowEn
            )

            WakeRegressionCard(
                isThai = isThai,
                ageMonths = ageMonths,
                onOpenPlan = { navController.navigate(Screen.SleepGuidance.route) }
            )

            // --- โหมดตรวจจับเสียง (Sound Tracking Mode) — the screen's namesake ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.GraphicEq, null, tint = Primary, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isThai) "โหมดตรวจจับเสียง" else "Sound Tracking Mode",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isThai) "ติดตามสภาพแวดล้อมและการรบกวน" else "Monitor sleep environment",
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                        }
                        Switch(
                            checked = monitor.monitoring,
                            onCheckedChange = { on ->
                                if (on) monitorViewModel.startMonitoring() else monitorViewModel.stopMonitoring()
                            }
                        )
                    }

                    // Live status pill — "กำลังทำงาน (Live Monitoring)" in the design.
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (monitor.monitoring) Primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (monitor.monitoring) Primary else Outline)
                            )
                            Text(
                                if (monitor.monitoring) {
                                    if (isThai) "กำลังทำงาน • ${monitor.liveLabel}" else "Live monitoring • ${monitor.liveLabel}"
                                } else {
                                    if (isThai) "ปิดอยู่" else "Off"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (monitor.monitoring) Primary else Outline
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    DetectionRow(
                        icon = Icons.Filled.GraphicEq,
                        label = if (isThai) "ตรวจจับการกรน" else "Snore Detection",
                        count = monitor.todaySnoreCount,
                        isThai = isThai
                    )
                    DetectionRow(
                        icon = Icons.Filled.ChildCare,
                        label = if (isThai) "ตรวจจับเสียงร้องไห้" else "Cry Detection",
                        count = monitor.todayCryCount,
                        isThai = isThai
                    )
                    DetectionRow(
                        icon = Icons.Filled.VolumeUp,
                        label = if (isThai) "เสียงดังรบกวน" else "Loud disturbances",
                        count = monitor.todayLoudCount,
                        isThai = isThai
                    )

                    monitor.lastError?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Error)
                    }
                    Text(
                        if (isThai) "ต้องอนุญาตไมโครโฟน และเปิดหน้านี้ค้างไว้ขณะติดตาม"
                        else "Needs microphone permission, and this screen must stay open while tracking.",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                }
            }

            // --- เส้นเวลาการนอน (24 ชม.) ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isThai) "เส้นเวลาการนอน (24 ชม.)" else "Sleep timeline (24h)",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                        )
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.12f)) {
                            Text(
                                if (isThai) "วันนี้" else "Today",
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Primary
                            )
                        }
                    }
                    SleepTimelineChart(todayLogs)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00:00", "06:00", "12:00", "18:00").forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                        }
                        Text(
                            if (isThai) "ตอนนี้" else "Now",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Primary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        SleepStat(
                            if (isThai) "เวลานอนรวม" else "Total sleep",
                            "${totalMinutesToday / 60}${if (isThai) " ชม. " else "h "}${totalMinutesToday % 60}${if (isThai) " น." else "m"}",
                            Modifier.weight(1f)
                        )
                        SleepStat(
                            if (isThai) "จำนวนครั้งที่งีบ" else "Naps",
                            if (isThai) "$napCount ครั้ง" else "$napCount",
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- เช็กอารมณ์ ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isThai) "เช็กอารมณ์" else "Mood check-in",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { navController.navigate(RECORD_SLEEP_HISTORY_ROUTE) }) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp), tint = Primary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isThai) "เพิ่มบันทึก" else "Add log",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary
                        )
                    }
                }
                MoodGrid(selectedMood = todaysMood, onSelect = { mood ->
                    childViewModel.logMoodForToday(mood)
                    scope.launch { snackbarHostState.showSnackbar(moodSavedMsg) }
                })
            }

            // --- เครื่องเล่นเสียงสีขาว ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Waves, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        if (isThai) "เครื่องเล่นเสียงสีขาว" else "White noise player",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                }
                NoisePlayerCard(noisePlayer)
            }

            // --- stat tiles ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepStat(if (isThai) "นอนครั้งล่าสุด" else "Last sleep", lastSleepText, Modifier.weight(1f))
                    SleepStat(
                        if (isThai) "เฉลี่ยต่อวัน" else "Avg/day",
                        "${weeklyAvgMinutes / 60}${if (isThai) " ชม." else "h"}",
                        Modifier.weight(1f)
                    )
                }
                // Stitch's 4th tile here is เมลานินสูงสุด; dropped per product decision — melatonin
                // isn't measured anywhere in this app.
                SleepStat(
                    if (isThai) "ความสม่ำเสมอ" else "Consistency",
                    "${consistencyLabel(consistencyPct, isThai)} ($consistencyPct%)",
                    Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Stitch's "ตื่นอยู่ / ช่วงเวลาตื่นปัจจุบัน" header card.
 *
 * The design shows a fixed "2 ชม. 45 นาที / เป้าหมาย 3 ชม."; here the elapsed figure is real and the
 * target comes from the child's age band, so an overshoot is a genuine overtiredness cue.
 */
@Composable
private fun AwakeStatusCard(
    isThai: Boolean,
    sleeping: Boolean,
    awakeMinutes: Long?,
    targetMinutes: Int,
    targetLabel: String
) {
    val progress = if (awakeMinutes == null || targetMinutes <= 0) 0f
    else (awakeMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
    val over = awakeMinutes != null && awakeMinutes > targetMinutes

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryFixed.copy(0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Bedtime, null, tint = HeroTop, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            sleeping -> if (isThai) "กำลังนอน" else "Asleep"
                            awakeMinutes == null -> if (isThai) "ยังไม่มีข้อมูล" else "No data yet"
                            else -> if (isThai) "ตื่นอยู่" else "Awake"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = OnPrimaryFixed
                    )
                    Text(
                        if (isThai) "ช่วงเวลาตื่นปัจจุบัน" else "Current wake window",
                        style = MaterialTheme.typography.labelSmall, color = OnPrimaryFixed.copy(0.7f)
                    )
                }
            }

            if (sleeping) {
                Text(
                    if (isThai) "ช่วงเวลาตื่นจะเริ่มนับใหม่เมื่อลูกน้อยตื่น"
                    else "The wake window restarts when your baby wakes up",
                    style = MaterialTheme.typography.bodySmall, color = OnPrimaryFixed.copy(0.8f)
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        awakeMinutes?.let {
                            if (isThai) "${it / 60} ชม. ${it % 60} นาที" else "${it / 60}h ${it % 60}m"
                        } ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (over) Error else OnPrimaryFixed
                    )
                    Text(
                        if (isThai) "/ เป้าหมาย $targetLabel" else "/ target $targetLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnPrimaryFixed.copy(0.75f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(100.dp)),
                    color = if (over) Error else HeroTop,
                    trackColor = OnPrimaryFixed.copy(0.12f)
                )
                if (over) {
                    Text(
                        if (isThai) "เกินช่วงเวลาตื่นที่แนะนำแล้ว ลองพาเข้านอน"
                        else "Past the recommended wake window — try settling for sleep",
                        style = MaterialTheme.typography.labelSmall, color = Error, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Stitch's AI insight card. The design hardcodes the 4-month regression; this shows the band that
 * matches the child's actual age and says nothing when they're outside a known regression window —
 * an unprompted "regression detected" for a 2-year-old would be a false signal.
 */
@Composable
private fun WakeRegressionCard(isThai: Boolean, ageMonths: Int, onOpenPlan: () -> Unit) {
    val window = SLEEP_REGRESSIONS.firstOrNull { ageMonths in it.first } ?: return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TertiaryFixed.copy(0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = HeroTop, modifier = Modifier.size(18.dp))
                Text(
                    if (isThai) "ตรวจพบภาวะถดถอยของการนอนช่วง ${window.second} เดือน"
                    else "Possible ${window.second}-month sleep regression",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = OnPrimaryFixed
                )
            }
            Text(
                if (isThai) "รูปแบบการนอนช่วงวัยนี้มักตื่นบ่อยขึ้นในเวลากลางคืน ลองลดช่วงเวลาตื่นสุดท้ายลง 15 นาทีเพื่อป้องกันอาการเหนื่อยล้าสะสม"
                else "Night wakings are common at this age. Try shortening the last wake window by 15 minutes to avoid accumulated overtiredness.",
                style = MaterialTheme.typography.bodySmall, color = OnPrimaryFixed.copy(0.85f)
            )
            Surface(onClick = onOpenPlan, color = androidx.compose.ui.graphics.Color.Transparent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isThai) "ดูแผนปฏิบัติการ" else "View action plan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = HeroTop
                    )
                    Icon(Icons.Filled.ChevronRight, null, tint = HeroTop, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Well-documented infant sleep-regression windows, in months. */
private val SLEEP_REGRESSIONS = listOf(
    3..5 to 4,
    7..9 to 8,
    11..13 to 12,
    17..19 to 18
)

private fun consistencyLabel(pct: Int, isThai: Boolean): String = when {
    pct >= 80 -> if (isThai) "สูง" else "High"
    pct >= 50 -> if (isThai) "ปานกลาง" else "Medium"
    else -> if (isThai) "ต่ำ" else "Low"
}

@Composable
private fun DetectionRow(icon: ImageVector, label: String, count: Int, isThai: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Secondary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (isThai) "$count ครั้ง" else "$count×",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) Primary else Outline
        )
    }
}
