package com.nla.AIscanerPDF.presentation.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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

/**
 * Экран Premium. Показывает реальные продукты RuStore с ценами из
 * `priceLabel`; плейсхолдеры вместо цены не показываются (п. 11 ТЗ).
 * Paywall не показывается при первом запуске приложения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedParameter")
@Composable
fun PremiumScreen(navController: NavHostController, viewModel: PremiumViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    state.message?.let { message ->
        val text = stringResource(message.textRes())
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.premium_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                stringResource(R.string.premium_subtitle),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("• " + stringResource(R.string.premium_benefit_pdf), Modifier.padding(top = 16.dp))
            Text("• " + stringResource(R.string.premium_benefit_ocr), Modifier.padding(top = 8.dp))
            Text("• " + stringResource(R.string.premium_benefit_ai), Modifier.padding(top = 8.dp))

            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                state.isPremium -> Text(
                    stringResource(R.string.premium_active),
                    Modifier.padding(top = 32.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.products.isEmpty() -> Text(
                    stringResource(R.string.premium_unavailable),
                    Modifier.padding(top = 32.dp),
                )
                else -> state.products.forEach { product ->
                    Button(
                        onClick = { viewModel.onPurchase(product.productId) },
                        enabled = !state.isPurchasing,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text("${product.title} — ${product.price}")
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::onRestore,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.settings_restore))
            }
        }
    }
}

private fun PremiumMessage.textRes(): Int = when (this) {
    PremiumMessage.PURCHASE_SUCCESS -> R.string.premium_purchase_success
    PremiumMessage.PURCHASE_ERROR -> R.string.error_purchase
    PremiumMessage.RESTORED -> R.string.premium_restored
    PremiumMessage.NOT_RESTORED -> R.string.premium_nothing_to_restore
    PremiumMessage.PRODUCTS_UNAVAILABLE -> R.string.premium_unavailable
}
