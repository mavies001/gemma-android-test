package com.yourapp.gemmatest.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class NovaColorScheme(
    val Bg0: Color,
    val Bg1: Color,
    val Surface: Color,
    val Surface2: Color,
    val Line: Color,
    val Blue500: Color,
    val Blue300: Color,
    val Cyan: Color,
    val Text0: Color,
    val Text1: Color,
    val Text2: Color,
    val CodeBg: Color,
)

val DarkNova = NovaColorScheme(
    Bg0 = Color(0xFF060A16),
    Bg1 = Color(0xFF0B1226),
    Surface = Color(0xFF111A33),
    Surface2 = Color(0xFF152047),
    Line = Color(0xFF22305C),
    Blue500 = Color(0xFF3B6CF6),
    Blue300 = Color(0xFF8FB3FF),
    Cyan = Color(0xFF31D8E0),
    Text0 = Color(0xFFEEF2FF),
    Text1 = Color(0xFFAAB7E0),
    Text2 = Color(0xFF6F7DB0),
    CodeBg = Color(0xFF0B1226),
)

val LightNova = NovaColorScheme(
    Bg0 = Color(0xFFF6F7FB),
    Bg1 = Color(0xFFFFFFFF),
    Surface = Color(0xFFEFF1F8),
    Surface2 = Color(0xFFE3E7F5),
    Line = Color(0xFFD9DEEE),
    Blue500 = Color(0xFF3B6CF6),
    Blue300 = Color(0xFF3B6CF6),
    Cyan = Color(0xFF1AAEB8),
    Text0 = Color(0xFF15192B),
    Text1 = Color(0xFF454E6E),
    Text2 = Color(0xFF7A82A0),
    CodeBg = Color(0xFFEAECF6),
)

val LocalNovaColors = staticCompositionLocalOf { DarkNova }
