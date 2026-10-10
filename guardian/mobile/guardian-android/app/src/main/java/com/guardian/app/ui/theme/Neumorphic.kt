package com.guardian.app.ui.theme

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ClayShadow = Color(0x40463A8C)
private val ClayShade = Color(0xFFEFEBFA)

/** Clay surface: soft drop shadow below a white, puffy rounded body that darkens slightly toward the bottom edge. */
fun Modifier.neu(corner: Dp = 24.dp, depth: Dp = 6.dp, fill: Color = Color.White): Modifier = drawBehind {
    val r = corner.toPx()
    val d = depth.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = fill.toArgb() }
        paint.setShadowLayer(d * 2.2f, 0f, d * 1.4f, ClayShadow.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(fill, ClayShade), startY = size.height * 0.55f, endY = size.height),
        cornerRadius = CornerRadius(r, r),
        topLeft = Offset.Zero,
    )
}
