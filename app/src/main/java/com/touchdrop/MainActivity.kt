package com.touchdrop

import android.Manifest
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.touchdrop.databinding.ActivityMainBinding
import com.touchdrop.manager.HotspotManager
import com.touchdrop.server.TouchDropServer
import com.touchdrop.service.TouchDropNfcService
import com.touchdrop.ui.HelpBottomSheet
import com.touchdrop.ui.SettingsBottomSheet
import com.touchdrop.util.FileUtils
import com.touchdrop.util.HapticUtils
import com.touchdrop.util.NetworkUtils
import com.touchdrop.util.QrCodeUtils
import com.touchdrop.util.ThemeManager
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var server: TouchDropServer? = null
    private var selectedFile: File? = null
    private var lastReceivedFile: File? = null
    private var activeShareUrl: String = "http://127.0.0.1:8080"

    // Hızlı Köprü (Local-Only Hotspot) Yöneticisi
    private lateinit var hotspotManager: HotspotManager

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedUri(it) }
    }

    private val hotspotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            startHotspotBridge()
        } else {
            Toast.makeText(this, "Köprü oluşturmak için Wi-Fi izinleri gereklidir.", Toast.LENGTH_SHORT).show()
            updateModeUi(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        hotspotManager = HotspotManager(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInitialState()
        setupNfcCallback()
        setupServer()
        setupListeners()
        playEntranceAnimation()
        startNfcPulseAnimation()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun setupInitialState() {
        binding.tvStatus.text = getString(R.string.status_prompt_select_file)
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.tvStatusSub.text = getString(R.string.status_sub_select_file)
        binding.layoutSelectedFile.visibility = View.GONE
        binding.btnOpenReceived.visibility = View.GONE
        binding.btnTestInBrowser.visibility = View.GONE
        binding.ivQrCode.visibility = View.GONE
        binding.layoutIconContainer.visibility = View.VISIBLE

        updateModeUi(false)
    }

    /**
     * 2'li Mod Seçici Arayüz Güncellemesi (Aynı Wi-Fi vs Hızlı Köprü)
     */
    private fun updateModeUi(isHotspot: Boolean) {
        binding.ivQrCode.visibility = View.GONE
        binding.layoutIconContainer.visibility = View.VISIBLE
        if (!isHotspot) {
            // Mod 1: Aynı Wi-Fi Aktif
            binding.btnModeWifi.setBackgroundResource(R.drawable.bg_mode_active)
            binding.ivModeWifi.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
            binding.tvModeWifi.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

            binding.btnModeHotspot.setBackgroundColor(Color.TRANSPARENT)
            binding.ivModeHotspot.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
            binding.tvModeHotspot.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

            binding.tvModeHint.text = getString(R.string.hint_mode_wifi)
        } else {
            // Mod 2: Hızlı Köprü Aktif
            binding.btnModeWifi.setBackgroundColor(Color.TRANSPARENT)
            binding.ivModeWifi.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
            binding.tvModeWifi.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

            binding.btnModeHotspot.setBackgroundResource(R.drawable.bg_mode_active_emerald)
            binding.ivModeHotspot.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
            binding.tvModeHotspot.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))

            binding.tvModeHint.text = getString(R.string.hint_mode_hotspot)
        }

        if (selectedFile != null) {
            binding.tvStatus.text = getString(R.string.status_prompt_tap_phone)
            binding.tvStatus.setTextColor(Color.parseColor("#10B981"))
            binding.tvStatusSub.text = getString(R.string.status_sub_tap_phone)
        }
    }

    /**
     * Açılış Giriş Animasyonu (Bespoke Staged Entrance Animation)
     */
    private fun playEntranceAnimation() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.translationY = -24f

        binding.layoutModeSelector.alpha = 0f
        binding.layoutModeSelector.translationY = -16f

        binding.tvModeHint.alpha = 0f

        binding.cardStatus.alpha = 0f
        binding.cardStatus.scaleX = 0.94f
        binding.cardStatus.scaleY = 0.94f

        binding.btnSelectFile.alpha = 0f
        binding.btnSelectFile.translationY = 32f

        val interpolator = DecelerateInterpolator(1.8f)

        binding.layoutHeader.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(interpolator)
            .start()

        binding.layoutModeSelector.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(440)
            .setStartDelay(80)
            .setInterpolator(interpolator)
            .start()

        binding.tvModeHint.animate()
            .alpha(1f)
            .setDuration(440)
            .setStartDelay(120)
            .start()

        binding.cardStatus.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(480)
            .setStartDelay(160)
            .setInterpolator(interpolator)
            .start()

        binding.btnSelectFile.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(480)
            .setStartDelay(220)
            .setInterpolator(interpolator)
            .start()
    }

    private fun startNfcPulseAnimation() {
        binding.ivNfc.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(1200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.ivNfc.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(1200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        startNfcPulseAnimation()
                    }
                    .start()
            }
            .start()
    }

    private fun setupNfcCallback() {
        TouchDropNfcService.onStepChanged = { step ->
            runOnUiThread {
                handleNfcStepChanged(step)
            }
        }
    }

    private fun handleNfcStepChanged(step: Int) {
        when (step) {
            2 -> {
                HapticUtils.vibrate(this, 120)
                binding.tvStatus.text = getString(R.string.status_wifi_sent)
                binding.tvStatus.setTextColor(Color.parseColor("#F59E0B"))
                binding.tvStatusSub.text = getString(R.string.status_sub_wifi_sent)
                Toast.makeText(this, getString(R.string.toast_wifi_sent), Toast.LENGTH_SHORT).show()
            }
            3 -> {
                HapticUtils.vibrate(this, 200)
                binding.tvStatus.text = getString(R.string.status_ready_opened)
                binding.tvStatus.setTextColor(Color.parseColor("#10B981"))
                binding.tvStatusSub.text = getString(R.string.status_sub_ready_opened)
                Toast.makeText(this, getString(R.string.toast_browser_opened), Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (hotspotManager.isHotspotActive) {
                        TouchDropNfcService.autoStep = 1
                        if (selectedFile != null) {
                            binding.tvStatus.text = getString(R.string.status_prompt_tap_phone)
                            binding.tvStatus.setTextColor(Color.parseColor("#10B981"))
                            binding.tvStatusSub.text = getString(R.string.status_sub_tap_phone)
                        } else {
                            binding.tvStatus.text = getString(R.string.status_prompt_select_file)
                            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                            binding.tvStatusSub.text = getString(R.string.status_sub_select_file)
                        }
                    }
                }, 10000)
            }
        }
    }

    private fun setupServer() {
        try {
            server = TouchDropServer(this, 8080).apply {
                onUploadReceived = { file, count ->
                    runOnUiThread {
                        lastReceivedFile = file
                        binding.tvStatus.text = getString(R.string.status_file_received_title)
                        binding.tvStatus.setTextColor(Color.parseColor("#10B981"))
                        binding.tvStatusSub.text = "${file.name} (${FileUtils.formatFileSize(file.length())})"
                        binding.btnOpenReceived.visibility = View.VISIBLE
                        binding.btnOpenReceived.text = getString(R.string.btn_open_file)
                        HapticUtils.vibrate(this@MainActivity, 150)
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.toast_file_saved, count, file.name),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                start()
            }
            updateNfcUrl()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        binding.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.btnTestInBrowser.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(activeShareUrl))
            startActivity(browserIntent)
        }

        // Mod 1: Aynı Wi-Fi Tıklandı
        binding.btnModeWifi.setOnClickListener {
            if (hotspotManager.isHotspotActive) {
                stopHotspotBridge()
            }
            updateModeUi(false)
        }

        // Mod 2: Hızlı Köprü Tıklandı
        binding.btnModeHotspot.setOnClickListener {
            if (!hotspotManager.isHotspotActive) {
                startHotspotBridge()
            }
        }

        binding.btnOpenReceived.setOnClickListener {
            lastReceivedFile?.let { openFile(it) }
        }

        binding.btnSettings.setOnClickListener {
            SettingsBottomSheet.newInstance().show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        binding.btnInfo.setOnClickListener {
            HelpBottomSheet.newInstance(hotspotManager.isHotspotActive).show(supportFragmentManager, HelpBottomSheet.TAG)
        }

        binding.ivQrCode.setOnClickListener {
            Toast.makeText(this, getString(R.string.status_hotspot_qr_sub), Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // HIZLI KÖPRÜ (LOCAL-ONLY HOTSPOT) YÖNETİMİ
    // =========================================================================

    private fun startHotspotBridge() {
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (neededPermissions.isNotEmpty()) {
            hotspotPermissionLauncher.launch(neededPermissions.toTypedArray())
            return
        }

        binding.tvModeHint.text = "Köprü başlatılıyor..."
        hotspotManager.startBridge(
            onStarted = { _, _, shareUrl ->
                activeShareUrl = shareUrl
                updateModeUi(true)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_hotspot_active),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onFailed = { reason ->
                updateModeUi(false)
                Toast.makeText(
                    this@MainActivity,
                    "Köprü başlatılamadı ($reason). Ayarlar açılıyor...",
                    Toast.LENGTH_LONG
                ).show()
                hotspotManager.openHotspotSettings()
            },
            onStopped = {
                updateModeUi(false)
                updateNfcUrl()
            }
        )
    }

    private fun stopHotspotBridge() {
        hotspotManager.stopBridge()
        updateModeUi(false)
        updateNfcUrl()
        Toast.makeText(this, getString(R.string.toast_hotspot_stopped), Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // DOSYA SEÇİMİ VE AÇILMASI
    // =========================================================================

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            uri?.let { handleSelectedUri(it) }
        }
    }

    private fun handleSelectedUri(uri: Uri) {
        val fileName = FileUtils.getFileName(this, uri) ?: "shared_file"
        val tempFile = File(cacheDir, fileName)

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        selectedFile = tempFile
        server?.activeFile = tempFile

        binding.layoutSelectedFile.visibility = View.VISIBLE
        binding.tvSelectedFile.text = "${fileName} (${FileUtils.formatFileSize(tempFile.length())})"

        binding.tvStatus.text = getString(R.string.status_prompt_tap_phone)
        binding.tvStatus.setTextColor(Color.parseColor("#10B981"))
        binding.tvStatusSub.text = getString(R.string.status_sub_tap_phone)

        updateNfcUrl()
        binding.btnTestInBrowser.visibility = View.VISIBLE
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
            val mime = if (extension.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            } else null

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.btn_open_file)))
        } catch (e: Exception) {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (ex: Exception) {
                Toast.makeText(this, "Dosya: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateNfcUrl() {
        val ip = NetworkUtils.getLocalIpAddress()
        if (ip != null && ip != "127.0.0.1") {
            activeShareUrl = "http://$ip:8080"
        } else {
            activeShareUrl = "http://127.0.0.1:8080"
        }
        TouchDropNfcService.currentShareUrl = activeShareUrl
    }

    override fun onResume() {
        super.onResume()
        updateNfcUrl()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled) {
                Toast.makeText(this, getString(R.string.toast_nfc_disabled), Toast.LENGTH_LONG).show()
            }
            try {
                val cardEmulation = CardEmulation.getInstance(nfcAdapter)
                val componentName = ComponentName(this, TouchDropNfcService::class.java)

                cardEmulation.registerAidsForService(
                    componentName,
                    CardEmulation.CATEGORY_OTHER,
                    listOf("D2760000850101", "D2760000850100")
                )
                val setPref = cardEmulation.setPreferredService(this, componentName)
                android.util.Log.d("TouchDrop", "setPreferredService result: $setPref")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_nfc_missing), Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            val cardEmulation = CardEmulation.getInstance(nfcAdapter)
            cardEmulation.unsetPreferredService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TouchDropNfcService.onStepChanged = null
        server?.onUploadReceived = null
        server?.stop()
        server = null
        hotspotManager.stopBridge()
    }
}
