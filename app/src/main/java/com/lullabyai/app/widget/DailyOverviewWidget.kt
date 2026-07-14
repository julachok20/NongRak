package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen widget: today's logging at a glance, tap to open the app.
 * Matches the 25.Widget "Daily Overview" full-width card: title + calendar row, a ring,
 * then 3 stat chips. The ring shows "today's logging completion" (of 3 tracked categories:
 * fed, slept, food logged) — this app has no medical "health score", so rather than fabricate
 * one the ring is repurposed to something real and transparently labeled. The mockup's
 * AI-insight banner is skipped — that needs a live network call inside the widget.
 */
class DailyOverviewWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataFetcher.fetchDailyOverview()
        val ring = renderProgressRing(
            context = context,
            sizeDp = 72,
            strokeWidthDp = 7,
            percent = snapshot.percentComplete,
            trackColor = WidgetColors.Primary.copy(alpha = 0.12f),
            fillColor = WidgetColors.Primary
        )
        provideContent { Content(snapshot, ring) }
    }

    @Composable
    private fun Content(snapshot: DailyOverviewSnapshot, ring: android.graphics.Bitmap) {
        WidgetCard(background = WidgetColors.Surface, accentColor = WidgetColors.Primary, onClick = openAppTo("dashboard")) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    text = "สรุปภาพรวมรายวัน",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.headline)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text("📅", style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant)))
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = snapshot.childName ?: "ลูกน้อย",
                style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant), fontSize = WidgetType.label)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(72.dp))
                Box(contentAlignment = Alignment.Center) {
                    androidx.glance.layout.Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        Text(
                            text = "${snapshot.percentComplete}%",
                            style = TextStyle(color = ColorProvider(WidgetColors.Primary), fontWeight = FontWeight.Bold, fontSize = WidgetType.stat)
                        )
                        Text(
                            text = "บันทึกวันนี้",
                            style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant), fontSize = WidgetType.caption)
                        )
                    }
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row {
                StatChip(
                    icon = "😴",
                    label = "การนอน",
                    value = if (snapshot.sleepMinutesToday > 0) "${snapshot.sleepMinutesToday / 60} ชม. ${snapshot.sleepMinutesToday % 60} น." else "-",
                    chipColor = WidgetColors.SurfaceContainerLow,
                    onColor = WidgetColors.OnSurface
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                StatChip(
                    icon = "🍽️",
                    label = "โภชนาการ",
                    value = if (snapshot.kcalToday > 0) "${snapshot.kcalToday} kcal" else "-",
                    chipColor = WidgetColors.SurfaceContainerLow,
                    onColor = WidgetColors.OnSurface
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                StatChip(
                    icon = "🍼",
                    label = "ให้นมล่าสุด",
                    value = snapshot.lastFeeding ?: "-",
                    chipColor = WidgetColors.SurfaceContainerLow,
                    onColor = WidgetColors.OnSurface
                )
            }
        }
    }
}
