package com.cuadrecaja.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Íconos de línea para el editor (dibujados, sin depender de fuentes de emoji). */
enum class Icono {
    TEXTO, TITULO, CAMPO, CELDA, PARRAFO, TABLA, LINEA, ESPACIO, IMAGEN, QR, BARRAS, FIRMA, FECHA, HORA,
    DESHACER, REHACER, LIMPIAR, COPIAR, SUBIR, BAJAR, BORRAR, CERRAR, PAPEL, IMPRESORA, LAPIZ,
}

@Composable
fun IconoLinea(icono: Icono, color: Color, modifier: Modifier = Modifier, tamano: Dp = 28.dp) {
    Canvas(modifier.size(tamano)) {
        val g = size.minDimension / 24f // se dibuja en una cuadrícula de 24 x 24
        val trazo = Stroke(width = 1.8f * g, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * g, y * g)
        fun linea(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = trazo.width, cap = StrokeCap.Round)
        fun caja(x: Float, y: Float, w: Float, h: Float, r: Float = 3f, estilo: Stroke = trazo) =
            drawRoundRect(color, p(x, y), Size(w * g, h * g), CornerRadius(r * g), style = estilo)
        fun lleno(x: Float, y: Float, w: Float, h: Float, r: Float = 0.8f) =
            drawRoundRect(color, p(x, y), Size(w * g, h * g), CornerRadius(r * g))
        fun camino(vararg puntos: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(puntos[0].first * g, puntos[0].second * g)
                puntos.drop(1).forEach { lineTo(it.first * g, it.second * g) }
            }
            drawPath(path, color, style = trazo)
        }

        when (icono) {
            Icono.TEXTO -> { caja(3f, 3f, 18f, 18f); linea(8f, 8f, 16f, 8f); linea(12f, 8f, 12f, 17f) }
            Icono.TITULO -> { lleno(3f, 5f, 18f, 14f, 2.5f); drawLine(Color.White, p(8f, 9f), p(16f, 9f), trazo.width, StrokeCap.Round); drawLine(Color.White, p(12f, 9f), p(12f, 16f), trazo.width, StrokeCap.Round) }
            Icono.CAMPO -> { caja(2.5f, 7f, 19f, 10f, 2.5f); linea(7f, 9.5f, 7f, 14.5f); linea(10f, 14.5f, 18f, 14.5f) }
            Icono.CELDA -> {
                caja(3f, 4f, 18f, 16f, 3f, Stroke(trazo.width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * g, 2.5f * g))))
                linea(7f, 9f, 12f, 9f); linea(7f, 14f, 17f, 14f)
            }
            Icono.PARRAFO -> { linea(4f, 6f, 20f, 6f); linea(4f, 10f, 20f, 10f); linea(4f, 14f, 20f, 14f); linea(4f, 18f, 13f, 18f) }
            Icono.TABLA -> { caja(3f, 4f, 18f, 16f); linea(3f, 9.5f, 21f, 9.5f); linea(3f, 15f, 21f, 15f); linea(9f, 4f, 9f, 20f); linea(15f, 4f, 15f, 20f) }
            Icono.LINEA -> linea(4f, 4f, 20f, 20f)
            Icono.ESPACIO -> { linea(4f, 3.5f, 20f, 3.5f); linea(4f, 20.5f, 20f, 20.5f); linea(12f, 7f, 12f, 17f); camino(9f to 9.5f, 12f to 6.5f, 15f to 9.5f); camino(9f to 14.5f, 12f to 17.5f, 15f to 14.5f) }
            Icono.IMAGEN -> {
                caja(3f, 4f, 18f, 16f)
                drawCircle(color, 1.8f * g, p(8.5f, 9f), style = trazo)
                camino(3.5f to 18f, 9.5f to 12.5f, 13f to 16f, 15.5f to 13.5f, 20.5f to 18f)
            }
            Icono.QR -> {
                caja(3f, 3f, 7f, 7f, 1.5f); caja(14f, 3f, 7f, 7f, 1.5f); caja(3f, 14f, 7f, 7f, 1.5f)
                lleno(14f, 14f, 3f, 3f); lleno(18f, 18f, 3f, 3f); lleno(18f, 14f, 3f, 2f); lleno(14f, 19f, 2.5f, 2f)
            }
            Icono.BARRAS -> {
                val barras = listOf(3f to 1.6f, 6f to 2.8f, 10f to 1.2f, 12.5f to 2.4f, 16f to 1.2f, 18.5f to 2.6f)
                barras.forEach { (x, w) -> lleno(x, 4f, w, 16f, 0.4f) }
            }
            Icono.FIRMA -> {
                val path = Path().apply {
                    moveTo(3f * g, 15f * g)
                    cubicTo(6f * g, 4f * g, 9f * g, 4f * g, 8f * g, 13f * g)
                    cubicTo(7.5f * g, 17f * g, 11f * g, 9f * g, 13f * g, 12f * g)
                    cubicTo(14.5f * g, 14f * g, 17f * g, 10f * g, 21f * g, 12f * g)
                }
                drawPath(path, color, style = trazo)
                linea(3f, 20f, 21f, 20f)
            }
            Icono.FECHA -> { caja(3f, 5f, 18f, 16f); linea(3f, 10f, 21f, 10f); linea(8f, 3f, 8f, 7f); linea(16f, 3f, 16f, 7f); lleno(7f, 13f, 3f, 3f); lleno(13f, 13f, 3f, 3f) }
            Icono.HORA -> { drawCircle(color, 9f * g, p(12f, 12f), style = trazo); camino(12f to 7f, 12f to 12f, 15.5f to 14f) }
            Icono.DESHACER -> flecha(this, g, trazo, color, izquierda = true)
            Icono.REHACER -> flecha(this, g, trazo, color, izquierda = false)
            Icono.LIMPIAR -> {
                linea(15f, 3f, 12.5f, 10f)
                caja(5.5f, 10f, 13f, 4f, 1.5f)
                camino(6.5f to 14f, 5f to 21f, 19f to 21f, 17.5f to 14f)
                linea(9.5f, 17f, 9f, 21f); linea(14.5f, 17f, 15f, 21f)
            }
            Icono.COPIAR -> { caja(8f, 8f, 13f, 13f); camino(5f to 16f, 3f to 16f, 3f to 3f, 16f to 3f, 16f to 5f) }
            Icono.SUBIR -> { linea(12f, 20f, 12f, 5f); camino(6f to 11f, 12f to 5f, 18f to 11f) }
            Icono.BAJAR -> { linea(12f, 4f, 12f, 19f); camino(6f to 13f, 12f to 19f, 18f to 13f) }
            Icono.BORRAR -> { linea(4f, 6.5f, 20f, 6.5f); camino(9f to 6.5f, 9.5f to 3.5f, 14.5f to 3.5f, 15f to 6.5f); camino(6f to 6.5f, 7f to 20.5f, 17f to 20.5f, 18f to 6.5f); linea(10.5f, 10f, 10.5f, 17f); linea(13.5f, 10f, 13.5f, 17f) }
            Icono.CERRAR -> { linea(6f, 6f, 18f, 18f); linea(18f, 6f, 6f, 18f) }
            Icono.IMPRESORA -> {
                camino(7f to 9f, 7f to 3f, 17f to 3f, 17f to 9f)
                caja(3f, 9f, 18f, 8f, 2f)
                caja(7f, 14f, 10f, 7f, 1f)
                lleno(17f, 11.5f, 1.8f, 1.5f)
            }
            Icono.LAPIZ -> {
                camino(4f to 20f, 5f to 15.5f, 15.5f to 5f, 19f to 8.5f, 8.5f to 19f, 4f to 20f)
                linea(13f, 7.5f, 16.5f, 11f)
            }
            Icono.PAPEL -> { camino(6f to 2.5f, 18f to 2.5f, 18f to 21.5f, 15f to 19.5f, 12f to 21.5f, 9f to 19.5f, 6f to 21.5f, 6f to 2.5f); linea(9f, 8f, 15f, 8f); linea(9f, 12f, 15f, 12f) }
        }
    }
}

private fun flecha(d: DrawScope, g: Float, trazo: Stroke, color: Color, izquierda: Boolean) {
    val path = Path()
    if (izquierda) {
        path.moveTo(9f * g, 6f * g); path.lineTo(4f * g, 10f * g); path.lineTo(9f * g, 14f * g)
        path.moveTo(4f * g, 10f * g); path.lineTo(14f * g, 10f * g)
        path.cubicTo(22f * g, 10f * g, 22f * g, 20f * g, 14f * g, 20f * g); path.lineTo(8f * g, 20f * g)
    } else {
        path.moveTo(15f * g, 6f * g); path.lineTo(20f * g, 10f * g); path.lineTo(15f * g, 14f * g)
        path.moveTo(20f * g, 10f * g); path.lineTo(10f * g, 10f * g)
        path.cubicTo(2f * g, 10f * g, 2f * g, 20f * g, 10f * g, 20f * g); path.lineTo(16f * g, 20f * g)
    }
    d.drawPath(path, color, style = trazo)
}
