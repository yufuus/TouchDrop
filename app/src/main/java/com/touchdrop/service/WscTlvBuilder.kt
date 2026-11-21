package com.touchdrop.service

import java.nio.charset.StandardCharsets

/**
 * Wi-Fi Alliance WPS / WSC (Wi-Fi Simple Configuration) TLV ve NDEF oluşturucu.
 * Standart şartnameye göre 0x100E (Credential) bloğu içinde:
 * - 0x1003: Auth Type (WPA2-Personal: 0x0020)
 * - 0x100F: Enc Type (AES: 0x0008)
 * - 0x1026: Network Index (0x01)
 * - 0x1027: Network Key / Password
 * - 0x1045: Network Name / SSID
 */
object WscTlvBuilder {

    fun buildWscPayload(ssid: String, password: String): ByteArray {
        val ssidBytes = ssid.toByteArray(StandardCharsets.UTF_8)
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)

        val authTlv = byteArrayOf(0x10, 0x03, 0x00, 0x02, 0x00, 0x20)
        val encTlv = byteArrayOf(0x10, 0x0F, 0x00, 0x02, 0x00, 0x08)
        val netIdxTlv = byteArrayOf(0x10, 0x26, 0x00, 0x01, 0x01)

        val passLen = passBytes.size
        val passTlv = ByteArray(4 + passLen)
        passTlv[0] = 0x10
        passTlv[1] = 0x27
        passTlv[2] = ((passLen shr 8) and 0xFF).toByte()
        passTlv[3] = (passLen and 0xFF).toByte()
        System.arraycopy(passBytes, 0, passTlv, 4, passLen)

        val ssidLen = ssidBytes.size
        val ssidTlv = ByteArray(4 + ssidLen)
        ssidTlv[0] = 0x10
        ssidTlv[1] = 0x45
        ssidTlv[2] = ((ssidLen shr 8) and 0xFF).toByte()
        ssidTlv[3] = (ssidLen and 0xFF).toByte()
        System.arraycopy(ssidBytes, 0, ssidTlv, 4, ssidLen)

        val credValueLen = authTlv.size + encTlv.size + netIdxTlv.size + passTlv.size + ssidTlv.size
        val credTlv = ByteArray(4 + credValueLen)
        credTlv[0] = 0x10
        credTlv[1] = 0x0E
        credTlv[2] = ((credValueLen shr 8) and 0xFF).toByte()
        credTlv[3] = (credValueLen and 0xFF).toByte()

        var offset = 4
        System.arraycopy(authTlv, 0, credTlv, offset, authTlv.size); offset += authTlv.size
        System.arraycopy(encTlv, 0, credTlv, offset, encTlv.size); offset += encTlv.size
        System.arraycopy(netIdxTlv, 0, credTlv, offset, netIdxTlv.size); offset += netIdxTlv.size
        System.arraycopy(passTlv, 0, credTlv, offset, passTlv.size); offset += passTlv.size
        System.arraycopy(ssidTlv, 0, credTlv, offset, ssidTlv.size)

        return credTlv
    }

    fun buildWscRecord(ssid: String, password: String, isSingle: Boolean = true): ByteArray {
        val payload = buildWscPayload(ssid, password)
        val mimeBytes = "application/vnd.wfa.wsc".toByteArray(StandardCharsets.US_ASCII)

        val header = if (isSingle) 0xD2.toByte() else 0x92.toByte()
        val record = ByteArray(3 + mimeBytes.size + payload.size)
        record[0] = header
        record[1] = mimeBytes.size.toByte()
        record[2] = payload.size.toByte()
        System.arraycopy(mimeBytes, 0, record, 3, mimeBytes.size)
        System.arraycopy(payload, 0, record, 3 + mimeBytes.size, payload.size)
        return record
    }

    fun buildUriRecord(url: String, isFirst: Boolean = true, isLast: Boolean = true): ByteArray {
        var prefixCode: Byte = 0x00
        var cleanUrl = url

        if (url.startsWith("http://www.")) {
            prefixCode = 0x01
            cleanUrl = url.substring("http://www.".length)
        } else if (url.startsWith("https://www.")) {
            prefixCode = 0x02
            cleanUrl = url.substring("https://".length)
        } else if (url.startsWith("http://")) {
            prefixCode = 0x03
            cleanUrl = url.substring("http://".length)
        } else if (url.startsWith("https://")) {
            prefixCode = 0x04
            cleanUrl = url.substring("https://".length)
        }

        val urlBytes = cleanUrl.toByteArray(StandardCharsets.UTF_8)
        val payloadLength = 1 + urlBytes.size

        val mb = if (isFirst) 0x80 else 0x00
        val me = if (isLast) 0x40 else 0x00
        val header = (mb or me or 0x10 or 0x01).toByte()

        val record = ByteArray(4 + payloadLength)
        record[0] = header
        record[1] = 0x01.toByte()
        record[2] = payloadLength.toByte()
        record[3] = 0x55.toByte()
        record[4] = prefixCode
        System.arraycopy(urlBytes, 0, record, 5, urlBytes.size)
        return record
    }
}
