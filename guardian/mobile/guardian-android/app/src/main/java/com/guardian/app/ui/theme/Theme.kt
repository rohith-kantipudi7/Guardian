package com.guardian.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Command-center palette, matched to the web dashboard.
val Ok = Color(0xFF22C55E)
val Bad = Color(0xFFF43F5E)
val Warn = Color(0xFFF59E0B)
val Critical = Color(0xFFF43F5E)
val Info = Color(0xFF10B981)
val Violet = Color(0xFFA78BFA)
val Muted = Color(0xFF94A3B8)

val Bg = Color(0xFF06090F)
val Surface1 = Color(0xFF0E1420)
val Surface2 = Color(0xFF151D2C)
val Line = Color(0xFF233044)

// Brand gradient stops (cyan -> blue -> indigo) for hero surfaces and the brand mark.
val BrandCyan = Color(0xFF10B981)
val BrandBlue = Color(0xFF059669)
val BrandIndigo = Color(0xFF34D399)

val BrandBrush = Brush.linearGradient(listOf(BrandCyan, BrandIndigo))
val DangerBrush = Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48)))

private val Scheme = darkColorScheme(
    primary = Info,
    onPrimary = Color(0xFF06251A),
    secondary = Violet,
    error = Critical,
    onError = Color(0xFF1A0308),
    background = Bg,
    onBackground = Color(0xFFF1F5F9),
    surface = Surface1,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    outline = Line,
    outlineVariant = Line,
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
)

private val Shape = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = Shape, content = content)

