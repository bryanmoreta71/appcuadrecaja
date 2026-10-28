package com.cuadrecaja.app.impresion

import android.graphics.Bitmap
import android.graphics.Color
import com.cuadrecaja.app.plantillas.TipoBarras
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** Códigos de barras para mostrar en pantalla (y que el escáner los lea). */
object CodigoBarras {

    fun formato(t: TipoBarras): BarcodeFormat = when (t) {
        TipoBarras.CODE128 -> BarcodeFormat.CODE_128
        TipoBarras.CODE39 -> BarcodeFormat.CODE_39
        TipoBarras.CODE93 -> BarcodeFormat.CODE_93
        TipoBarras.EAN13 -> BarcodeFormat.EAN_13
        TipoBarras.EAN8 -> BarcodeFormat.EAN_8
        TipoBarras.UPCA -> BarcodeFormat.UPC_A
        TipoBarras.ITF -> BarcodeFormat.ITF
        TipoBarras.CODABAR -> BarcodeFormat.CODABAR
    }

    /** El tipo de nuestra app para un formato leído con la cámara (null si no lo manejamos). */
    fun tipoDeFormato(nombre: String?): TipoBarras? = when (nombre) {
        "CODE_128" -> TipoBarras.CODE128
        "CODE_39" -> TipoBarras.CODE39
        "CODE_93" -> TipoBarras.CODE93
        "EAN_13" -> TipoBarras.EAN13
        "EAN_8" -> TipoBarras.EAN8
        "UPC_A" -> TipoBarras.UPCA
        "ITF" -> TipoBarras.ITF
        "CODABAR" -> TipoBarras.CODABAR
        else -> null
    }

    /**
     * Dibuja el código con barras de ancho entero (nítidas) y margen blanco a los lados,
     * de unos [anchoPx] de ancho. null si el contenido no vale para ese tipo.
     */
    fun dibujar(contenido: String, tipo: TipoBarras, anchoPx: Int, altoPx: Int): Bitmap? {
        if (contenido.isBlank()) return null
        val m = try {
            MultiFormatWriter().encode(contenido.trim(), formato(tipo), 1, 1, mapOf(EncodeHintType.MARGIN to 0))
        } catch (e: Exception) {
            return null
        }
        val margen = 10 // módulos blancos a cada lado: el escáner los necesita
        val total = m.width + margen * 2
        val modulo = (anchoPx / total).coerceAtLeast(1)
        val w = total * modulo
        val px = IntArray(w * altoPx) { Color.WHITE }
        for (x in 0 until m.width) {
            if (!m.get(x, 0)) continue
            val x0 = (margen + x) * modulo
            for (y in 0 until altoPx) for (k in 0 until modulo) px[y * w + x0 + k] = Color.BLACK
        }
        return Bitmap.createBitmap(px, w, altoPx, Bitmap.Config.ARGB_8888)
    }
}
