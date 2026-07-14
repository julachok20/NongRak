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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen widget: % of milestones achieved + next unachieved one.
 * Matches the 25.Widget "Development Milestone" 2x2 card: label chip + star icon row,
 * a real rendered progress ring (Bitmap via WidgetGraphics — Glance has no Canvas
 * composable, so this pre-renders the ring instead of approximating it with bars),
 * then "ทักษะถัดไป" label + skill name.
 */
class MilestoneWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataFetcher.fetchMilestones()
        val ring = renderProgressRing(
            context = context,
            sizeDp = 56,
            strokeWidthDp = 5,
            percent = snapshot.percentAchieved,
            trackColor = WidgetColors.Tertiary.copy(alpha = 0.15f),
            fillColor = WidgetColors.Tertiary
        )
        provideContent { Content(snapshot, ring) }
    }

    @Composable
    private fun Content(snapshot: MilestoneSnapshot, ring: android.graphics.Bitmap) {
        WidgetCard(
            background = WidgetColors.Surface,
            accentColor = WidgetColors.Tertiary,
            onClick = openAppTo("child_development")
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                LabelChip("พัฒนาการ", chipColor = WidgetColors.TertiaryContainer, onColor = WidgetColors.OnPrimaryContainer)
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text("⭐", style = TextStyle(color = ColorProvider(WidgetColors.Tertiary)))
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            if (snapshot.totalCount == 0) {
                Text(
                    text = "ยังไม่มีข้อมูลพัฒนาการ",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface))
                )
            } else {
                Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = GlanceModifier.size(56.dp)) {
                        Image(provider = ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(56.dp))
                    }
                    Text(
                        text = "${snapshot.percentAchieved}%",
                        style = TextStyle(color = ColorProvider(WidgetColors.Tertiary), fontWeight = FontWeight.Bold, fontSize = WidgetType.stat)
                    )
                }
                if (snapshot.nextMilestoneName != null) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Text(text = "ทักษะถัดไป", style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant), fontSize = WidgetType.label))
                    Text(
                        text = snapshot.nextMilestoneName,
                        style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.headline)
                    )
                }
            }
        }
    }
}
