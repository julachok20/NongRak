package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen widget: latest weight/height with trend vs the previous record.
 * Matches the 25.Widget "Growth Tracker" 2x2 card: label chip + child name headline,
 * then a stacked label/value row per metric with a trend arrow at the row end.
 */
class GrowthTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataFetcher.fetchGrowth()
        provideContent { Content(snapshot) }
    }

    @Composable
    private fun MetricRow(label: String, value: String, unit: String, delta: Double?) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = label, style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant), fontSize = WidgetType.label))
                Text(
                    text = "$value $unit",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.stat)
                )
            }
            when {
                delta == null -> {}
                delta > 0 -> Text("▲", style = TextStyle(color = ColorProvider(WidgetColors.Green)))
                delta < 0 -> Text("▼", style = TextStyle(color = ColorProvider(WidgetColors.Error)))
                else -> Text("–", style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant)))
            }
        }
    }

    @Composable
    private fun Content(snapshot: GrowthSnapshot) {
        WidgetCard(background = WidgetColors.SurfaceContainerLow, accentColor = WidgetColors.Secondary, onClick = openAppTo("growth_metrics")) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically) {
                LabelChip("การเติบโต", chipColor = WidgetColors.PrimaryContainer, onColor = WidgetColors.OnPrimaryContainer)
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text("📈", style = TextStyle(color = ColorProvider(WidgetColors.Primary)))
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = snapshot.childName ?: "ลูกน้อย",
                style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.headline)
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            if (snapshot.weightKg == null && snapshot.heightCm == null) {
                Text(
                    text = "ยังไม่มีข้อมูลการเจริญเติบโต",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface))
                )
            } else {
                MetricRow("น้ำหนัก", snapshot.weightKg?.let { "%.1f".format(it) } ?: "-", "กก.", snapshot.weightDeltaKg)
                Spacer(modifier = GlanceModifier.height(8.dp))
                MetricRow("ส่วนสูง", snapshot.heightCm?.let { "%.1f".format(it) } ?: "-", "ซม.", snapshot.heightDeltaCm)
            }
        }
    }
}
