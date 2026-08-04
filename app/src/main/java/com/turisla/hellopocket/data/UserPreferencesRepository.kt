package com.turisla.hellopocket.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.turisla.hellopocket.utils.loggerI

class UserPreferencesRepository(context: Context) {

    companion object {
        private const val USER_PREFS_NAME = "user_prefs"
        private const val PREF_KEY_LANGUAGE = "language"

        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_CHINESE = "zh"
        const val LANGUAGE_KOREAN = "ko"
        const val LANGUAGE_VIETNAMESE = "vi"
        const val LANGUAGE_HINDI = "hi"
        const val LANGUAGE_SPANISH = "es"
        const val LANGUAGE_PORTUGUESE = "pt"

        const val PREF_KEY_THEME = "theme"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

    }

    private val prefs = context.getSharedPreferences(USER_PREFS_NAME, Context.MODE_PRIVATE)

    fun getLanguage(): String {
        return prefs.getString(PREF_KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }

    fun saveLanguage(language: String) {
        prefs.edit { putString(PREF_KEY_LANGUAGE, language) }
    }

    fun applyLanguage() {
        val language = getLanguage()
        val localeList = when (language) {
            LANGUAGE_ENGLISH -> LocaleListCompat.forLanguageTags("en")
            LANGUAGE_CHINESE -> LocaleListCompat.forLanguageTags("zh")
            LANGUAGE_KOREAN -> LocaleListCompat.forLanguageTags("ko")
            LANGUAGE_VIETNAMESE -> LocaleListCompat.forLanguageTags("vi")
            LANGUAGE_HINDI -> LocaleListCompat.forLanguageTags("hi")
            LANGUAGE_SPANISH -> LocaleListCompat.forLanguageTags("es")
            LANGUAGE_PORTUGUESE -> LocaleListCompat.forLanguageTags("pt")
            else -> LocaleListCompat.getEmptyLocaleList() // "system"
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getTheme(): String {
        return prefs.getString(PREF_KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun saveTheme(theme: String) {
        prefs.edit { putString(PREF_KEY_THEME, theme) }
    }

    fun applyTheme() {
        val theme = getTheme()
        val mode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
