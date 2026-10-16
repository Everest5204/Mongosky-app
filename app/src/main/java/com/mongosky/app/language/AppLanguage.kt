package com.mongosky.app.language

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.EnumMap

/** Device-only preference. No account writes, network calls, SDK, or Activity recreation. */
internal object AppLanguage {
    private var application: Context? = null
    var current by mutableStateOf(AppLocale.ENGLISH)
        private set

    fun initialize(context: Context) {
        if (application != null) return
        application = context.applicationContext
        current = try {
            AppLocale.parse(preferences().getString("locale", null))
        } catch (_: ClassCastException) {
            AppLocale.ENGLISH
        }
    }

    fun select(locale: AppLocale): Boolean {
        if (current == locale) return true
        return try {
            preferences().edit().putString("locale", locale.tag).apply()
            current = locale
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun preferences() = checkNotNull(application)
        .getSharedPreferences("mongosky_language", Context.MODE_PRIVATE)

    fun configuration(base: Configuration, locale: AppLocale = current) =
        Configuration(base).apply { setLocales(LocaleList(Locale.forLanguageTag(locale.tag))) }

    // Rebuild only if device configuration changes (font size, orientation, etc.).
    private var cachedConfiguration: Configuration? = null
    private val cachedResources = EnumMap<AppLocale, Resources>(AppLocale::class.java)
    @Synchronized
    fun resources(locale: AppLocale): Resources? {
        val context = application ?: return null
        val base = context.resources.configuration
        if (base != cachedConfiguration) {
            cachedResources.clear()
            cachedConfiguration = Configuration(base)
        }
        return cachedResources.getOrPut(locale) {
            context.createConfigurationContext(configuration(base, locale)).resources
        }
    }
}
