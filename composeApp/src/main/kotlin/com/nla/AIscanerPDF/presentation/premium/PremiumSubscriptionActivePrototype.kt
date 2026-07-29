package com.nla.AIscanerPDF.presentation.premium

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nla.AIscanerPDF.domain.model.ThemeMode
import com.nla.AIscanerPDF.presentation.theme.ScannerTheme

/** Экран активной подписки или бессрочного Premium-доступа. */
@Composable
fun PremiumSubscriptionActivePrototypeScreen(
    renewalDate: String = "24 августа 2026",
    productTitle: String = "Годовой план",
    isLifetime: Boolean = false,
    autoRenewEnabled: Boolean? = true,
    isCancelling: Boolean = false,
    onCancelAutoRenew: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = colors.onPrimary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Premium активирован",
                modifier = Modifier.padding(top = 24.dp),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Спасибо, что выбрали Scanner AI Premium",
                modifier = Modifier.padding(top = 8.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.primaryContainer,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = if (isLifetime) "Ваша покупка" else "Ваша подписка",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Premium · $productTitle",
                        modifier = Modifier.padding(top = 8.dp),
                        color = colors.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isLifetime) {
                            "Доступ предоставлен навсегда"
                        } else {
                            "Действует до $renewalDate"
                        },
                        modifier = Modifier.padding(top = 18.dp),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (isLifetime) {
                            "Единоразовая покупка"
                        } else if (autoRenewEnabled == true) {
                            "Автопродление включено"
                        } else if (autoRenewEnabled == false) {
                            "Автопродление отключено"
                        } else {
                            "Статус автопродления уточняется"
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!isLifetime && autoRenewEnabled == true) {
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    enabled = !isCancelling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                ) {
                    Text("Отменить автопродление", fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                text = if (isLifetime) {
                    "Premium останется доступен навсегда"
                } else {
                    "Premium останется доступен до $renewalDate"
                },
                modifier = Modifier.padding(top = 12.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (!isLifetime && showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = {
                Text("Отменить автопродление?")
            },
            text = {
                Text(
                    "Подписка останется активной до $renewalDate. После этого новый платёж не спишется.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        onCancelAutoRenew()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text("Отменить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelDialog = false }) {
                    Text("Оставить")
                }
            },
        )
    }
}

@Preview(
    name = "Подписка активна · светлая",
    showBackground = true,
    backgroundColor = 0xFFFDFBFF,
    widthDp = 412,
    heightDp = 840,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun PremiumSubscriptionActiveLightPreview() {
    ScannerTheme(ThemeMode.LIGHT) {
        PremiumSubscriptionActivePrototypeScreen()
    }
}

@Preview(
    name = "Подписка активна · тёмная",
    showBackground = true,
    backgroundColor = 0xFF121317,
    widthDp = 412,
    heightDp = 840,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PremiumSubscriptionActiveDarkPreview() {
    ScannerTheme(ThemeMode.DARK) {
        PremiumSubscriptionActivePrototypeScreen()
    }
}

@Preview(
    name = "Premium навсегда · тёмная",
    showBackground = true,
    backgroundColor = 0xFF121317,
    widthDp = 412,
    heightDp = 840,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PremiumLifetimeActiveDarkPreview() {
    ScannerTheme(ThemeMode.DARK) {
        PremiumSubscriptionActivePrototypeScreen(
            productTitle = "Навсегда",
            isLifetime = true,
            autoRenewEnabled = null,
        )
    }
}
