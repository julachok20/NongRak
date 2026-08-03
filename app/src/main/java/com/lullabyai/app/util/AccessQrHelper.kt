package com.lullabyai.app.util

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

/** Offline QR encode/decode for Share Access + Sync Child (Stitch #5 C). */
object AccessQrHelper {

    fun encodeBitmap(payload: String, sizePx: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        return bmp
    }

    fun decodeFromArgb(pixels: IntArray, width: Int, height: Int): String? = try {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
        }
        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val text = MultiFormatReader().apply { setHints(hints) }.decode(bitmap).text?.trim()
        text?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun payloadForCode(code: String): String = "nongrak://access?code=${code.trim().uppercase()}"
}
