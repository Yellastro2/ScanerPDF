package com.nla.AIscanerPDF.data.ai

import kotlinx.serialization.Serializable
import com.nla.AIscanerPDF.domain.model.AiSummary
import com.nla.AIscanerPDF.domain.model.ContractAnalysis
import com.nla.AIscanerPDF.domain.model.ContractClause
import com.nla.AIscanerPDF.domain.model.DetectedAmount
import com.nla.AIscanerPDF.domain.model.DetectedDate
import com.nla.AIscanerPDF.domain.model.ExtractedDocumentData
import com.nla.AIscanerPDF.domain.model.ExtractedField

@Serializable
data class AiTextRequestDto(val text: String, val language: String)

@Serializable
data class AiSummaryDto(
    val documentType: String? = null,
    val shortSummary: String,
    val keyPoints: List<String> = emptyList(),
    val dates: List<DetectedDateDto> = emptyList(),
    val amounts: List<DetectedAmountDto> = emptyList(),
    val requiredActions: List<String> = emptyList(),
) {
    fun toDomain() = AiSummary(
        documentType = documentType,
        shortSummary = shortSummary,
        keyPoints = keyPoints,
        dates = dates.map { it.toDomain() },
        amounts = amounts.map { it.toDomain() },
        requiredActions = requiredActions,
    )
}

@Serializable
data class DetectedDateDto(val value: String, val description: String? = null) {
    fun toDomain() = DetectedDate(value, description)
}

@Serializable
data class DetectedAmountDto(
    val value: String,
    val currency: String? = null,
    val description: String? = null,
) {
    fun toDomain() = DetectedAmount(value, currency, description)
}

@Serializable
data class ExtractedFieldDto(
    val name: String,
    val value: String,
    val page: Int? = null,
    val sourceFragment: String? = null,
    val confidence: Float? = null,
) {
    fun toDomain() = ExtractedField(name, value, page, sourceFragment, confidence)
}

@Serializable
data class ExtractedDataDto(val fields: List<ExtractedFieldDto> = emptyList()) {
    fun toDomain() = ExtractedDocumentData(fields.map { it.toDomain() })
}

@Serializable
data class ContractClauseDto(
    val title: String,
    val description: String,
    val sourceFragment: String? = null,
) {
    fun toDomain() = ContractClause(title, description, sourceFragment)
}

@Serializable
data class ContractAnalysisDto(
    val importantTerms: List<ContractClauseDto> = emptyList(),
    val risks: List<ContractClauseDto> = emptyList(),
    val deadlines: List<ContractClauseDto> = emptyList(),
    val moneyTerms: List<ContractClauseDto> = emptyList(),
    val questionsToClarify: List<String> = emptyList(),
) {
    fun toDomain() = ContractAnalysis(
        importantTerms = importantTerms.map { it.toDomain() },
        risks = risks.map { it.toDomain() },
        deadlines = deadlines.map { it.toDomain() },
        moneyTerms = moneyTerms.map { it.toDomain() },
        questionsToClarify = questionsToClarify,
    )
}
