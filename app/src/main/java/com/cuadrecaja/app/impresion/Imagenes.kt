package com.cuadrecaja.app.impresion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.cuadrecaja.app.plantillas.nuevoId
import java.io.File

/** Imágenes de las plantillas (logos, sellos...): se copian a la app y se pasan a blanco y negro. */
object Imagenes {

    fun carpeta(context: Context): File = File(context.filesDir, "imagenes").apply { mkdirs() }

    /** Copia la imagen elegida a la app, achicada al ancho máximo del papel. Devuelve el nombre del archivo. */
    fun guardar(context: Context, uri: Uri): String? {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val maximo = 576
        val imagen = if (original.width > maximo) {
            Bitmap.createScaledBitmap(original, maximo, (original.height * maximo / original.width.toFloat()).toInt(), true)
        } else original
        // Lo transparente se vuelve blanco (el papel).
        val conFondo = Bitmap.createBitmap(imagen.width, imagen.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(conFondo).apply { drawColor(Color.WHITE); drawBitmap(imagen, 0f, 0f, null) }
        val nombre = "${nuevoId()}.png"
        File(carpeta(context), nombre).outputStream().use { conFondo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return nombre
    }

    fun borrar(context: Context, nombre: String) {
        if (nombre.isNotBlank()) File(carpeta(context), nombre).delete()
    }

    fun luminancia(c: Int): Int = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    fun esNegro(c: Int): Boolean = Color.alpha(c) > 127 && luminancia(c) < 140

    /** Pasa una foto o logo a puntos blanco y negro con tramado (Floyd–Steinberg) para que se vean los grises. */
    fun tramar(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val gris = FloatArray(w * h) { i ->
            val c = px[i]
            if (Color.alpha(c) < 128) 255f else luminancia(c).toFloat()
        }
        tramarGrises(gris, w, h)
        for (i in px.indices) px[i] = if (gris[i] < 128f) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Tramado en sitio: al terminar cada valor es 0 (negro) o 255 (blanco). */
    fun tramarGrises(g: FloatArray, w: Int, h: Int) {
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val viejo = g[i]
            val nuevo = if (viejo < 128f) 0f else 255f
            g[i] = nuevo
            val err = viejo - nuevo
            if (x + 1 < w) g[i + 1] += err * 7 / 16
            if (y + 1 < h) {
                if (x > 0) g[i + w - 1] += err * 3 / 16
                g[i + w] += err * 5 / 16
                if (x + 1 < w) g[i + w + 1] += err * 1 / 16
            }
        }
    }
}
