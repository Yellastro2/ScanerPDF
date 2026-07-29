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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.domain.model.SubscriptionPeriod
import com.nla.AIscanerPDF.domain.model.SubscriptionStatus
import ru.rustore.sdk.core.util.RuStoreUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current

    state.message?.let { message ->
        val text = stringResource(message.textRes())
        LaunchedEffect(message) {
            viewModel.consumeMessage()
            if (message == PremiumMessage.RESTORE_AUTHORIZATION_REQUIRED) {
                val launchFailed =
                    runCatching { RuStoreUtils.openRuStoreAuthorization(context) }.isFailure
                if (launchFailed) snackbarHostState.showSnackbar(text)
            } else {
                snackbarHostState.showSnackbar(text)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.premium_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            state.subscription is SubscriptionStatus.Premium -> {
                val subscription = state.subscription as SubscriptionStatus.Premium
                val activeProduct =
                    state.products.firstOrNull { it.productId == subscription.productId }
                val isLifetime =
                    subscription.isLifetime || activeProduct?.period == SubscriptionPeriod.ONE_TIME
                PremiumSubscriptionActivePrototypeScreen(
                    renewalDate = subscription.expiresAtMillis?.let(::formatPremiumDate) ?: "неизвестной даты",
                    productTitle = activeProduct?.title ?: subscription.productId,
                    isLifetime = isLifetime,
                    autoRenewEnabled = subscription.autoRenewEnabled,
                    isCancelling = state.isCancellingAutoRenew,
                    onCancelAutoRenew = viewModel::onCancelAutoRenew,
                    modifier = Modifier.padding(padding),
                )
            }
            state.products.isEmpty() -> Text(
                stringResource(R.string.premium_unavailable),
                Modifier.padding(padding).padding(24.dp),
            )
            else -> PremiumOfferPrototypeScreen(
                products = state.products,
                isPurchasing = state.isPurchasing,
                isRestoring = state.isRestoring,
                onPurchase = viewModel::onPurchase,
                onRestore = viewModel::onRestore,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private fun PremiumMessage.textRes(): Int = when (this) {
    PremiumMessage.PURCHASE_SUCCESS -> R.string.premium_purchase_success
    PremiumMessage.PURCHASE_ACTIVATION_PENDING -> R.string.premium_purchase_activation_pending
    PremiumMessage.PURCHASE_ERROR -> R.string.error_purchase
    PremiumMessage.RESTORED -> R.string.premium_restored
    PremiumMessage.NOT_RESTORED -> R.string.premium_nothing_to_restore
    PremiumMessage.RESTORE_AUTHORIZATION_REQUIRED -> R.string.premium_restore_authorization_required
    PremiumMessage.RESTORE_TEMPORARILY_UNAVAILABLE -> R.string.premium_restore_temporarily_unavailable
    PremiumMessage.PRODUCTS_UNAVAILABLE -> R.string.premium_unavailable
    PremiumMessage.AUTO_RENEW_CANCELLED -> R.string.premium_auto_renew_cancelled
    PremiumMessage.AUTO_RENEW_CANCEL_ERROR -> R.string.premium_auto_renew_cancel_error
}

private fun formatPremiumDate(epochMillis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(epochMillis))
