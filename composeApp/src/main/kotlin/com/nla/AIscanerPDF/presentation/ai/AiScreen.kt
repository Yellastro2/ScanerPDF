package com.nla.AIscanerPDF.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.domain.model.ContractClause
import com.nla.AIscanerPDF.presentation.common.toMessage
import com.nla.AIscanerPDF.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(navController: NavHostController, viewModel: AiViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AiUiEffect.OpenPremium -> navController.navigate(Routes.PREMIUM)
            }
        }
    }

    state.error?.let { error ->
        val message = error.toMessage()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ai_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Дисклеймер обязателен (п. 9.3 ТЗ)
            Card {
                Text(
                    stringResource(R.string.ai_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }

            if (!state.hasRecognizedText) {
                Text(stringResource(R.string.ai_no_text), style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.onRun(AiMode.SUMMARY) },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.ai_summarize)) }
                OutlinedButton(
                    onClick = { viewModel.onRun(AiMode.EXTRACTION) },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.ai_extract)) }
            }
            OutlinedButton(
                onClick = { viewModel.onRun(AiMode.CONTRACT) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.ai_contract)) }

            if (state.isRecognizingDocument) {
                Text(stringResource(R.string.ai_recognizing_document))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (state.isLoading) {
                Text(stringResource(R.string.ai_in_progress))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.summary?.let { summary ->
                SectionTitle(stringResource(R.string.ai_summarize))
                summary.documentType?.let {
                    Text(stringResource(R.string.ai_document_type) + ": " + it)
                }
                Text(summary.shortSummary)
                BulletSection(stringResource(R.string.ai_key_points), summary.keyPoints)
                BulletSection(
                    stringResource(R.string.ai_dates),
                    summary.dates.map { listOfNotNull(it.value, it.description).joinToString(" — ") },
                )
                BulletSection(
                    stringResource(R.string.ai_amounts),
                    summary.amounts.map {
                        listOfNotNull(it.value, it.currency, it.description).joinToString(" ")
                    },
                )
                BulletSection(stringResource(R.string.ai_actions), summary.requiredActions)
            }

            state.extraction?.let { extraction ->
                SectionTitle(stringResource(R.string.ai_extract))
                if (extraction.fields.isEmpty()) {
                    Text(stringResource(R.string.ai_result_empty))
                } else {
                    extraction.fields.forEach { field ->
                        Text("• ${field.name}: ${field.value}")
                    }
                }
            }

            state.contract?.let { contract ->
                SectionTitle(stringResource(R.string.ai_contract))
                ClauseSection(stringResource(R.string.ai_important_terms), contract.importantTerms)
                ClauseSection(stringResource(R.string.ai_risks), contract.risks)
                ClauseSection(stringResource(R.string.ai_deadlines), contract.deadlines)
                ClauseSection(stringResource(R.string.ai_money_terms), contract.moneyTerms)
                BulletSection(stringResource(R.string.ai_questions), contract.questionsToClarify)
            }
        }
    }

    if (state.showConsentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onConsentDeclined,
            title = { Text(stringResource(R.string.ai_consent_title)) },
            text = { Text(stringResource(R.string.ai_consent_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConsentAccepted) {
                    Text(stringResource(R.string.ai_consent_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onConsentDeclined) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    items.forEach { Text("• $it") }
}

@Composable
private fun ClauseSection(title: String, clauses: List<ContractClause>) {
    if (clauses.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    clauses.forEach { clause ->
        Text("• ${clause.title}: ${clause.description}")
        clause.sourceFragment?.let {
            Text(
                "  «$it»",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
