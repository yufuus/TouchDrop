package com.touchdrop.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.touchdrop.R
import com.touchdrop.databinding.BottomSheetHelpBinding
import com.touchdrop.databinding.ItemHelpSlideBinding
import com.touchdrop.service.TouchDropNfcService
import com.touchdrop.util.HapticUtils

class HelpBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHelpBinding? = null
    private val binding get() = _binding!!

    private var isHotspotMode: Boolean = false
    private var currentSlides: List<HelpSlide> = emptyList()

    data class HelpSlide(
        val imageRes: Int,
        val badgeText: String,
        val title: String,
        val desc: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isHotspotMode = arguments?.getBoolean(ARG_IS_HOTSPOT)
            ?: TouchDropNfcService.isHotspotActive
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val d = dialogInterface as BottomSheetDialog
            val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                val screenHeight = resources.displayMetrics.heightPixels
                // Ekranın %92'sini kaplayarak telefon mockuplarını büyük ve ferah göster
                val targetHeight = (screenHeight * 0.92).toInt()
                sheet.layoutParams.height = targetHeight
                sheet.requestLayout()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.peekHeight = targetHeight
                behavior.isDraggable = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            setupModeTabs()
            setupHotspotCard()
            setupViewPager()
            setupButtons()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getWifiSlides(): List<HelpSlide> = listOf(
        HelpSlide(
            imageRes = R.drawable.ss_guide_mode,
            badgeText = getString(R.string.help_wifi_slide1_badge),
            title = getString(R.string.help_wifi_slide1_title),
            desc = getString(R.string.help_wifi_slide1_desc)
        ),
        HelpSlide(
            imageRes = R.drawable.ss_guide_wifi_tap,
            badgeText = getString(R.string.help_wifi_slide2_badge),
            title = getString(R.string.help_wifi_slide2_title),
            desc = getString(R.string.help_wifi_slide2_desc)
        ),
        HelpSlide(
            imageRes = R.drawable.ss_guide_receiver,
            badgeText = getString(R.string.help_wifi_slide3_badge),
            title = getString(R.string.help_wifi_slide3_title),
            desc = getString(R.string.help_wifi_slide3_desc)
        )
    )

    private fun getBridgeSlides(): List<HelpSlide> = listOf(
        HelpSlide(
            imageRes = R.drawable.ss_guide_bridge_tap1,
            badgeText = getString(R.string.help_bridge_slide1_badge),
            title = getString(R.string.help_bridge_slide1_title),
            desc = getString(R.string.help_bridge_slide1_desc)
        ),
        HelpSlide(
            imageRes = R.drawable.ss_guide_bridge_tap2,
            badgeText = getString(R.string.help_bridge_slide2_badge),
            title = getString(R.string.help_bridge_slide2_title),
            desc = getString(R.string.help_bridge_slide2_desc)
        ),
        HelpSlide(
            imageRes = R.drawable.ss_guide_receiver,
            badgeText = getString(R.string.help_bridge_slide3_badge),
            title = getString(R.string.help_bridge_slide3_title),
            desc = getString(R.string.help_bridge_slide3_desc)
        )
    )

    private fun setupModeTabs() {
        updateTabVisuals()

        binding.btnHelpTabWifi.applyBounceClick {
            if (isHotspotMode) {
                isHotspotMode = false
                updateTabVisuals()
                transitionSlides()
            }
        }

        binding.btnHelpTabHotspot.applyBounceClick {
            if (!isHotspotMode) {
                isHotspotMode = true
                updateTabVisuals()
                transitionSlides()
            }
        }
    }

    private fun updateTabVisuals() {
        val activeWifiBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_mode_active)
        val activeHotspotBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_mode_active_emerald)
        val textPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val textMuted = ContextCompat.getColor(requireContext(), R.color.text_muted)
        val accentEmerald = ContextCompat.getColor(requireContext(), R.color.accent_emerald)

        if (!isHotspotMode) {
            // Mod 1: Aynı Wi-Fi Aktif
            binding.btnHelpTabWifi.background = activeWifiBg
            binding.ivHelpTabWifi.setColorFilter(textPrimary)
            binding.tvHelpTabWifiTitle.setTextColor(textPrimary)
            binding.tvHelpTabWifiSub.setTextColor(accentEmerald)

            binding.btnHelpTabHotspot.setBackgroundColor(Color.TRANSPARENT)
            binding.ivHelpTabHotspot.setColorFilter(textMuted)
            binding.tvHelpTabHotspotTitle.setTextColor(textMuted)
            binding.tvHelpTabHotspotSub.setTextColor(textMuted)
        } else {
            // Mod 2: Hızlı Köprü Aktif
            binding.btnHelpTabWifi.setBackgroundColor(Color.TRANSPARENT)
            binding.ivHelpTabWifi.setColorFilter(textMuted)
            binding.tvHelpTabWifiTitle.setTextColor(textMuted)
            binding.tvHelpTabWifiSub.setTextColor(textMuted)

            binding.btnHelpTabHotspot.background = activeHotspotBg
            binding.ivHelpTabHotspot.setColorFilter(accentEmerald)
            binding.tvHelpTabHotspotTitle.setTextColor(accentEmerald)
            binding.tvHelpTabHotspotSub.setTextColor(accentEmerald)
        }
    }

    private fun transitionSlides() {
        setupHotspotCard()
        binding.viewPagerHelp.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                currentSlides = if (isHotspotMode) getBridgeSlides() else getWifiSlides()
                binding.viewPagerHelp.adapter = HelpSlideAdapter(currentSlides)
                binding.viewPagerHelp.setCurrentItem(0, false)
                updateDots(0)
                binding.viewPagerHelp.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .start()
            }
            .start()
    }

    private fun setupHotspotCard() {
        if (isHotspotMode && TouchDropNfcService.isHotspotActive && !TouchDropNfcService.hotspotSsid.isNullOrEmpty()) {
            val ssid = TouchDropNfcService.hotspotSsid
            val pass = TouchDropNfcService.hotspotPass ?: ""
            binding.cardHotspotInfo.visibility = View.VISIBLE
            binding.tvHotspotSsid.text = "Ağ: $ssid"
            binding.tvHotspotPass.text = "Şifre: $pass"

            binding.btnCopyPass.applyBounceClick {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Hotspot Password", pass)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Şifre panoya kopyalandı", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.cardHotspotInfo.visibility = View.GONE
        }
    }

    private fun setupViewPager() {
        currentSlides = if (isHotspotMode) getBridgeSlides() else getWifiSlides()
        val adapter = HelpSlideAdapter(currentSlides)
        binding.viewPagerHelp.adapter = adapter

        // Akıcı 3D derinlik ve sayfa geçiş animasyonu (PageTransformer)
        val compositePageTransformer = CompositePageTransformer().apply {
            addTransformer(MarginPageTransformer((10 * resources.displayMetrics.density).toInt()))
            addTransformer { page, position ->
                val absPos = kotlin.math.abs(position)
                if (absPos >= 1f) {
                    page.alpha = 0.35f
                    page.scaleY = 0.90f
                    page.scaleX = 0.90f
                } else {
                    page.alpha = 1f - (0.5f * absPos)
                    val scale = 0.90f + ((1f - absPos) * 0.10f)
                    page.scaleY = scale
                    page.scaleX = scale
                }
            }
        }
        binding.viewPagerHelp.setPageTransformer(compositePageTransformer)

        binding.viewPagerHelp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDots(position)

                if (position == currentSlides.size - 1) {
                    binding.btnNextHelp.text = getString(R.string.btn_got_it)
                    binding.btnSkipHelp.visibility = View.INVISIBLE
                } else {
                    binding.btnNextHelp.text = getString(R.string.btn_next)
                    binding.btnSkipHelp.visibility = View.VISIBLE
                }
            }
        })

        binding.viewPagerHelp.setCurrentItem(0, false)
        updateDots(0)
    }

    private fun updateDots(activePosition: Int) {
        try {
            val dots = listOf(binding.dot0, binding.dot1, binding.dot2)
            val density = resources.displayMetrics.density

            for (i in dots.indices) {
                val dot = dots[i]
                val w = if (i == activePosition) (20 * density).toInt() else (6 * density).toInt()
                val h = (6 * density).toInt()
                val params = dot.layoutParams
                if (params != null) {
                    params.width = w
                    params.height = h
                    dot.layoutParams = params
                }
                dot.setBackgroundResource(if (i == activePosition) R.drawable.dot_active else R.drawable.dot_inactive)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupButtons() {
        binding.btnCloseHelp.applyBounceClick {
            dismiss()
        }

        binding.btnSkipHelp.applyBounceClick {
            dismiss()
        }

        binding.btnNextHelp.applyBounceClick {
            val current = binding.viewPagerHelp.currentItem
            if (current < currentSlides.size - 1) {
                binding.viewPagerHelp.setCurrentItem(current + 1, true)
            } else {
                dismiss()
            }
        }
    }

    /**
     * Tıklamalara canlı yaylanma (spring bounce) ve dokunsal geri bildirim efekti
     */
    private fun View.applyBounceClick(onClick: () -> Unit) {
        setOnClickListener {
            HapticUtils.vibrate(context, 15)
            animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(70)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .withEndAction { onClick() }
                        .start()
                }
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class HelpSlideAdapter(private val items: List<HelpSlide>) :
        RecyclerView.Adapter<HelpSlideAdapter.SlideViewHolder>() {

        inner class SlideViewHolder(val itemBinding: ItemHelpSlideBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val itemBinding = ItemHelpSlideBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return SlideViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val item = items[position]
            with(holder.itemBinding) {
                ivSlideImage.setImageResource(item.imageRes)
                tvSlideStepBadge.text = item.badgeText
                tvSlideTitle.text = item.title
                tvSlideDesc.text = item.desc

                // Fotoğraf kartına tıklandığında eğlenceli büyüme-küçülme ve haptik animasyonu
                cardSlidePreview.setOnClickListener {
                    HapticUtils.vibrate(it.context, 20)
                    it.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(120)
                        .withEndAction {
                            it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        }
                        .start()
                }

                // Slayt metin alanına hafif yukarıdan giriş animasyonu
                layoutSlideText.alpha = 0f
                layoutSlideText.translationY = 16f
                layoutSlideText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .start()
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val TAG = "HelpBottomSheet"
        private const val ARG_IS_HOTSPOT = "arg_is_hotspot"

        fun newInstance(isHotspot: Boolean = false): HelpBottomSheet {
            val fragment = HelpBottomSheet()
            val args = Bundle().apply {
                putBoolean(ARG_IS_HOTSPOT, isHotspot)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
