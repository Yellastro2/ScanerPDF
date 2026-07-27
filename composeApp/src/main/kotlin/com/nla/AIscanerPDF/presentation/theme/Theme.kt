package com.nla.AIscanerPDF.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nla.AIscanerPDF.domain.model.ThemeMode

private val Blue = Color(0xFF1F5EFF)
private val BlueDark = Color(0xFFADC6FF)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF585E71),
    surface = Color(0xFFFDFBFF),
    background = Color(0xFFFDFBFF),
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF00297A),
    primaryContainer = Color(0xFF0041C4),
    secondary = Color(0xFFC1C6DD),
    surface = Color(0xFF121317),
    background = Color(0xFF121317),
)

@Composable
fun ScannerTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
