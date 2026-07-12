package com.lullabyai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lullabyai.app.MainActivity
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.data.repository.SleepRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.TimeUtils

/**
 * Home-screen widget: last feeding + last sleep time at a glance, tap to open the app.
 * Refreshed on the OS's widget update cycle (see quick_info_widget_info.xml).
 */
class QuickInfoWidget : GlanceAppWidget() {

    private data class WidgetContent(
        val childName: String?,
        val lastFeeding: String?,
        val isSleeping: Boolean,
        val lastSleep: String?
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val childId = ChildSelection.current()

        val content = if (childId == null) {
            WidgetContent(childName = null, lastFeeding = null, isSleeping = false, lastSleep = null)
        } else {
            val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
            val lastMilk = NutritionRepository().getMilkLogs(childId).firstOrNull()
            val lastSleepLog = SleepRepository().getLogs(childId).getOrDefault(emptyList()).firstOrNull()
            WidgetContent(
                childName = child?.name,
                lastFeeding = TimeUtils.localTime(lastMilk?.loggedAt),
                isSleeping = lastSleepLog != null && lastSleepLog.endTime == null,
                lastSleep = TimeUtils.localTime(lastSleepLog?.endTime ?: lastSleepLog?.startTime)
            )
        }

        provideContent { Content(content) }
    }

    @Composable
    private fun Content(content: WidgetContent) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Primary))
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = content.childName ?: "NongRak",
                style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = if (content.lastFeeding != null) "ให้นมล่าสุด ${content.lastFeeding}" else "ยังไม่มีข้อมูลการให้นม",
                style = TextStyle(color = ColorProvider(Color.White))
            )
            Text(
                text = when {
                    content.isSleeping -> "กำลังนอนอยู่"
                    content.lastSleep != null -> "นอนล่าสุด ${content.lastSleep}"
                    else -> "ยังไม่มีข้อมูลการนอน"
                },
                style = TextStyle(color = ColorProvider(Color.White))
            )
        }
    }
}
