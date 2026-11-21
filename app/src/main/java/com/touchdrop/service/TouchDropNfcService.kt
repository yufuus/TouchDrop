package com.touchdrop.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * NFC Forum Type 4 NDEF Tag Emülasyonu (HCE).
 * Tam otomatik çift aşamalı (Auto-Pilot) akış:
 * - Ev/Ofis Wi-Fi bağlıysa: Tek dokunuşla doğrudan indirme sayfasını açar.
 * - Hızlı Köprü (Hotspot) modundaysa:
 *   1. Dokunuş: QR okutmaya gerek kalmadan doğrudan Wi-Fi'ye bağlar (WSC).
 *   2. Dokunuş: Otomatik olarak indirme linkine geçer ve web sayfasını açar.
 */
class TouchDropNfcService : HostApduService() {

    companion object {
        @Volatile
        var currentShareUrl: String = "http://127.0.0.1:8080"
        @Volatile
        var hotspotSsid: String? = null
        @Volatile
        var hotspotPass: String? = null
        @Volatile
        var isHotspotActive: Boolean = false

        // Otomatik Akış Aşaması:
        // 1: Wi-Fi bağlantı isteği fırlatır (WSC)
        // 2: Web indirme sayfası linki fırlatır (URI)
        @Volatile
        var autoStep: Int = 1
        @Volatile
        var onStepChanged: ((Int) -> Unit)? = null

        // Durum kodları (ISO 7816-4 Status Words)
        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val STATUS_WRONG_PARAMS = byteArrayOf(0x6A.toByte(), 0x86.toByte())
        val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        // NFC Forum Type 4 Tag v2.0 AID: D2 76 00 00 85 01 01
        val AID_NDEF_V2 = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )
        // NFC Forum Type 4 Tag v1.0 AID: D2 76 00 00 85 01 00
        val AID_NDEF_V1 = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x00.toByte()
        )

        // Capability Container (CC) File ID: E1 03
        val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03.toByte())

        // NDEF File ID: E1 04
        val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04.toByte())

        // CC Dosyası İçeriği (15 Bayt) - NFC Forum Type 4 Tag v2.0 Spesifikasyonu
        val CC_FILE = byteArrayOf(
            0x00, 0x0F,                  // CCLEN: 15 bayt
            0x20,                        // Mapping Versiyon: 2.0
            0x00, 0x7F,                  // MLe: Max R-APDU boyutu = 127 bayt
            0x00, 0x7F,                  // MLc: Max C-APDU boyutu = 127 bayt
            0x04, 0x06,                  // T: NDEF File Control TLV (0x04), L: 6 bayt
            0xE1.toByte(), 0x04.toByte(), // File ID: E1 04
            0x04, 0x00,                  // Max NDEF boyutu: 1024 bayt
            0x00,                        // Okuma izni: Kısıtlamasız (0x00)
            0xFF.toByte()                // Yazma izni: Salt okunur (0xFF)
        )

        fun buildWscPayload(ssid: String, password: String): ByteArray =
            WscTlvBuilder.buildWscPayload(ssid, password)

        fun buildWscRecord(ssid: String, password: String, isSingle: Boolean = true): ByteArray =
            WscTlvBuilder.buildWscRecord(ssid, password, isSingle)

        fun buildNdefMessage(url: String): ByteArray =
            WscTlvBuilder.buildUriRecord(url, isFirst = true, isLast = true)

        fun buildUriRecord(url: String, isFirst: Boolean = true, isLast: Boolean = true): ByteArray =
            WscTlvBuilder.buildUriRecord(url, isFirst, isLast)

        fun buildActiveNdefMessage(): ByteArray {
            if (!isHotspotActive || hotspotSsid.isNullOrEmpty() || hotspotPass == null) {
                return buildNdefMessage(currentShareUrl)
            }
            return if (autoStep == 1) {
                buildWscRecord(hotspotSsid!!, hotspotPass!!, isSingle = true)
            } else {
                buildNdefMessage(currentShareUrl)
            }
        }

        fun buildNdefFile(): ByteArray {
            val message = buildActiveNdefMessage()
            val file = ByteArray(2 + message.size)
            file[0] = ((message.size shr 8) and 0xFF).toByte()
            file[1] = (message.size and 0xFF).toByte()
            System.arraycopy(message, 0, file, 2, message.size)
            return file
        }
    }

    private var isCcSelected = false
    private var isNdefSelected = false
    private var cachedNdefFile: ByteArray? = null
    private var stepAdvancedInThisSession = false

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val resp = handleApdu(commandApdu)
        android.util.Log.d("TouchDropNFC", "IN: ${commandApdu?.joinToString("") { "%02X".format(it) }} -> OUT: ${resp.joinToString("") { "%02X".format(it) }}")
        return resp
    }

    fun handleApdu(commandApdu: ByteArray?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED
        }

        val ins = commandApdu[1]
        val p1 = commandApdu[2]

        // 1. SELECT Komutu (INS = 0xA4)
        if (ins == 0xA4.toByte()) {
            if (p1 == 0x04.toByte()) {
                if (commandApdu.size >= 5) {
                    val lc = commandApdu[4].toInt() and 0xFF
                    if (commandApdu.size >= 5 + lc) {
                        val aid = commandApdu.copyOfRange(5, 5 + lc)
                        if (aid.contentEquals(AID_NDEF_V2) || aid.contentEquals(AID_NDEF_V1)) {
                            isCcSelected = false
                            isNdefSelected = false
                            cachedNdefFile = null
                            stepAdvancedInThisSession = false
                            return STATUS_SUCCESS
                        }
                    }
                }
                return STATUS_FILE_NOT_FOUND
            } else if (p1 == 0x00.toByte() || p1 == 0x02.toByte()) {
                if (commandApdu.size >= 7) {
                    val fileId = commandApdu.copyOfRange(5, 7)
                    if (fileId.contentEquals(CC_FILE_ID)) {
                        isCcSelected = true
                        isNdefSelected = false
                        return STATUS_SUCCESS
                    } else if (fileId.contentEquals(NDEF_FILE_ID)) {
                        isCcSelected = false
                        isNdefSelected = true
                        cachedNdefFile = buildNdefFile()
                        return STATUS_SUCCESS
                    }
                }
                return STATUS_FILE_NOT_FOUND
            }
        }

        // 2. READ BINARY Komutu (INS = 0xB0)
        if (ins == 0xB0.toByte()) {
            val offset = ((p1.toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
            val le = if (commandApdu.size >= 5) commandApdu[4].toInt() and 0xFF else 0
            val expectedLen = if (le == 0) 256 else le

            if (isCcSelected) {
                if (offset >= CC_FILE.size) {
                    return STATUS_WRONG_PARAMS
                }
                val available = CC_FILE.size - offset
                val readLen = minOf(expectedLen, available)
                val response = ByteArray(readLen + 2)
                System.arraycopy(CC_FILE, offset, response, 0, readLen)
                response[readLen] = 0x90.toByte()
                response[readLen + 1] = 0x00.toByte()
                return response
            } else if (isNdefSelected) {
                val ndefFile = cachedNdefFile ?: buildNdefFile().also { cachedNdefFile = it }
                if (offset >= ndefFile.size) {
                    return STATUS_WRONG_PARAMS
                }
                val available = ndefFile.size - offset
                val readLen = minOf(expectedLen, available)
                val response = ByteArray(readLen + 2)
                System.arraycopy(ndefFile, offset, response, 0, readLen)
                response[readLen] = 0x90.toByte()
                response[readLen + 1] = 0x00.toByte()

                // İlk dokunuşta Wi-Fi verisi okunduktan sonra otomatik olarak Aşama 2'ye (Link) geç
                if (isHotspotActive && autoStep == 1 && !stepAdvancedInThisSession) {
                    stepAdvancedInThisSession = true
                    autoStep = 2
                    onStepChanged?.invoke(2)
                } else if (isHotspotActive && autoStep == 2 && !stepAdvancedInThisSession) {
                    stepAdvancedInThisSession = true
                    onStepChanged?.invoke(3)
                }

                return response
            }
            return STATUS_FILE_NOT_FOUND
        }

        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        isCcSelected = false
        isNdefSelected = false
        cachedNdefFile = null
        stepAdvancedInThisSession = false
    }

    fun resetState() {
        isCcSelected = false
        isNdefSelected = false
        cachedNdefFile = null
        stepAdvancedInThisSession = false
    }
}
