package com.example.ev

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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

        val englishCheckBox = view.findViewById<CheckBox>(R.id.EnglishButton)
        val russianCheckBox = view.findViewById<CheckBox>(R.id.RussianButton)

        updateCheckBoxes(englishCheckBox, russianCheckBox)

        englishCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                russianCheckBox.setOnCheckedChangeListener(null)
                russianCheckBox.isChecked = false

                setRussianCheckBoxListener(russianCheckBox, englishCheckBox)

                changeLanguage("en")
            }
        }

        russianCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                englishCheckBox.setOnCheckedChangeListener(null)
                englishCheckBox.isChecked = false

                setEnglishCheckBoxListener(englishCheckBox, russianCheckBox)

                changeLanguage("ru")
            }
        }
    }

    private fun updateCheckBoxes(englishCheckBox: CheckBox, russianCheckBox: CheckBox) {
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

        setEnglishCheckBoxListener(englishCheckBox, russianCheckBox)
        setRussianCheckBoxListener(russianCheckBox, englishCheckBox)
    }

    private fun setEnglishCheckBoxListener(englishCheckBox: CheckBox, russianCheckBox: CheckBox) {
        englishCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                russianCheckBox.setOnCheckedChangeListener(null)
                russianCheckBox.isChecked = false
                setRussianCheckBoxListener(russianCheckBox, englishCheckBox)
                changeLanguage("en")
            }
        }
    }

    private fun setRussianCheckBoxListener(russianCheckBox: CheckBox, englishCheckBox: CheckBox) {
        russianCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                englishCheckBox.setOnCheckedChangeListener(null)
                englishCheckBox.isChecked = false
                setEnglishCheckBoxListener(englishCheckBox, russianCheckBox)
                changeLanguage("ru")
            }
        }
    }

    private fun changeLanguage(languageCode: String) {
        val currentLanguage = LocaleHelper.getLanguage(requireContext())
        if (currentLanguage == languageCode) {
            return
        }

        LocaleHelper.setLocale(requireContext(), languageCode)

        val message = if (languageCode == "en") "Language changed to English" else "Язык изменен на Русский"
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

        requireActivity().recreate()
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val englishCheckBox = it.findViewById<CheckBox>(R.id.EnglishButton)
            val russianCheckBox = it.findViewById<CheckBox>(R.id.RussianButton)
            updateCheckBoxes(englishCheckBox, russianCheckBox)
        }
    }
}