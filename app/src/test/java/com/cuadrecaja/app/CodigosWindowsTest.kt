package com.cuadrecaja.app

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import android.graphics.BitmapFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lee con ZXing los QR y códigos de barras que dibuja el programa de Windows (cuadre-nota.cs),
 * para comprobar que cualquier lector los entiende. Las imágenes las genera la prueba con Mono;
 * si no están, la prueba se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CodigosWindowsTest {

    private val dir = File(System.getProperty("capturas.dir") ?: "build/capturas")

    private fun leer(nombre: String): String {
        val f = File(dir, nombre)
        assumeTrue("falta $nombre (se genera con la prueba de Mono)", f.exists())
        val img = BitmapFactory.decodeFile(f.path)
        val px = IntArray(img.width * img.height)
        img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(img.width, img.height, px)))
        return MultiFormatReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
    }

    @Test
    fun qrDeTodosLosNivelesSeLeen() {
        for (nivel in 0..3) {
            assertEquals("Tienda Doña Rosa / Carlos Pérez - https://ejemplo.com/entrega/12345", leer("qr_cs_$nivel.png"))
        }
    }

    @Test
    fun code128SeLee() {
        assertEquals("ENTREGA-24/10/2026", leer("code128_cs_ENTREGA-24_10_2026.png"))
        assertEquals("12345678", leer("code128_cs_12345678.png"))
        assertEquals("Nandu 7", leer("code128_cs_tildes.png"))
    }
}
