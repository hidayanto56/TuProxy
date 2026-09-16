package com.example.tuproxy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palet dark elegan yang agak terang: slate lembut, teks kontras tinggi. */
private val TuProxyDark = darkColorScheme(
    primary = Color(0xFF4CC38A),
    onPrimary = Color(0xFF04120A),
    secondary = Color(0xFF7AA2F7),
    onSecondary = Color(0xFF0A1120),
    background = Color(0xFF1A2230),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF222D3F),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF2C3A50),
    onSurfaceVariant = Color(0xFFC3CEDB),
    surfaceContainer = Color(0xFF222D3F),
    error = Color(0xFFF87171),
    onError = Color(0xFF1A0505),
    outline = Color(0xFF43536B),
    outlineVariant = Color(0xFF334052),
)

@Composable
fun TuProxyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TuProxyDark, content = content)
}
