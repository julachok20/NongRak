package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lullabyai.app.data.repository.MedicationRepository

private val DoseIdKey = ActionParameters.Key<String>("dose_id")

/** Marks a dose taken and refreshes the widget in place — the mockup's "ทานแล้ว" button. */
class MarkDoseTakenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val doseId = parameters[DoseIdKey] ?: return
        MedicationRepository().markDoseTaken(doseId)
        MedicineDoseWidget().update(context, glanceId)
    }
}

/**
 * Home-screen widget: today's pending medicine doses with a one-tap "taken" action.
 * Matches the 25.Widget "Medical Reminder" full-width card: icon box + title + "วันนี้" chip,
 * then rows with a time block, divider, medicine name, and a pill action button.
 */
class MedicineDoseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataFetcher.fetchMedicineDoses()
        provideContent { Content(snapshot) }
    }

    private fun periodLabel(time: String?): String {
        val hour = time?.substringBefore(":")?.toIntOrNull() ?: return ""
        return when (hour) {
            in 5..11 -> "เช้า"
            in 12..16 -> "กลางวัน"
            in 17..20 -> "เย็น"
            else -> "กลางคืน"
        }
    }

    @Composable
    private fun Content(snapshot: MedicineDoseSnapshot) {
        WidgetCard(background = WidgetColors.Surface, accentColor = WidgetColors.Error) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                IconBadge("💊", badgeColor = WidgetColors.ErrorContainer, iconColor = WidgetColors.Error, size = 28.dp)
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "แจ้งเตือนยา",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.headline),
                    modifier = GlanceModifier.defaultWeight()
                )
                LabelChip("วันนี้", chipColor = WidgetColors.ErrorContainer, onColor = WidgetColors.Error)
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            if (snapshot.doses.isEmpty()) {
                Text(
                    text = "ไม่มียาที่ต้องทานวันนี้",
                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface)),
                    modifier = GlanceModifier.clickable(openAppTo("medication_history"))
                )
            } else {
                snapshot.doses.forEachIndexed { index, dose ->
                    if (index > 0) Spacer(modifier = GlanceModifier.height(8.dp))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(16.dp)
                            .background(ColorProvider(WidgetColors.SurfaceContainerLow))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Row(
                            modifier = GlanceModifier.defaultWeight().clickable(openAppTo("medication_history")),
                            verticalAlignment = Alignment.Vertical.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                                Text(
                                    text = dose.scheduledTime ?: "-",
                                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = periodLabel(dose.scheduledTime),
                                    style = TextStyle(color = ColorProvider(WidgetColors.OnSurfaceVariant))
                                )
                            }
                            Box(
                                modifier = GlanceModifier
                                    .padding(horizontal = 10.dp)
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(ColorProvider(WidgetColors.OnSurfaceVariant))
                            ) {}
                            Text(
                                text = dose.medicineName,
                                style = TextStyle(color = ColorProvider(WidgetColors.OnSurface), fontWeight = FontWeight.Bold, fontSize = WidgetType.body)
                            )
                        }
                        if (dose.taken) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(999.dp)
                                    .background(ColorProvider(WidgetColors.OnSurfaceVariant))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("ทานแล้ว", style = TextStyle(color = ColorProvider(WidgetColors.Surface), fontWeight = FontWeight.Bold))
                            }
                        } else {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(999.dp)
                                    .background(ColorProvider(WidgetColors.Primary))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .clickable(actionRunCallback<MarkDoseTakenAction>(actionParametersOf(DoseIdKey to dose.id)))
                            ) {
                                Text("ทานแล้ว", style = TextStyle(color = ColorProvider(WidgetColors.OnPrimaryContainer), fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}
