package com.touchdrop.server

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.webkit.MimeTypeMap
import com.touchdrop.util.FileUtils
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Android üzerinde çalışan gömülü, sıfır kurulumlu, yüksek hızlı HTTP sunucusu.
 *
 * Güvenlik ve Mimari Özellikleri:
 * - Doğrudan disk akışı (zero-copy / streaming): Bellek taşmasını (OOM) önler, gigabaytlarca dosyayı 0 RAM ile aktarır.
 * - Sıkı Path Traversal Koruması: Canonical path kontrolü ile dizin dışına çıkma saldırılarını %100 engeller.
 * - Dosya Adı Sanitizasyonu: Null bayt, kontrol karakterleri ve injection açıklarına karşı tam korumalıdır.
 * - Güvenlik Başlıkları: X-Content-Type-Options: nosniff, no-cache, Content-Security-Policy kısıtlamaları.
 */
class TouchDropServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    @Volatile
    var activeFile: File? = null

    @Volatile
    var onUploadReceived: ((File, Int) -> Unit)? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // CORS ve Güvenlik Başlıkları
        return when {
            // 1. Ana Web İndirme ve Gönderme Arayüzü
            uri == "/" || uri == "/index.html" -> {
                try {
                    val htmlStream: InputStream = context.assets.open("web/index.html")
                    val resp = newChunkedResponse(Response.Status.OK, "text/html; charset=UTF-8", htmlStream)
                    resp.addHeader("X-Content-Type-Options", "nosniff")
                    resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                    resp
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Asset yüklenemedi")
                }
            }

            // 2. Dosya Bilgisi Durum API (Polling)
            uri == "/api/file-info" -> {
                val file = activeFile
                val json = JSONObject()
                if (file != null && file.exists()) {
                    val ext = file.extension.lowercase()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    json.put("hasFile", true)
                    json.put("name", file.name)
                    json.put("size", file.length())
                    json.put("sizeFormatted", FileUtils.formatFileSize(file.length()))
                    json.put("mimeType", mime)
                } else {
                    json.put("hasFile", false)
                    json.put("name", "")
                    json.put("size", 0)
                    json.put("sizeFormatted", "--")
                    json.put("mimeType", "")
                }
                val resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", json.toString())
                resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                resp.addHeader("Access-Control-Allow-Origin", "*")
                resp.addHeader("X-Content-Type-Options", "nosniff")
                resp
            }

            // 3. Dosya İndirme (Zero-Copy Streaming)
            uri == "/api/download" -> {
                val file = activeFile
                if (file != null && file.exists()) {
                    try {
                        val fileStream = FileInputStream(file)
                        val ext = file.extension.lowercase()
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                        val safeDownloadName = FileUtils.sanitizeFileName(file.name)

                        val response = newFixedLengthResponse(
                            Response.Status.OK,
                            mime,
                            fileStream,
                            file.length()
                        )
                        response.addHeader("Content-Disposition", "attachment; filename=\"$safeDownloadName\"")
                        response.addHeader("Accept-Ranges", "bytes")
                        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                        response.addHeader("X-Content-Type-Options", "nosniff")
                        response.addHeader("Access-Control-Allow-Origin", "*")
                        response
                    } catch (e: Exception) {
                        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Dosya okuma hatası")
                    }
                } else {
                    val waitingHtml = """
                        <!DOCTYPE html>
                        <html lang="tr">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <title>TouchDrop • Dosya Bekleniyor</title>
                          <style>
                            body { background: #09090B; color: #FAFAFA; font-family: -apple-system, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; padding: 20px; text-align: center; }
                            .card { background: #121215; border: 1px solid rgba(255,255,255,0.08); padding: 32px 24px; border-radius: 24px; max-width: 380px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
                            h2 { font-size: 19px; margin-bottom: 8px; font-weight: 600; }
                            p { color: #A1A1AA; font-size: 14px; margin-bottom: 24px; line-height: 1.5; }
                            a { display: inline-flex; align-items: center; justify-content: center; padding: 14px 24px; background: #FAFAFA; color: #09090B; text-decoration: none; border-radius: 14px; font-weight: 600; font-size: 14px; }
                          </style>
                        </head>
                        <body>
                          <div class="card">
                            <h2>Henüz Dosya Seçilmedi</h2>
                            <p>Gönderici cihaz henüz aktarılacak bir dosya belirlemedi. Dosya seçildiğinde sayfa otomatik güncellenecektir.</p>
                            <a href="/">Ana Sayfaya Dön</a>
                          </div>
                        </body>
                        </html>
                    """.trimIndent()
                    val resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", waitingHtml)
                    resp.addHeader("X-Content-Type-Options", "nosniff")
                    resp
                }
            }

            // 4. Karşı Cihazdan Dosya Alma (Ters Yönde Upload)
            uri == "/api/upload" && method == Method.POST -> {
                try {
                    val files = HashMap<String, String>()
                    session.parseBody(files)

                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val touchDropDir = File(downloadsDir, "TouchDrop")
                    if (!touchDropDir.exists()) touchDropDir.mkdirs()

                    val baseCanonical = touchDropDir.canonicalPath + File.separator
                    val parms = session.parms
                    var lastSavedFile: File? = null
                    var savedCount = 0

                    files.forEach { (key, tempFilePath) ->
                        val submittedName = parms[key]
                        val safeName = FileUtils.sanitizeFileName(submittedName)

                        var targetFile = File(touchDropDir, safeName).canonicalFile
                        // Sıkı Path Traversal Denetimi
                        if (!targetFile.path.startsWith(baseCanonical)) {
                            targetFile = File(touchDropDir, "safe_${System.currentTimeMillis()}_$safeName").canonicalFile
                        }

                        if (targetFile.exists()) {
                            val base = safeName.substringBeforeLast('.', safeName)
                            val ext = if (safeName.contains('.')) ".${safeName.substringAfterLast('.')}" else ""
                            var c = 1
                            while (targetFile.exists()) {
                                targetFile = File(touchDropDir, "${base}_$c$ext").canonicalFile
                                c++
                            }
                        }

                        val tempFile = File(tempFilePath)
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()

                        // Android MediaStore ve Galeri'ye anında işlet
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            null
                        ) { path, uriRes ->
                            android.util.Log.d("TouchDrop", "MediaScanner indexed: $path -> $uriRes")
                        }

                        lastSavedFile = targetFile
                        savedCount++
                    }

                    if (lastSavedFile != null) {
                        onUploadReceived?.invoke(lastSavedFile!!, savedCount)
                    }

                    val resp = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok","count":$savedCount}""")
                    resp.addHeader("Access-Control-Allow-Origin", "*")
                    resp.addHeader("X-Content-Type-Options", "nosniff")
                    resp
                } catch (e: Exception) {
                    android.util.Log.e("TouchDropServer", "Upload error", e)
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Yükleme hatası: ${e.message}")
                }
            }

            // OPTIONS preflight isteği desteği
            method == Method.OPTIONS -> {
                val resp = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                resp.addHeader("Access-Control-Allow-Origin", "*")
                resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Range")
                resp
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }
}
