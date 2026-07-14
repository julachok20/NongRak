package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen widget: 3 quick-log shortcuts. Matches the 25.Widget "Quick Action Hub":
 * light card, "บันทึกกิจกรรม" header, and white circular icon buttons. Each tap opens the
 * screen that actually logs that item (mirroring QuickLogHubScreen's own routing).
 */
class QuickActionHubWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun RowScope.Action(icon: String, label: String, route: String) {
        Column(
            modifier = GlanceModifier.defaultWeight().clickable(openAppTo(route)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = GlanceModifier
                    .size(48.dp)
                    .cornerRadius(24.dp)
                    .background(ColorProvider(WidgetColors.Surface)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(text = icon, style = TextStyle(fontSize = 20.sp))
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.OnSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = WidgetType.label,
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    @Composable
    private fun Content() {
        WidgetCard(background = WidgetColors.SurfaceContainerLow, accentColor = WidgetColors.Primary) {
            Text(
                text = "บันทึกกิจกรรม",
                style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.headline)
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Action("🍼", "บันทึกนม", "milk_record")
                Action("🍽️", "บันทึกอาหาร", "calorie_calculator")
                Action("💊", "บันทึกยา", "add_medicine_schedule")
            }
        }
    }
}
