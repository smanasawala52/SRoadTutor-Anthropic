package com.sroadtutor.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.Locale

/**
 * SRoadTutor's per-app locale catalog.
 *
 * <p>Storage: a tiny {@link SharedPreferences} file ("locale_prefs") so that
 * {@link #wrap} can do a synchronous read inside {@code attachBaseContext}
 * without risk of an ANR. {@link #currentLanguage} bridges the same pref into
 * a Compose-friendly {@link Flow} via an OnSharedPreferenceChangeListener.</p>
 *
 * <p>Pre-Android 13 locale switching is done by overriding the resource
 * configuration in {@code attachBaseContext}. Works on every API level the
 * app targets (24+) and avoids pulling in {@code androidx.appcompat}.</p>
 */
object AppLocales {

    /** Stable language tags. Keep aligned with the values-* resource folders. */
    enum class Language(val tag: String, val backendCode: String, val displayResName: String) {
        ENGLISH("en", "en", "lang_en"),
        PUNJABI("pa", "pa", "lang_pa"),
        HINDI("hi", "hi", "lang_hi"),
        URDU("ur", "ur", "lang_ur"),
        BENGALI("bn", "bn", "lang_bn"),
        ARABIC("ar", "ar", "lang_ar"),
        KOREAN("ko", "ko", "lang_ko"),
        CANTONESE("zh-HK", "yue", "lang_yue"),
        FILIPINO("fil", "fil", "lang_fil");

        companion object {
            fun fromTag(tag: String?): Language =
                entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH
        }
    }

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_TAG = "lang_tag"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Reactive read for Compose. Emits on every change to the lang tag. */
    fun currentLanguage(context: Context): Flow<Language> {
        val sp = prefs(context.applicationContext)
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_TAG) {
                    trySend(Language.fromTag(sp.getString(KEY_TAG, null)))
                }
            }
            sp.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
        }.onStart { emit(Language.fromTag(sp.getString(KEY_TAG, null))) }
    }

    /** Persist the user's choice. Async — UI then recreates the activity. */
    fun setLanguageBlocking(context: Context, language: Language) {
        prefs(context.applicationContext).edit().putString(KEY_TAG, language.tag).apply()
    }

    /** suspend wrapper for symmetry with other coroutine code paths. */
    suspend fun setLanguage(context: Context, language: Language) =
        setLanguageBlocking(context, language)

    /**
     * Wrap a base context with the persisted locale's configuration. Safe to
     * call from MainActivity#attachBaseContext — pure synchronous SharedPreferences
     * read.
     */
    fun wrap(base: Context): ContextWrapper {
        val tag = prefs(base.applicationContext).getString(KEY_TAG, null)
        val resolved = Language.fromTag(tag)
        val locale = toLocale(resolved)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        val locales = LocaleList(locale)
        LocaleList.setDefault(locales)
        config.setLocales(locales)
        config.setLayoutDirection(locale)
        return object : ContextWrapper(base.createConfigurationContext(config)) {}
    }

    private fun toLocale(language: Language): Locale = when (language) {
        Language.CANTONESE -> Locale.Builder().setLanguage("zh").setRegion("HK").build()
        Language.FILIPINO -> Locale.forLanguageTag("fil")
        else -> Locale.forLanguageTag(language.tag)
    }
}
