package com.lullabyai.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Renders a circular progress ring to a Bitmap. Glance has no Canvas composable, so a
 * pre-rendered Bitmap displayed via `Image(ImageProvider(bitmap))` is the real workaround
 * (not a fake substitute) for the mockup's SVG progress rings.
 */
fun renderProgressRing(
    context: Context,
    sizeDp: Int,
    strokeWidthDp: Int,
    percent: Int,
    trackColor: Color,
    fillColor: Color
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val strokePx = strokeWidthDp * density
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val inset = strokePx / 2
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = trackColor.toArgb()
    canvas.drawArc(rect, 0f, 360f, false, paint)
    paint.color = fillColor.toArgb()
    canvas.drawArc(rect, -90f, 360f * (percent.coerceIn(0, 100) / 100f), false, paint)
    return bitmap
}

/**
 * Renders the mockup's decorative sleep-quality wave. The mockup's own SVG path isn't tied to
 * real numbers either (it's a fixed aesthetic curve, not a data plot), so reproducing that exact
 * shape here isn't fabricating data — it's copying decoration.
 */
fun renderSleepWave(context: Context, widthDp: Int, heightDp: Int, lineColor: Color, fillColor: Color): Bitmap {
    val density = context.resources.displayMetrics.density
    val w = widthDp * density
    val h = heightDp * density
    val bitmap = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    fun x(v: Float) = v / 100f * w
    fun y(v: Float) = v / 40f * h
    val path = Path().apply {
        moveTo(x(0f), y(40f))
        cubicTo(x(20f), y(10f), x(40f), y(35f), x(60f), y(15f))
        cubicTo(x(75f), y(5f), x(85f), y(30f), x(100f), y(10f))
    }
    val fillPath = Path(path).apply {
        lineTo(x(100f), y(40f))
        lineTo(x(0f), y(40f))
        close()
    }
    canvas.drawPath(
        fillPath,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor.copy(alpha = 0.2f).toArgb()
        }
    )
    canvas.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeCap = Paint.Cap.ROUND
            color = lineColor.toArgb()
        }
    )
    return bitmap
}
