package com.touchdrop.model

import java.io.File

/**
 * Paylaşılan dosyanın meta bilgilerini tutan veri sınıfı.
 */
data class SharedFileInfo(
    val file: File,
    val name: String = file.name,
    val size: Long = file.length(),
    val mimeType: String? = null
)
