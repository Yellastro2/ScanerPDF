package com.nla.AIscanerPDF.presentation.premium

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nla.AIscanerPDF.domain.model.ThemeMode
import com.nla.AIscanerPDF.domain.model.SubscriptionPeriod
import com.nla.AIscanerPDF.domain.model.SubscriptionProduct
import com.nla.AIscanerPDF.presentation.theme.ScannerTheme

/** Paywall, который отображает переданный каталог товаров RuStore. */
@Composable
fun PremiumOfferPrototypeScreen(
    products: List<SubscriptionProduct> = previewProducts,
    isPurchasing: Boolean = false,
    isRestoring: Boolean = false,
    onPurchase: (String) -> Unit = {},
    onRestore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    var selectedProductId by remember(products) { mutableStateOf(products.firstOrNull()?.productId) }
    val selectedProduct = products.firstOrNull { it.productId == selectedProductId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        PremiumHero(
            primary = colors.primary,
            primaryContainer = colors.primaryContainer,
            onPrimary = colors.onPrimary,
            onSurface = colors.onSurface,
            isDarkTheme = isDarkTheme,
        )
        PremiumFeatures(colors.primary, colors.onPrimary)

        Text(
            text = if (isRestoring) "Восстанавливаем…" else "Восстановить покупки",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 28.dp)
                .clickable(enabled = !isRestoring, onClick = onRestore),
            color = colors.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            products.forEach { product ->
                PremiumPlanCard(
                    title = if (product.period == SubscriptionPeriod.ONE_TIME) {
                        "Навсегда"
                    } else {
                        product.title
                    },
                    price = product.price,
                    priceCaption = if (product.period == SubscriptionPeriod.ONE_TIME) {
                        "Единоразовая покупка · доступ навсегда"
                    } else {
                        "Автопродление по окончании периода"
                    },
                    highlighted = selectedProductId == product.productId,
                    colors = colors,
                    isDarkTheme = isDarkTheme,
                    onClick = { selectedProductId = product.productId },
                )
            }

            Button(
                onClick = { selectedProductId?.let(onPurchase) },
                enabled = selectedProductId != null && !isPurchasing && !isRestoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(
                    text = if (selectedProduct?.period == SubscriptionPeriod.ONE_TIME) {
                        "Купить навсегда"
                    } else {
                        "Подписаться"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (selectedProduct?.period == SubscriptionPeriod.ONE_TIME) {
                    "Один платёж без автопродления"
                } else {
                    "Отмена подписки в любое время"
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PremiumHero(
    primary: Color,
    primaryContainer: Color,
    onPrimary: Color,
    onSurface: Color,
    isDarkTheme: Boolean,
) {
    val heroColor = if (isDarkTheme) primaryContainer else primary
    val heroContentColor = if (isDarkTheme) onSurface else onPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(heroColor),
    ) {
        DecorativeDocument(
            primary = primary,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 18.dp).rotate(-14f),
        )
        DecorativeDocument(
            primary = primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 14.dp).rotate(11f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.26f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "БОЛЬШЕ ВОЗМОЖНОСТЕЙ",
                color = heroContentColor,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "с Scanner AI Premium",
                modifier = Modifier.padding(top = 8.dp),
                color = heroContentColor.copy(alpha = 0.90f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DecorativeDocument(primary: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 118.dp, height = 158.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            rotate(-7f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.72f),
                    topLeft = Offset(size.width * .15f, 0f),
                    size = Size(size.width * .7f, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                )
                repeat(5) { line ->
                    drawRoundRect(
                        color = primary.copy(alpha = 0.23f),
                        topLeft = Offset(size.width * .27f, size.height * (.18f + line * .12f)),
                        size = Size(size.width * .46f, 5.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumFeatures(primary: Color, onPrimary: Color) {
    val features = listOf(
        "Умное самари" to "Полный анализ документа",
        "OCR без лимитов" to "Распознавайте всё",
        "AI-реквизиты" to "Извлекает главное",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        features.forEach { feature ->
            FeatureCard(
                title = feature.first,
                subtitle = feature.second,
                primary = primary,
                onPrimary = onPrimary,
            )
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, primary: Color, onPrimary: Color) {
    Box(
        modifier = Modifier
            .width(156.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.72f))))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(onPrimary.copy(alpha = .18f))
                .align(Alignment.TopEnd),
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                drawCircle(onPrimary.copy(alpha = .9f), radius = size.minDimension / 2)
                drawCircle(primary, radius = size.minDimension / 5)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 50.dp)
                .align(Alignment.TopStart),
        ) {
            Text(title, color = onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = onPrimary.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PremiumPlanCard(
    title: String,
    price: String,
    priceCaption: String,
    oldPrice: String? = null,
    discount: String? = null,
    highlighted: Boolean = false,
    colors: androidx.compose.material3.ColorScheme,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (highlighted) {
            colors.primaryContainer
        } else {
            colors.primaryContainer.copy(alpha = if (isDarkTheme) 0.46f else 0.38f)
        },
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.primary.copy(alpha = if (highlighted) .32f else .12f),
        ),
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Column {
                Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    price,
                    modifier = Modifier.padding(top = 4.dp),
                    color = colors.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(priceCaption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.End,
            ) {
                discount?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = colors.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                oldPrice?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 8.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
        }
    }
}

private val previewProducts = listOf(
    SubscriptionProduct("premium_monthly", "1 месяц", "299 ₽ в месяц", SubscriptionPeriod.MONTHLY),
    SubscriptionProduct("premium_yearly", "1 год", "1 490 ₽", SubscriptionPeriod.YEARLY),
    SubscriptionProduct("premium_forever", "Навсегда", "2 990 ₽", SubscriptionPeriod.ONE_TIME),
)

@Preview(
    name = "Светлая тема",
    showBackground = true,
    backgroundColor = 0xFFFDFBFF,
    heightDp = 900,
    widthDp = 412,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun PremiumOfferPrototypeLightPreview() {
    ScannerTheme(ThemeMode.LIGHT) {
        PremiumOfferPrototypeScreen()
    }
}

@Preview(
    name = "Тёмная тема",
    showBackground = true,
    backgroundColor = 0xFF121317,
    heightDp = 900,
    widthDp = 412,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PremiumOfferPrototypeDarkPreview() {
    ScannerTheme(ThemeMode.DARK) {
        PremiumOfferPrototypeScreen()
    }
}
