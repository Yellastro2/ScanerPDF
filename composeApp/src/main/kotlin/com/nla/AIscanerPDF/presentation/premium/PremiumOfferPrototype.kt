package com.nla.AIscanerPDF.presentation.premium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import com.nla.AIscanerPDF.presentation.theme.ScannerTheme

private val PremiumBlue = Color(0xFF1F5EFF)
private val PremiumCyan = Color(0xFF18BDE8)
private val PremiumSurface = Color(0xFFF1F4FA)
private val PremiumText = Color(0xFF222735)

/**
 * Изолированный макет paywall для просмотра в Compose Preview.
 *
 * Экран намеренно не связан с навигацией, RuStore Pay или [PremiumViewModel].
 */
@Composable
fun PremiumOfferPrototypeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        PremiumHero()
        PremiumFeatures()

        Text(
            text = "Восстановить покупки",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 28.dp)
                .clickable { },
            color = PremiumBlue,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumPlanCard(
                title = "1 месяц",
                price = "299 ₽ в месяц",
                priceCaption = "Регулярное продление",
            )
            PremiumPlanCard(
                title = "1 год",
                price = "1 490 ₽",
                priceCaption = "124 ₽ в месяц",
                oldPrice = "3 588 ₽",
                discount = "Скидка 58%",
                highlighted = true,
            )
            PremiumPlanCard(
                title = "Навсегда",
                price = "2 990 ₽",
                priceCaption = "Единоразовый платёж",
                oldPrice = "9 966 ₽",
                discount = "Скидка 70%",
            )

            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue),
            ) {
                Text(
                    text = "Подписаться",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Отмена подписки в любое время",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PremiumHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF7ADAF5), PremiumBlue, Color(0xFF173EAF)),
                    start = Offset.Zero,
                    end = Offset(900f, 700f),
                ),
            ),
    ) {
        DecorativeDocument(modifier = Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 18.dp).rotate(-14f))
        DecorativeDocument(modifier = Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 14.dp).rotate(11f))
        DecorativeDocument(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).rotate(5f))
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x990D2881)))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "БОЛЬШЕ ВОЗМОЖНОСТЕЙ",
                color = Color.White,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "с Scanner AI Premium",
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DecorativeDocument(modifier: Modifier = Modifier) {
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
                        color = PremiumBlue.copy(alpha = 0.23f),
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
private fun PremiumFeatures() {
    val features = listOf(
        "Интелектуальное самари" to "Полный анализ документа",
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
        features.forEachIndexed { index, feature ->
            FeatureCard(
                title = feature.first,
                subtitle = feature.second,
                accent = if (index == 1) PremiumCyan else PremiumBlue,
            )
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, accent: Color) {
    Box(
        modifier = Modifier
            .width(156.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.56f))))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .22f)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                drawCircle(Color.White.copy(alpha = .9f), radius = size.minDimension / 2)
                drawCircle(accent, radius = size.minDimension / 5)
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall)
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (highlighted) Color(0xFFEAF0FF) else PremiumSurface,
        shape = RoundedCornerShape(22.dp),
        border = if (highlighted) androidx.compose.foundation.BorderStroke(1.dp, PremiumBlue.copy(alpha = .25f)) else null,
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Column {
                Text(title, color = PremiumText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    price,
                    modifier = Modifier.padding(top = 4.dp),
                    color = if (highlighted) PremiumBlue else PremiumText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(priceCaption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                            .background(PremiumCyan)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                oldPrice?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDFBFF, heightDp = 900, widthDp = 412)
@Composable
private fun PremiumOfferPrototypePreview() {
    ScannerTheme {
        PremiumOfferPrototypeScreen()
    }
}
