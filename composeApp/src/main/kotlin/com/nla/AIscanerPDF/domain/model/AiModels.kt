package com.nla.AIscanerPDF.domain.model

sealed interface AiAnalysisResult {
    data class Summary(val summary: AiSummary) : AiAnalysisResult
    data class Extraction(val data: ExtractedDocumentData) : AiAnalysisResult
    data class Contract(val analysis: ContractAnalysis) : AiAnalysisResult
}

data class AiSummary(
    val documentType: String?,
    val shortSummary: String,
    val keyPoints: List<String>,
    val dates: List<DetectedDate>,
    val amounts: List<DetectedAmount>,
    val requiredActions: List<String>,
)

data class DetectedDate(val value: String, val description: String?)

data class DetectedAmount(val value: String, val currency: String?, val description: String?)

/**
 * Извлечённое поле. Данные не придумываются: поле присутствует
 * только если найдено в тексте (п. 9.2 ТЗ).
 */
data class ExtractedField(
    val name: String,
    val value: String,
    val page: Int?,
    val sourceFragment: String?,
    val confidence: Float?,
)

data class ExtractedDocumentData(
    val fields: List<ExtractedField>,
)

data class ContractClause(
    val title: String,
    val description: String,
    val sourceFragment: String?,
)

data class ContractAnalysis(
    val importantTerms: List<ContractClause>,
    val risks: List<ContractClause>,
    val deadlines: List<ContractClause>,
    val moneyTerms: List<ContractClause>,
    val questionsToClarify: List<String>,
)
