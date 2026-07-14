package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen widget: latest main-sleep session duration. Matches the 25.Widget
 * "Sleep Analytics" full-width card: "บันทึกการนอน" label + duration headline + a button,
 * plus the mockup's decorative sleep-quality wave (a real rendered Bitmap via WidgetGraphics
 * — the mockup's own SVG path is a fixed aesthetic curve too, not a real data plot). The
 * mockup's separate AI-insight text is skipped — that would need a live network call inside
 * the widget, which is a real cost/latency tradeoff, not a styling gap.
 */
class SleepAnalyticsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataFetcher.fetchSleepAnalytics()
        val wave = renderSleepWave(
            context = context,
            widthDp = 260,
            heightDp = 64,
            lineColor = WidgetColors.Primary,
            fillColor = WidgetColors.Primary
        )
        provideContent { Content(snapshot, wave) }
    }

    @Composable
    private fun Content(snapshot: SleepAnalyticsSnapshot, wave: android.graphics.Bitmap) {
        WidgetCard(background = WidgetColors.Surface, accentColor = WidgetColors.Primary, onClick = openAppTo("sleep_tracker")) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "บันทึกการนอน",
                        style = TextStyle(color = ColorProvider(WidgetColors.Primary), fontWeight = FontWeight.Medium, fontSize = WidgetType.label)
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = if (snapshot.lastSleepHours != null) {
                            "ล่าสุด: ${snapshot.lastSleepHours} ชม. ${snapshot.lastSleepMinutes} นาที"
                        } else {
                            "ยังไม่มีข้อมูลการนอน"
                        },
                        style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.stat)
                    )
                }
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(999.dp)
                        .background(ColorProvider(WidgetColors.PrimaryContainer))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "😴 บันทึก",
                        style = TextStyle(color = ColorProvider(WidgetColors.OnPrimaryContainer), fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .cornerRadius(16.dp)
                    .background(ColorProvider(WidgetColors.SurfaceContainerLow)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(wave),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().height(64.dp),
                    contentScale = ContentScale.FillBounds
                )
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(999.dp)
                        .background(ColorProvider(WidgetColors.Surface))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("แนวโน้มการนอน", style = TextStyle(color = ColorProvider(WidgetColors.Primary)))
                }
            }
        }
    }
}
