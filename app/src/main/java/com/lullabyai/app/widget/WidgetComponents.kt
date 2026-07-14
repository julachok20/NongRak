package com.lullabyai.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val WidgetRouteParamKey = ActionParameters.Key<String>(com.lullabyai.app.MainActivity.WIDGET_TARGET_ROUTE_EXTRA)

/** Opens the app directly to [route] (an `AppNavigation` route string) instead of the default
 *  Dashboard — each widget links to the actual screen its data comes from. */
fun openAppTo(route: String): Action =
    actionStartActivity<com.lullabyai.app.MainActivity>(actionParametersOf(WidgetRouteParamKey to route))

/** A deliberate type scale, applied consistently across all 7 widgets. Glance's default Text
 *  has no scale of its own — every size defaults the same — which is the single biggest
 *  "generic Android widget" tell. This is the family's typographic signature. */
object WidgetType {
    val stat = 22.sp    // the one hero number per card (ring %, weight/height, duration)
    val headline = 15.sp // card title
    val body = 13.sp     // primary supporting text
    val label = 11.sp    // chip / row labels
    val caption = 10.sp  // smallest annotations
}

/** Rounded card shell shared by every 25.Widget-style widget (rounding applies on Android 12+;
 *  older devices fall back to square corners — Glance's cornerRadius() has no pre-S path).
 *  [accentColor], when set, draws a 4dp top stripe in the category's color — this is the
 *  widget family's shared visual signature, tying 7 otherwise-different cards together. */
@Composable
fun WidgetCard(
    background: Color,
    accentColor: Color? = null,
    onClick: Action? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(background))
            .let { if (onClick != null) it.clickable(onClick) else it }
    ) {
        if (accentColor != null) {
            Box(modifier = GlanceModifier.fillMaxWidth().height(4.dp).background(ColorProvider(accentColor))) {}
        }
        Column(
            modifier = GlanceModifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            content = content
        )
    }
}

/** Small circular tinted icon badge — the consistent leading-icon treatment used across every
 *  widget, replacing bare emoji floating next to text. */
@Composable
fun IconBadge(icon: String, badgeColor: Color, iconColor: Color, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(ColorProvider(badgeColor)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, style = TextStyle(color = ColorProvider(iconColor), fontSize = WidgetType.body))
    }
}

/** A discrete 10-segment bar approximating the mockup's progress rings — Glance has no Canvas,
 *  and Row/Column weighting is equal-only (no arbitrary percentages), so this rounds to the
 *  nearest segment rather than drawing a smooth ring. */
@Composable
fun SegmentedProgressBar(percent: Int, fillColor: Color, trackColor: Color, segments: Int = 10) {
    val filled = (percent * segments / 100).coerceIn(0, segments)
    Row(modifier = GlanceModifier.fillMaxWidth().height(8.dp)) {
        repeat(segments) { i ->
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .cornerRadius(3.dp)
                    .background(ColorProvider(if (i < filled) fillColor else trackColor))
            ) {}
        }
    }
}

/** Small icon-glyph + label + value chip, mirroring the mockup's mini-stat chips. Glance can't
 *  host arbitrary vector icon sets without adding drawable resources, so an emoji glyph stands
 *  in for the icon. */
@Composable
fun StatChip(icon: String, label: String, value: String, chipColor: Color, onColor: Color) {
    Column(
        modifier = GlanceModifier
            .cornerRadius(16.dp)
            .background(ColorProvider(chipColor))
            .padding(10.dp)
    ) {
        Text(text = "$icon $value", style = TextStyle(color = ColorProvider(onColor), fontWeight = FontWeight.Bold, fontSize = WidgetType.body))
        Text(text = label, style = TextStyle(color = ColorProvider(onColor), fontSize = WidgetType.caption))
    }
}

@Composable
fun Divider(color: Color) {
    Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(color))) {}
}

/** Small rounded-pill category tag, matching the mockup's "การเติบโต" / "วัคซีน" / "วันนี้" labels. */
@Composable
fun LabelChip(text: String, chipColor: Color, onColor: Color) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(999.dp)
            .background(ColorProvider(chipColor))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = TextStyle(color = ColorProvider(onColor), fontWeight = FontWeight.Bold, fontSize = WidgetType.caption))
    }
}
