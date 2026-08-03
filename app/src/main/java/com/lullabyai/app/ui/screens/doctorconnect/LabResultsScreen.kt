@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.HealthAttention
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * "ผลการตรวจ" — the parent-facing result view.
 *
 * There is no `lab_results` table: the doctor's Clinical Record Entry writes vitals and narrative to
 * `doctor_visits`, so that table *is* the result set. Both rendering paths the product asked for are
 * implemented and chosen per-metric off the data itself:
 *  - **structured path** — every numeric column the doctor filled in (weight, height, temperature,
 *    systolic BP) becomes a trend chart plus a reference-range read-out where a range exists;
 *  - **text path** — the doctor's own diagnosis / treatment plan / advice, which is all that renders
 *    when nothing numeric was recorded.
 */
@Composable
fun LabResultsScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val child = childState.child
    val vm: LabResultsViewModel = viewModel()
    val ui by vm.ui.collectAsState()

    LaunchedEffect(child?.id) { child?.id?.let { vm.load(it) } }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ผลการตรวจ" else "Lab Results",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ConnectBottomNav() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 1. Patient summary
            if (child != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(48.dp).background(Primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.ChildCare, null, tint = Primary) }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    child.name.ifBlank { if (s.isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                                )
                                Text(childAgeLabel(child, s.isThai), style = MaterialTheme.typography.bodySmall, color = Outline)
                            }
                            child.weightKg?.let {
                                Text(
                                    (if (s.isThai) "น้ำหนัก: " else "Weight: ") + "$it " + (if (s.isThai) "กก." else "kg"),
                                    style = MaterialTheme.typography.labelSmall, color = Outline
                                )
                            }
                        }
                    }
                }
            }

            when {
                ui.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }

                ui.error != null -> item {
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                ui.visits.isEmpty() -> item { EmptyResults(s.isThai) }

                else -> {
                    // ---- Structured path: charts for every numeric metric the doctor recorded ----
                    if (ui.hasChartableData) {
                        item { VitalsSummaryTable(ui.summarySeries, s.isThai) }
                        item { ConnectSectionHeader(if (s.isThai) "ค่าที่วัดได้ (กราฟแนวโน้ม)" else "Measured values (trend)", Icons.Filled.Science) }
                        items(ui.series, key = { it.key }) { series -> MetricCard(series, s.isThai) }
                    }

                    // ---- Text path: the doctor's own write-up ---------------------------------
                    if (ui.narratives.isNotEmpty()) {
                        item { ConnectSectionHeader(if (s.isThai) "บันทึกจากแพทย์" else "Notes from the doctor", Icons.Filled.Info) }
                        items(ui.narratives, key = { it.id }) { visit -> NarrativeCard(visit, s.isThai) }
                    }

                    // ---- Summary insight, derived from what's above ---------------------------
                    item { InsightCard(ui, s.isThai, childName = child?.name.orEmpty()) }

                    if (!ui.hasChartableData && ui.narratives.isEmpty()) {
                        item { EmptyResults(s.isThai) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyResults(isThai: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Science, null, tint = Outline, modifier = Modifier.size(40.dp))
        Text(
            if (isThai) "ยังไม่มีผลการตรวจ" else "No lab results yet",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
        )
        Text(
            if (isThai) "ผลการตรวจจะปรากฏที่นี่หลังคุณหมอบันทึกข้อมูลการตรวจของลูกคุณ"
            else "Results will appear here once your doctor records a lab test for your child.",
            style = MaterialTheme.typography.bodySmall, color = Outline, textAlign = TextAlign.Center
        )
    }
}

/** Compact at-a-glance table — one row per metric with latest value and measurement date. */
@Composable
private fun VitalsSummaryTable(series: List<LabMetricSeries>, isThai: Boolean) {
    if (series.isEmpty()) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isThai) "สรุปค่าล่าสุด" else "Latest values",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (isThai) "รายการ" else "Metric",
                    Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isThai) "ค่า" else "Value",
                    Modifier.weight(0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isThai) "วันที่" else "Date",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
            HorizontalDivider(color = Outline.copy(alpha = 0.25f))

            series.forEachIndexed { index, row ->
                val accent = when (row.inRange) {
                    true -> HealthGood
                    false -> HealthAttention
                    null -> Primary
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isThai) row.labelTh else row.labelEn,
                        Modifier.weight(1.1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        Modifier.weight(0.9f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(accent, CircleShape))
                        Text(
                            buildString {
                                append(row.latest?.let { formatMetric(it) } ?: "—")
                                append(' ')
                                append(row.unit)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                    Text(
                        thaiDateTimeLabel(row.points.lastOrNull()?.first, isThai, withTime = false)
                            ?: (if (isThai) "—" else "—"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline,
                        textAlign = TextAlign.End
                    )
                }
                if (index < series.lastIndex) {
                    HorizontalDivider(color = Outline.copy(alpha = 0.12f))
                }
            }
        }
    }
}

/** Structured metric: latest value, reference range verdict, and a sparkline once there are ≥2 points. */
@Composable
private fun MetricCard(series: LabMetricSeries, isThai: Boolean) {
    val inRange = series.inRange
    val accent = when (inRange) {
        true -> HealthGood
        false -> HealthAttention
        null -> Primary
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isThai) series.labelTh else series.labelEn,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    thaiDateTimeLabel(series.points.lastOrNull()?.first, isThai, withTime = false)?.let {
                        Text(
                            (if (isThai) "วันที่ตรวจ: " else "Measured: ") + it,
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                    }
                }
                when (inRange) {
                    true -> StatusPill(if (isThai) "ปกติ" else "Normal", HealthGood, Icons.Filled.CheckCircle)
                    false -> StatusPill(if (isThai) "ควรเฝ้าระวัง" else "Monitor", HealthAttention, Icons.Filled.Warning, filled = true)
                    null -> StatusPill(if (isThai) "แนวโน้ม" else "Trend", Primary)
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    series.latest?.let { formatMetric(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent
                )
                Text(series.unit, style = MaterialTheme.typography.labelMedium, color = Outline, modifier = Modifier.padding(bottom = 6.dp))
            }

            if (series.referenceLow != null || series.referenceHigh != null) {
                Text(
                    (if (isThai) "เกณฑ์อ้างอิง: " else "Reference: ") +
                        listOfNotNull(series.referenceLow?.let { formatMetric(it) }, series.referenceHigh?.let { formatMetric(it) })
                            .joinToString(" - "),
                    style = MaterialTheme.typography.labelMedium, color = Outline
                )
                RangeBar(series, accent)
            }

            // A single data point has no trend to draw — the value + range read-out above is the whole story.
            if (series.points.size >= 2) {
                Sparkline(series.points.map { it.second }, accent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    thaiDateTimeLabel(series.points.first().first, isThai, withTime = false)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                    thaiDateTimeLabel(series.points.last().first, isThai, withTime = false)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
            }
        }
    }
}

/** Where the latest value sits inside its reference band — Stitch's thin progress rail. */
@Composable
private fun RangeBar(series: LabMetricSeries, accent: Color) {
    val lo = series.referenceLow ?: return
    val hi = series.referenceHigh ?: return
    val v = series.latest ?: return
    val span = (hi - lo).takeIf { it > 0.0 } ?: return
    // Pad the band by 30% on each side so an out-of-range value is still visible on the rail.
    val pad = span * 0.3
    val fraction = ((v - (lo - pad)) / (span + 2 * pad)).coerceIn(0.0, 1.0).toFloat()

    val bandFraction = (span / (span + 2 * pad)).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
        ) {
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(bandFraction).fillMaxHeight()
                    .background(HealthGood.copy(alpha = 0.30f), RoundedCornerShape(100.dp))
            )
        }
        // Marker positioned with weights rather than an offset, so it tracks the bar's real width.
        Row(Modifier.fillMaxWidth()) {
            if (fraction > 0.01f) Spacer(Modifier.weight(fraction))
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            if (fraction < 0.99f) Spacer(Modifier.weight(1f - fraction))
        }
    }
}

/** Minimal line chart — Canvas rather than a charting dependency, since it's one polyline. */
@Composable
private fun Sparkline(values: List<Double>, color: Color) {
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.surfaceVariant

    Canvas(Modifier.fillMaxWidth().height(88.dp)) {
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        fun y(v: Double) = (size.height - ((v - min) / range * size.height)).toFloat()

        // Baseline + midline
        listOf(0f, 0.5f, 1f).forEach { f ->
            drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
        }

        val path = Path().apply {
            values.forEachIndexed { i, v ->
                val px = stepX * i
                val py = y(v)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(path, color, style = Stroke(width = 3f))
        values.forEachIndexed { i, v -> drawCircle(color, radius = 5f, center = Offset(stepX * i, y(v))) }
    }
}

/** Text path — verbatim doctor prose, no interpretation applied. */
@Composable
private fun NarrativeCard(visit: DoctorVisit, isThai: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thaiDateTimeLabel(visit.visitDatetime, isThai, withTime = false)
                        ?: (if (isThai) "ไม่ระบุวันที่" else "Undated"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                visit.facilityName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            visit.diagnosis?.takeIf { it.isNotBlank() }?.let {
                NarrativeRow(if (isThai) "การวินิจฉัย" else "Diagnosis", it)
            }
            visit.treatmentPlan?.takeIf { it.isNotBlank() }?.let {
                NarrativeRow(if (isThai) "แผนการรักษา" else "Treatment plan", it)
            }
            visit.doctorAdvice?.takeIf { it.isNotBlank() }?.let {
                NarrativeRow(if (isThai) "คำแนะนำจากแพทย์" else "Doctor's advice", it)
            }
        }
    }
}

@Composable
private fun NarrativeRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Outline, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * "NongRak AI Insight". Deliberately rule-based rather than a model call: it only restates what the
 * recorded numbers already say (in/out of every reference range) and points at the doctor's own
 * advice. Inventing clinical interpretation from a chart would be unsafe.
 */
@Composable
private fun InsightCard(ui: LabResultsUiState, isThai: Boolean, childName: String) {
    val ranged = ui.series.filter { it.inRange != null }
    val flagged = ranged.filter { it.inRange == false }
    val name = childName.ifBlank { if (isThai) "ลูกของคุณ" else "your child" }

    val headline = when {
        ranged.isEmpty() && ui.narratives.isNotEmpty() ->
            if (isThai) "ผลการตรวจครั้งนี้เป็นบันทึกข้อความจากคุณหมอ ไม่มีค่าตัวเลขให้เปรียบเทียบเกณฑ์อ้างอิง"
            else "This result set is the doctor's written record — there are no numeric values to compare against reference ranges."
        flagged.isEmpty() ->
            if (isThai) "ค่าที่วัดได้ของ$name อยู่ในเกณฑ์อ้างอิงทั้งหมด"
            else "All measured values for $name fall inside their reference ranges."
        else -> {
            val list = flagged.joinToString(", ") { if (isThai) it.labelTh else it.labelEn }
            if (isThai) "พบค่าที่อยู่นอกเกณฑ์อ้างอิง: $list — แนะนำให้ปรึกษาคุณหมอในการนัดหมายครั้งถัดไป"
            else "Outside the reference range: $list — please raise this at the next appointment."
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                Text("NongRak AI Insight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(headline, style = MaterialTheme.typography.bodyLarge, color = Color.White)

            ui.narratives.firstOrNull()?.doctorAdvice?.takeIf { it.isNotBlank() }?.let { advice ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Info, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            if (isThai) "คำแนะนำเพิ่มเติมจากคุณหมอ" else "Additional advice from your doctor",
                            style = MaterialTheme.typography.labelMedium, color = Color.White
                        )
                    }
                    Text(advice, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            Text(
                if (isThai) "สรุปจากค่าที่คุณหมอบันทึกไว้ ไม่ใช่การวินิจฉัยทางการแพทย์"
                else "Summarised from the values your doctor recorded — not a medical diagnosis.",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/** Drops the trailing ".0" that Double carries for whole numbers (12.0 kg -> "12"). */
private fun formatMetric(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)
