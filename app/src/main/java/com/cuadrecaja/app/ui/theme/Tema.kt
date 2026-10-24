package com.cuadrecaja.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.Modo

/** Colores de cada modo: verde para el cuadre, índigo para la impresión. */
@Immutable
data class Paleta(
    val principal: Color,
    val oscuro: Color,
    val suave: Color,
    val fondo: Color,
    val acento: Color,
) {
    val degradado get() = Brush.linearGradient(listOf(principal, oscuro))
}

val PaletaCuadre = Paleta(
    principal = Color(0xFF0E9F6E),
    oscuro = Color(0xFF046C4E),
    suave = Color(0xFFDEF7EC),
    fondo = Color(0xFFF3F7F5),
    acento = Color(0xFFF59E0B),
)

val PaletaImpresion = Paleta(
    principal = Color(0xFF6366F1),
    oscuro = Color(0xFF4338CA),
    suave = Color(0xFFE0E7FF),
    fondo = Color(0xFFF4F5FB),
    acento = Color(0xFFEC4899),
)

val LocalPaleta = staticCompositionLocalOf { PaletaCuadre }

private val Tipografia = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val Formas = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun TemaApp(modo: Modo, content: @Composable () -> Unit) {
    val destino = if (modo == Modo.CUADRE) PaletaCuadre else PaletaImpresion
    val anim = tween<Color>(durationMillis = 450)
    val principal by animateColorAsState(destino.principal, anim, label = "principal")
    val oscuro by animateColorAsState(destino.oscuro, anim, label = "oscuro")
    val suave by animateColorAsState(destino.suave, anim, label = "suave")
    val fondo by animateColorAsState(destino.fondo, anim, label = "fondo")
    val acento by animateColorAsState(destino.acento, anim, label = "acento")
    val paleta = Paleta(principal, oscuro, suave, fondo, acento)

    val esquema = lightColorScheme(
        primary = principal,
        onPrimary = Color.White,
        primaryContainer = suave,
        onPrimaryContainer = oscuro,
        secondary = oscuro,
        secondaryContainer = suave,
        onSecondaryContainer = oscuro,
        tertiary = acento,
        background = fondo,
        surface = Color.White,
        surfaceVariant = Color(0xFFF1F3F5),
        surfaceContainerLow = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color(0xFFF1F3F5),
        outline = Color(0xFFD5DAE0),
        outlineVariant = Color(0xFFE6E9ED),
    )
    CompositionLocalProvider(LocalPaleta provides paleta) {
        MaterialTheme(colorScheme = esquema, typography = Tipografia, shapes = Formas, content = content)
    }
}
