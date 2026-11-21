package com.touchdrop.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.touchdrop.R
import com.touchdrop.databinding.BottomSheetSettingsBinding
import com.touchdrop.util.ThemeManager
import java.util.Locale

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupThemeSelection()
        setupLanguageSelection()
        setupListeners()
    }

    private fun setupThemeSelection() {
        val currentTheme = ThemeManager.getSavedTheme(requireContext())
        updateThemePills(currentTheme)

        binding.btnThemeLight.setOnClickListener { changeTheme(ThemeManager.THEME_LIGHT) }
        binding.btnThemeObsidian.setOnClickListener { changeTheme(ThemeManager.THEME_OBSIDIAN) }
        binding.btnThemeSystem.setOnClickListener { changeTheme(ThemeManager.THEME_SYSTEM) }
    }

    private fun updateThemePills(activeTheme: String) {
        val activeBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_mode_active_emerald)
        val defaultBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pill)
        val emeraldColor = ContextCompat.getColor(requireContext(), R.color.accent_emerald)
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        // Light (Grimsi)
        val isLight = (activeTheme == ThemeManager.THEME_LIGHT)
        binding.btnThemeLight.background = if (isLight) activeBg else defaultBg
        binding.ivThemeLight.setColorFilter(if (isLight) emeraldColor else textSecondary)
        binding.tvThemeLight.setTextColor(if (isLight) emeraldColor else textSecondary)

        // Obsidian
        val isObsidian = (activeTheme == ThemeManager.THEME_OBSIDIAN)
        binding.btnThemeObsidian.background = if (isObsidian) activeBg else defaultBg
        binding.ivThemeObsidian.setColorFilter(if (isObsidian) emeraldColor else textSecondary)
        binding.tvThemeObsidian.setTextColor(if (isObsidian) emeraldColor else textSecondary)

        // System
        val isSystem = (activeTheme == ThemeManager.THEME_SYSTEM)
        binding.btnThemeSystem.background = if (isSystem) activeBg else defaultBg
        binding.ivThemeSystem.setColorFilter(if (isSystem) emeraldColor else textSecondary)
        binding.tvThemeSystem.setTextColor(if (isSystem) emeraldColor else textSecondary)
    }

    private fun changeTheme(theme: String) {
        ThemeManager.setTheme(requireContext(), theme)
        dismiss()
    }

    private fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0]?.language?.lowercase() ?: "tr"
        } else {
            val def = Locale.getDefault().language.lowercase()
            if (def in listOf("tr", "en", "de", "es", "fr")) def else "tr"
        }
    }

    private fun setupLanguageSelection() {
        val currentLang = getCurrentLanguage()
        updateLanguagePills(currentLang)

        binding.btnLangTr.setOnClickListener { changeLanguage("tr") }
        binding.btnLangEn.setOnClickListener { changeLanguage("en") }
        binding.btnLangDe.setOnClickListener { changeLanguage("de") }
        binding.btnLangEs.setOnClickListener { changeLanguage("es") }
        binding.btnLangFr.setOnClickListener { changeLanguage("fr") }
    }

    private fun updateLanguagePills(activeLang: String) {
        val langMap = mapOf(
            "tr" to Pair(binding.btnLangTr, binding.ivCheckTr),
            "en" to Pair(binding.btnLangEn, binding.ivCheckEn),
            "de" to Pair(binding.btnLangDe, binding.ivCheckDe),
            "es" to Pair(binding.btnLangEs, binding.ivCheckEs),
            "fr" to Pair(binding.btnLangFr, binding.ivCheckFr)
        )

        val activeBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_mode_active_emerald)
        val defaultBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pill)
        val emeraldColor = ContextCompat.getColor(requireContext(), R.color.accent_emerald)
        val textSecondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        langMap.forEach { (code, views) ->
            val (btn, ivCheck) = views
            val isActive = (code == activeLang)
            btn.background = if (isActive) activeBg else defaultBg
            ivCheck.visibility = if (isActive) View.VISIBLE else View.GONE

            when (code) {
                "tr" -> binding.tvLangTr.setTextColor(if (isActive) emeraldColor else textSecondaryColor)
                "en" -> binding.tvLangEn.setTextColor(if (isActive) emeraldColor else textSecondaryColor)
                "de" -> binding.tvLangDe.setTextColor(if (isActive) emeraldColor else textSecondaryColor)
                "es" -> binding.tvLangEs.setTextColor(if (isActive) emeraldColor else textSecondaryColor)
                "fr" -> binding.tvLangFr.setTextColor(if (isActive) emeraldColor else textSecondaryColor)
            }
        }
    }

    private fun changeLanguage(langCode: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))
        dismiss()
    }

    private fun setupListeners() {
        binding.btnSettingsDone.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
        fun newInstance() = SettingsBottomSheet()
    }
}
