package com.touchdrop.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QrCodeUtils {

    /**
     * Apple iOS (iPhone Kamera) ve Android tarafından evrensel olarak tanınan
     * Wi-Fi QR kod formatı: WIFI:S:<SSID>;T:WPA;P:<PASSWORD>;;
     */
    fun generateWifiQrContent(ssid: String, pass: String): String =
        "WIFI:S:$ssid;T:WPA;P:$pass;;"

    fun generateQrMatrix(content: String, size: Int = 400): BitMatrix? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateWifiQrBitmap(ssid: String, pass: String, size: Int = 400): Bitmap? {
        return try {
            val content = generateWifiQrContent(ssid, pass)
            val bitMatrix = generateQrMatrix(content, size) ?: return null
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val offset = y * size
                for (x in 0 until size) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
