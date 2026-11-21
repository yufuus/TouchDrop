package com.touchdrop.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.log10
import kotlin.math.pow

/**
 * Dosya boyutu formatlama ve güvenli dosya adı sanitizasyon araçları.
 */
object FileUtils {

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        val formatted = String.format("%.1f", size / 1024.0.pow(digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return sanitizeFileName(result)
    }

    /**
     * Güvenli Dosya Adı Sanitizasyonu (Path Traversal, Injection ve Gizli Dosya Koruması).
     * - Dizin ayrıştırıcıları (/ ve \) temizler.
     * - Null bayt ve tehlikeli kontrol karakterlerini kaldırır.
     * - Başındaki noktaları temizleyerek gizli dosya oluşumunu engeller.
     * - İki veya daha fazla ardışık noktayı (..) engeller.
     */
    fun sanitizeFileName(submittedName: String?): String {
        if (submittedName.isNullOrBlank()) {
            return "touchdrop_${System.currentTimeMillis()}"
        }
        var clean = submittedName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1F\\x7F/\\\\?%*:|\"<>]"), "_")
            .trim()
            .trimStart('.')

        clean = clean.replace(Regex("\\.{2,}"), "_")

        return if (clean.isNotBlank()) clean else "touchdrop_${System.currentTimeMillis()}"
    }
}
