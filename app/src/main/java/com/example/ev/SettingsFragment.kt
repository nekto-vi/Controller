package com.example.ev

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageSection(view)
        setupThemeSection(view)
    }

    private fun setupLanguageSection(view: View) {
        val englishCheckBox = view.findViewById<CheckBox>(R.id.EnglishButton)
        val russianCheckBox = view.findViewById<CheckBox>(R.id.RussianButton)

        updateLanguageCheckBoxes(englishCheckBox, russianCheckBox)

        englishCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && LocaleHelper.getLanguage(requireContext()) != "en") {
                changeLanguage("en")
            }
        }

        russianCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && LocaleHelper.getLanguage(requireContext()) != "ru") {
                changeLanguage("ru")
            }
        }
    }

    private fun updateLanguageCheckBoxes(englishCheckBox: CheckBox, russianCheckBox: CheckBox) {
        val currentLanguage = LocaleHelper.getLanguage(requireContext())

        englishCheckBox.setOnCheckedChangeListener(null)
        russianCheckBox.setOnCheckedChangeListener(null)

        when (currentLanguage) {
            "en" -> {
                englishCheckBox.isChecked = true
                russianCheckBox.isChecked = false
            }
            "ru" -> {
                englishCheckBox.isChecked = false
                russianCheckBox.isChecked = true
            }
        }

        setupLanguageListeners(englishCheckBox, russianCheckBox)
    }

    private fun setupLanguageListeners(englishCheckBox: CheckBox, russianCheckBox: CheckBox) {
        englishCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && LocaleHelper.getLanguage(requireContext()) != "en") {
                russianCheckBox.isChecked = false
                changeLanguage("en")
            }
        }

        russianCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && LocaleHelper.getLanguage(requireContext()) != "ru") {
                englishCheckBox.isChecked = false
                changeLanguage("ru")
            }
        }
    }

    private fun changeLanguage(languageCode: String) {
        LocaleHelper.setLocale(requireContext(), languageCode)

        val message = if (languageCode == "en") "Language changed to English" else "Язык изменен на Русский"
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

        restartActivity()
    }

    private fun setupThemeSection(view: View) {
        val lightRadio = view.findViewById<RadioButton>(R.id.LightButton)
        val darkRadio = view.findViewById<RadioButton>(R.id.DarkButton)
        val systemRadio = view.findViewById<RadioButton>(R.id.SystemButton)
        val themeGroup = view.findViewById<RadioGroup>(R.id.themeRadioGroup)

        when (ThemeHelper.getTheme(requireContext())) {
            "light" -> lightRadio.isChecked = true
            "dark" -> darkRadio.isChecked = true
            else -> systemRadio.isChecked = true
        }

        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.LightButton -> "light"
                R.id.DarkButton -> "dark"
                R.id.SystemButton -> "system"
                else -> "system"
            }

            if (theme != ThemeHelper.getTheme(requireContext())) {
                ThemeHelper.setTheme(requireContext(), theme)

                val message = when (theme) {
                    "light" -> getString(R.string.theme_light_applied)
                    "dark" -> getString(R.string.theme_dark_applied)
                    else -> getString(R.string.theme_system_applied)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restartActivity() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra("destination", R.id.settings)
        startActivity(intent)
        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        requireActivity().finish()
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val englishCheckBox = it.findViewById<CheckBox>(R.id.EnglishButton)
            val russianCheckBox = it.findViewById<CheckBox>(R.id.RussianButton)
            updateLanguageCheckBoxes(englishCheckBox, russianCheckBox)
        }
    }
}