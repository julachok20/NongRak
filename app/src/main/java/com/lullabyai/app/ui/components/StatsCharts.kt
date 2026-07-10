package com.lullabyai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Shared "not enough data" placeholder for a chart card — used across the Statistics insight
 *  screens instead of leaving a blank/broken chart when a child has no logs yet for that metric. */
@Composable
fun EmptyChartState(isThai: Boolean, message: String? = null) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(
            message ?: (if (isThai) "ยังไม่มีข้อมูลเพียงพอสำหรับแสดงกราฟ" else "Not enough data yet to show this chart"),
            color = Outline, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center
        )
    }
}

/** 7-day vertical bar chart with a value label above each bar and a day-of-week row below.
 *  Bars at/above [targetLine] (if given) turn green instead of Primary. */
@Composable
fun WeeklyBarChart(values: List<Pair<LocalDate, Int>>, targetLine: Float? = null, isThai: Boolean = true, unit: String = "") {
    val maxVal = maxOf(values.maxOf { it.second }, (targetLine ?: 0f).toInt()).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { (_, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("$v$unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    val frac = (v.toFloat() / maxVal).coerceIn(0.02f, 1f)
                    Box(
                        Modifier.width(18.dp).fillMaxHeight(frac)
                            .background(if (targetLine != null && v >= targetLine) Color(0xFF2E7D32) else Primary, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { (d, _) ->
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, if (isThai) Locale.forLanguageTag("th") else Locale.ENGLISH),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Outline, textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 7-day polyline chart (e.g. milk oz, weight) with a day-of-week row below. */
@Composable
fun WeeklyLineChart(values: List<Pair<LocalDate, Float>>, isThai: Boolean = true) {
    val maxVal = (values.maxOf { it.second }).coerceAtLeast(1f)
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            fun y(v: Float) = size.height * (1f - (v / maxVal).coerceIn(0f, 1f))
            val path = Path()
            values.forEachIndexed { i, (_, v) ->
                val px = i * stepX; val py = y(v)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                drawCircle(color = Primary, radius = 4.dp.toPx(), center = Offset(px, py))
            }
            drawPath(path, color = Primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { (d, _) ->
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, if (isThai) Locale.forLanguageTag("th") else Locale.ENGLISH),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Outline, textAlign = TextAlign.Center
                )
            }
        }
    }
}
