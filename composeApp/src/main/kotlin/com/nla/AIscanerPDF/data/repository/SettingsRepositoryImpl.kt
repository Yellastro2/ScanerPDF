package com.nla.AIscanerPDF.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.nla.AIscanerPDF.domain.model.AppSettings
import com.nla.AIscanerPDF.domain.model.ExportQuality
import com.nla.AIscanerPDF.domain.model.PdfPageSize
import com.nla.AIscanerPDF.domain.model.ThemeMode
import com.nla.AIscanerPDF.domain.repository.SettingsRepository

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val quality = stringPreferencesKey("default_quality")
        val pageSize = stringPreferencesKey("default_page_size")
        val ocrLanguage = stringPreferencesKey("ocr_language")
        val autoDetect = booleanPreferencesKey("auto_detect_corners")
        val aiConsent = booleanPreferencesKey("ai_consent")
        val usedOcr = intPreferencesKey("used_ocr_operations")
        val usedAi = intPreferencesKey("used_ai_operations")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = enumOrDefault(prefs[Keys.theme], ThemeMode.SYSTEM),
            defaultQuality = enumOrDefault(prefs[Keys.quality], ExportQuality.MEDIUM),
            defaultPageSize = enumOrDefault(prefs[Keys.pageSize], PdfPageSize.AUTO),
            ocrLanguage = prefs[Keys.ocrLanguage] ?: "ru+en",
            autoDetectCorners = prefs[Keys.autoDetect] ?: true,
            aiConsentGiven = prefs[Keys.aiConsent] ?: false,
            usedOcrOperations = prefs[Keys.usedOcr] ?: 0,
            usedAiOperations = prefs[Keys.usedAi] ?: 0,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.theme] = mode.name }
    }

    override suspend fun setAutoDetectCorners(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoDetect] = enabled }
    }

    override suspend fun setAiConsentGiven(given: Boolean) {
        context.dataStore.edit { it[Keys.aiConsent] = given }
    }

    override suspend fun incrementOcrOperations() {
        context.dataStore.edit { it[Keys.usedOcr] = (it[Keys.usedOcr] ?: 0) + 1 }
    }

    override suspend fun incrementAiOperations() {
        context.dataStore.edit { it[Keys.usedAi] = (it[Keys.usedAi] ?: 0) + 1 }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
