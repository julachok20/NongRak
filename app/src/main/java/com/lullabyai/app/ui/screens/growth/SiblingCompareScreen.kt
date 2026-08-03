@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.growth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.SiblingCompareViewModel

/** Compares two of a parent's children's growth curves aligned by AGE (months), not calendar date —
 *  siblings are born on different dates, so the useful question is "how did each child grow at the
 *  same age", not "who's bigger right now". Only reachable when the account has 2+ children. */
@Composable
fun SiblingCompareScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val vm: SiblingCompareViewModel = viewModel()
    val childState by childViewModel.uiState.collectAsState()
    val ui by vm.ui.collectAsState()
    val children = childState.children

    var idA by remember(children) { mutableStateOf(children.getOrNull(0)?.id ?: "") }
    var idB by remember(children) { mutableStateOf(children.getOrNull(1)?.id ?: children.getOrNull(0)?.id ?: "") }
    var showWeight by remember { mutableStateOf(true) }

    val childA = children.firstOrNull { it.id == idA }
    val childB = children.firstOrNull { it.id == idB }

    LaunchedEffect(idA, idB) { if (idA.isNotBlank() && idB.isNotBlank()) vm.load(idA, idB) }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "เปรียบเทียบพี่น้อง" else "Sibling Comparison", onBack = onBack, navController = navController) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (children.size < 2) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isThai) "ต้องมีลูกอย่างน้อย 2 คนในบัญชีจึงจะเปรียบเทียบได้" else "Add a second child to your account to compare growth.",
                        color = Outline, style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Scaffold
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChildPicker(if (isThai) "ลูกคนที่ 1" else "Child A", children, idA, Color(0xFF3F51B5), Modifier.weight(1f)) { idA = it }
                ChildPicker(if (isThai) "ลูกคนที่ 2" else "Child B", children, idB, Color(0xFFEF6C00), Modifier.weight(1f)) { idB = it }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = showWeight, onClick = { showWeight = true }, label = { Text(if (isThai) "น้ำหนัก" else "Weight") }, leadingIcon = { Icon(Icons.Filled.MonitorWeight, null, modifier = Modifier.size(16.dp)) })
                FilterChip(selected = !showWeight, onClick = { showWeight = false }, label = { Text(if (isThai) "ส่วนสูง" else "Height") }, leadingIcon = { Icon(Icons.Filled.Height, null, modifier = Modifier.size(16.dp)) })
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        (if (isThai) "อายุ (เดือน) เทียบกับ " else "Age (months) vs. ") + (if (showWeight) (if (isThai) "น้ำหนัก (กก.)" else "weight (kg)") else (if (isThai) "ส่วนสูง (ซม.)" else "height (cm)")),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ui.loading) {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
                    } else if (childA != null && childB != null) {
                        SiblingCompareChart(childA, ui.recordsA, childB, ui.recordsB, showWeight)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendDot(childA.name, Color(0xFF3F51B5))
                            LegendDot(childB.name, Color(0xFFEF6C00))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildPicker(label: String, children: List<Child>, selectedId: String, color: Color, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = children.firstOrNull { it.id == selectedId }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
                Text(selected?.name ?: "-", maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                children.forEach { c ->
                    DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelect(c.id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun LegendDot(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Two-series line chart, both plotted against age-in-months on the x-axis (not calendar date) so
 *  the two children's curves are directly comparable regardless of birthdate. */
@Composable
private fun SiblingCompareChart(childA: Child, recordsA: List<GrowthRecord>, childB: Child, recordsB: List<GrowthRecord>, isWeight: Boolean) {
    fun points(birthdate: String, records: List<GrowthRecord>): List<Pair<Int, Double>> =
        records.reversed().mapNotNull { r ->
            val v = if (isWeight) r.weightKg else r.heightCm
            val age = ageMonthsAt(birthdate, r.recordedAt)
            if (v != null) age to v else null
        }

    val ptsA = points(childA.birthdate, recordsA)
    val ptsB = points(childB.birthdate, recordsB)
    if (ptsA.isEmpty() && ptsB.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("-", color = Outline) }
        return
    }
    val allAges = (ptsA + ptsB).map { it.first }
    val allValues = (ptsA + ptsB).map { it.second }
    val minAge = allAges.min(); val maxAge = allAges.max().coerceAtLeast(minAge + 1)
    val minV = allValues.min() * 0.95; val maxV = allValues.max() * 1.05

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        fun x(age: Int) = size.width * (age - minAge).toFloat() / (maxAge - minAge).toFloat()
        fun y(v: Double) = size.height * (1f - ((v - minV) / (maxV - minV)).toFloat())

        fun drawSeries(points: List<Pair<Int, Double>>, color: Color) {
            if (points.isEmpty()) return
            val path = Path()
            points.forEachIndexed { i, (age, v) ->
                val px = x(age); val py = y(v)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(px, py))
            }
            drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        drawSeries(ptsA, Color(0xFF3F51B5))
        drawSeries(ptsB, Color(0xFFEF6C00))
    }
}
