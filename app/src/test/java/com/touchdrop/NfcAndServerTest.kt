package com.touchdrop

import com.touchdrop.service.TouchDropNfcService
import com.touchdrop.util.FileUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class NfcAndServerTest {

    @Test
    fun testNfcApduProtocolFullFlow() {
        val service = TouchDropNfcService()
        TouchDropNfcService.currentShareUrl = "http://192.168.1.100:8080"
        TouchDropNfcService.isHotspotActive = false

        // 1. SELECT AID v2.0 (00 A4 04 00 07 D2 76 00 00 85 01 01 00)
        val selectAidV2 = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00
        )
        var resp = service.handleApdu(selectAidV2)
        assertArrayEquals(TouchDropNfcService.STATUS_SUCCESS, resp)

        // 2. SELECT CC File (00 A4 00 0C 02 E1 03)
        val selectCc = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
        resp = service.handleApdu(selectCc)
        assertArrayEquals(TouchDropNfcService.STATUS_SUCCESS, resp)

        // 3. READ CC File (00 B0 00 00 0F) - Tüm 15 baytı oku
        val readCc = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F)
        resp = service.handleApdu(readCc)
        assertEquals(15 + 2, resp.size)
        assertEquals(0x90.toByte(), resp[15])
        assertEquals(0x00.toByte(), resp[16])
        assertEquals(0x00.toByte(), resp[0]) // CCLEN MSB
        assertEquals(0x0F.toByte(), resp[1]) // CCLEN LSB (15 bayt)
        assertEquals(0x20.toByte(), resp[2]) // Versiyon 2.0

        // 4. READ CC File with Le = 0x00 (256 bayt isteği)
        val readCcZero = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x00)
        resp = service.handleApdu(readCcZero)
        assertEquals(15 + 2, resp.size)

        // 5. SELECT NDEF File (00 A4 00 0C 02 E1 04)
        val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
        resp = service.handleApdu(selectNdef)
        assertArrayEquals(TouchDropNfcService.STATUS_SUCCESS, resp)

        // 6. READ NLEN (İlk 2 bayt) (00 B0 00 00 02)
        val readNlen = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02)
        resp = service.handleApdu(readNlen)
        assertEquals(4, resp.size) // 2 bayt NLEN + 90 00
        val nlen = ((resp[0].toInt() and 0xFF) shl 8) or (resp[1].toInt() and 0xFF)
        assertTrue(nlen > 0)

        // 7. READ NDEF Message (Offset 2, Length = nlen)
        val readMsg = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, nlen.toByte())
        resp = service.handleApdu(readMsg)
        assertEquals(nlen + 2, resp.size)
        assertEquals(0xD1.toByte(), resp[0]) // NDEF Header
        assertEquals(0x55.toByte(), resp[3]) // 'U' (URI)
        assertEquals(0x03.toByte(), resp[4]) // "http://"
        val decodedUrl = "http://" + String(resp.copyOfRange(5, resp.size - 2), StandardCharsets.UTF_8)
        assertEquals("http://192.168.1.100:8080", decodedUrl)

        // 8. Test AID v1.0 fallback
        val selectAidV1 = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x00, 0x00
        )
        resp = service.handleApdu(selectAidV1)
        assertArrayEquals(TouchDropNfcService.STATUS_SUCCESS, resp)

        // 9. Test Select with P1 = 0x02
        val selectCcP1_02 = byteArrayOf(0x00, 0xA4.toByte(), 0x02, 0x0C, 0x02, 0xE1.toByte(), 0x03)
        resp = service.handleApdu(selectCcP1_02)
        assertArrayEquals(TouchDropNfcService.STATUS_SUCCESS, resp)

        // 10. Test Invalid File ID
        val selectUnknown = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0x3F.toByte(), 0x00)
        resp = service.handleApdu(selectUnknown)
        assertArrayEquals(TouchDropNfcService.STATUS_FILE_NOT_FOUND, resp)
    }

    @Test
    fun testNdefUriRecordGeneration() {
        val url = "http://192.168.1.50:8080"
        val record = TouchDropNfcService.buildNdefMessage(url)

        assertEquals(0xD1.toByte(), record[0])
        assertEquals(0x01.toByte(), record[1])
        assertEquals(0x55.toByte(), record[3])
        assertEquals(0x03.toByte(), record[4])
        assertEquals("192.168.1.50:8080", String(record.copyOfRange(5, record.size), StandardCharsets.UTF_8))
    }

    @Test
    fun testFileSizeFormatting() {
        fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val formatted = String.format("%.1f", size / Math.pow(1024.0, digitGroups.toDouble()))
            return "$formatted ${units[digitGroups]}"
        }

        assertTrue(formatFileSize(1024).contains("KB"))
        assertTrue(formatFileSize(1024 * 1024 * 15).contains("MB"))
        assertTrue(formatFileSize(1024L * 1024 * 1024 * 2).contains("GB"))
    }

    @Test
    fun testWifiQrCodeFormat() {
        val ssid = "TouchDrop-Direct-45"
        val pass = "secret1234"
        val wifiPayload = "WIFI:T:WPA;S:$ssid;P:$pass;;"
        assertTrue(wifiPayload.startsWith("WIFI:T:WPA;"))
        assertTrue(wifiPayload.contains("S:TouchDrop-Direct-45;"))
        assertTrue(wifiPayload.contains("P:secret1234;;"))
    }

    @Test
    fun testUploadedFileNameSanitization() {
        assertEquals("photo.jpg", FileUtils.sanitizeFileName("photo.jpg"))
        assertEquals("vacation.png", FileUtils.sanitizeFileName("../../etc/vacation.png"))
        assertEquals("document.pdf", FileUtils.sanitizeFileName("C:\\Users\\Admin\\document.pdf"))
        assertEquals("payload.sh", FileUtils.sanitizeFileName("..\\..\\payload.sh"))
        assertEquals("hidden", FileUtils.sanitizeFileName(".hidden"))
        assertEquals("secret_.txt", FileUtils.sanitizeFileName("secret\u0000.txt"))
        assertEquals("injected__file.png", FileUtils.sanitizeFileName("injected\r\nfile.png"))
        assertEquals("evil_file.txt", FileUtils.sanitizeFileName("evil..file.txt"))
        assertFalse(FileUtils.sanitizeFileName("../../../evil.exe").contains("/"))
        assertFalse(FileUtils.sanitizeFileName("../../../evil.exe").contains("\\"))
        assertTrue(FileUtils.sanitizeFileName("").startsWith("touchdrop_"))
        assertTrue(FileUtils.sanitizeFileName(null).startsWith("touchdrop_"))
    }

    @Test
    fun testWscPayloadAndRecordGeneration() {
        val ssid = "TouchDrop-Direct"
        val pass = "mypassword123"

        // WSC Payload
        val payload = TouchDropNfcService.buildWscPayload(ssid, pass)
        assertEquals(0x10.toByte(), payload[0])
        assertEquals(0x0E.toByte(), payload[1]) // Credential Block Tag 0x100E

        // WSC Single Record
        val record = TouchDropNfcService.buildWscRecord(ssid, pass, isSingle = true)
        assertEquals(0xD2.toByte(), record[0]) // MB=1, ME=1, SR=1, TNF=0x02
        assertEquals(23.toByte(), record[1])   // "application/vnd.wfa.wsc".length
        val mime = String(record.copyOfRange(3, 3 + 23), StandardCharsets.US_ASCII)
        assertEquals("application/vnd.wfa.wsc", mime)
    }

    @Test
    fun testAutomatedTwoStepFlow() {
        val service = TouchDropNfcService()
        TouchDropNfcService.currentShareUrl = "http://192.168.43.1:8080"
        TouchDropNfcService.hotspotSsid = "TouchDrop-AutoHotspot"
        TouchDropNfcService.hotspotPass = "autopass88"
        TouchDropNfcService.isHotspotActive = true
        TouchDropNfcService.autoStep = 1

        var stepReported: Int? = null
        TouchDropNfcService.onStepChanged = { step ->
            stepReported = step
        }

        // --- 1. Dokunuş: Karşı telefon Wi-Fi (WSC) bilgisini okuyor ---
        val selectAid = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00)
        service.handleApdu(selectAid)

        val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
        service.handleApdu(selectNdef)

        // Read NLEN & Payload
        val readWsc = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x7F)
        val wscResp = service.handleApdu(readWsc)

        // NLEN'den sonra MIME kaydı 0xD2 (WSC) olmalı
        assertEquals(0xD2.toByte(), wscResp[2])
        val mime = String(wscResp.copyOfRange(5, 5 + 23), StandardCharsets.US_ASCII)
        assertEquals("application/vnd.wfa.wsc", mime)

        // Otomatik olarak Aşama 2'ye geçmeli
        assertEquals(2, TouchDropNfcService.autoStep)
        assertEquals(2, stepReported)

        // Dokunuş bitti
        service.onDeactivated(0)

        // --- 2. Dokunuş: Karşı telefon şimdi indirme linkini (URI) okuyor ---
        service.handleApdu(selectAid)
        service.handleApdu(selectNdef)

        val readUri = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x7F)
        val uriResp = service.handleApdu(readUri)

        // NLEN'den sonra URI kaydı 0xD1 olmalı
        assertEquals(0xD1.toByte(), uriResp[2])
        assertEquals(0x55.toByte(), uriResp[5]) // 'U'
        assertEquals(3, stepReported) // Tamamlandı bildirimi
    }

    @Test
    fun testModularFileUtils() {
        assertEquals("photo.png", com.touchdrop.util.FileUtils.sanitizeFileName("photo.png"))
        assertEquals("doc.pdf", com.touchdrop.util.FileUtils.sanitizeFileName("../../../doc.pdf"))
        assertTrue(com.touchdrop.util.FileUtils.formatFileSize(1024 * 1024 * 5).contains("MB"))
    }

    @Test
    fun testWscTlvBuilderDirectly() {
        val payload = com.touchdrop.service.WscTlvBuilder.buildWscPayload("MyHotspot", "SecretPass123")
        assertTrue(payload.isNotEmpty())
        assertEquals(0x10.toByte(), payload[0])
    }
}
