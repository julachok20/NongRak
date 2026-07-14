package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen widget: nearest upcoming vaccine + days remaining.
 * Matches the 25.Widget "Vaccine Reminder" 2x2 card: icon box + "วัคซีน" label chip row,
 * then vaccine name headline + days-remaining line, on a soft coral tint.
 */
class VaccineReminderWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataFetcher.fetchVaccine()
        provideContent { Content(snapshot) }
    }

    @Composable
    private fun Content(snapshot: VaccineSnapshot) {
        WidgetCard(
            background = WidgetColors.Surface,
            accentColor = WidgetColors.Secondary,
            onClick = openAppTo("vaccine_book")
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                IconBadge("💉", badgeColor = WidgetColors.SecondaryContainer, iconColor = WidgetColors.OnSecondaryContainer, size = 36.dp)
                Spacer(modifier = GlanceModifier.defaultWeight())
                LabelChip("วัคซีน", chipColor = WidgetColors.SecondaryContainer, onColor = WidgetColors.OnSecondaryContainer)
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            if (snapshot.vaccineName == null) {
                Text(
                    text = "ไม่มีวัคซีนที่ต้องนัด",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface))
                )
            } else {
                Text(
                    text = snapshot.vaccineName,
                    style = TextStyle(color = ColorProvider(WidgetColors.Secondary), fontWeight = FontWeight.Bold, fontSize = WidgetType.headline)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("🕐 ", style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant)))
                    Text(
                        text = when (snapshot.daysUntil) {
                            0L -> "วันนี้"
                            1L -> "พรุ่งนี้"
                            else -> "อีก ${snapshot.daysUntil} วัน"
                        },
                        style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant))
                    )
                }
            }
        }
    }
}
