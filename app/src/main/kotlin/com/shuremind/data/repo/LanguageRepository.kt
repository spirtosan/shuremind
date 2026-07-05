package com.shuremind.data.repo

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** D-18: app_language setting (system|en|bg|ru, default system). */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    BULGARIAN("bg"),
    RUSSIAN("ru")
}

/**
 * Reads/writes the UI language via AppCompatDelegate.setApplicationLocales. With
 * autoStoreLocales enabled (see AndroidManifest), AppCompat persists the chosen locales itself —
 * no separate DataStore round trip is needed to survive process death.
 */
class LanguageRepository {

    fun get(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.SYSTEM
        return when (locales[0]?.language) {
            "bg" -> AppLanguage.BULGARIAN
            "ru" -> AppLanguage.RUSSIAN
            "en" -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
    }

    fun set(language: AppLanguage) {
        val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
