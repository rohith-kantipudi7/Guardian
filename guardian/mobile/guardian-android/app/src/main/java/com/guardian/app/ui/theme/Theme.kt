package com.guardian.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ok = Color(0xFF22C55E)
val Bad = Color(0xFFEF4444)
val Warn = Color(0xFFF59E0B)
val Critical = Color(0xFFEF4444)
val Info = Color(0xFF38BDF8)
val Violet = Color(0xFFA78BFA)
val Muted = Color(0xFF94A3B8)

val Bg = Color(0xFF0A0F1A)
val Surface1 = Color(0xFF111827)
val Surface2 = Color(0xFF1F2937)
val Line = Color(0xFF263244)

private val Scheme = darkColorScheme(
    primary = Info,
    onPrimary = Color(0xFF00121F),
    secondary = Violet,
    error = Critical,
    background = Bg,
    onBackground = Color(0xFFE5E7EB),
    surface = Surface1,
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    outline = Line,
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

private val Shape = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = Shape, content = content)
