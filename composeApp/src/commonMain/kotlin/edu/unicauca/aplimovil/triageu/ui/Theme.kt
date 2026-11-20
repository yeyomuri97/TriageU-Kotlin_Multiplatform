package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TriageGreen = Color(0xFF4F7A68)
val TriageGreenDark = Color(0xFF3F6B59)
val SoftGreen = Color(0xFFEAF4EE)
val BackgroundGreen = Color(0xFFF8FBF9)
val BorderGreen = Color(0xFFDDE5E0)
val TextSecondary = Color(0xFF667085)
val ErrorRed = Color(0xFFD92D20)

fun colorFromHex(hex: String): Color {
    var h = hex.filter { it.isLetterOrDigit() }
    if (h.length != 6) h = "4F7A68"
    val v = h.toLongOrNull(16) ?: 0x4F7A68L
    return Color(
        red = ((v shr 16) and 0xFF) / 255f,
        green = ((v shr 8) and 0xFF) / 255f,
        blue = (v and 0xFF) / 255f
    )
}

@Composable
fun TriageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TriageGreen,
            onPrimary = Color.White,
            secondary = TriageGreenDark,
            background = BackgroundGreen,
            surface = Color.White,
            onSurface = Color(0xFF101828),
            outline = BorderGreen,
            error = ErrorRed
        ),
        content = content
    )
}
