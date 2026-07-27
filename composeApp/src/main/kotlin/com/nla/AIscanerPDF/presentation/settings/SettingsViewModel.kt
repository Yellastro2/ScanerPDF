package com.nla.AIscanerPDF.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nla.AIscanerPDF.domain.model.AppSettings
import com.nla.AIscanerPDF.domain.model.ThemeMode
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val documents: DocumentRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onAutoDetectChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDetectCorners(enabled) }
    }

    /** «Удалить все данные»: документы, страницы, файлы (п. 10 ТЗ). */
    fun onDeleteAll() {
        viewModelScope.launch { documents.deleteAllDocuments() }
    }
}
