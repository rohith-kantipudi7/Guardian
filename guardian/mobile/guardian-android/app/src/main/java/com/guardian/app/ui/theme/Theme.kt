package com.guardian.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Claymorphism palette (white surfaces on light grey), matched to the web dashboard.
val Ok = Color(0xFF1E8E4E)
val Bad = Color(0xFFD92D4A)
val Warn = Color(0xFFB7791F)
val Critical = Color(0xFFD92D4A)
val Info = Color(0xFF2563EB)
val Violet = Color(0xFF52617A)
val Muted = Color(0xFF5B6B82)

val Bg = Color(0xFFEEF0F4)
val Surface1 = Color.White
val Surface2 = Color.White
val Line = Color(0xFFDDE1E8)
val ShadowDark = Color(0xFFB8C1D0)
val ShadowLight = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1F2937)

val BrandCyan = Info
val BrandBlue = Info
val BrandIndigo = Info

val BrandBrush: Brush = SolidColor(Info)
val DangerBrush: Brush = SolidColor(Critical)

private val Scheme = lightColorScheme(
    primary = Info,
    onPrimary = Color.White,
    secondary = Violet,
    error = Critical,
    onError = Color.White,
    background = Bg,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Color.White,
    onSurfaceVariant = Muted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
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
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = Shape, content = content)
