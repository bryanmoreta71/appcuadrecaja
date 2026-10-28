package com.cuadrecaja.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import com.cuadrecaja.app.impresion.CodigoBarras
import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.stock.CodigoStock
import com.cuadrecaja.app.stock.StockViewModel
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h860dp-xhdpi")
class StockCapturasTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val dir = File(System.getProperty("capturas.dir") ?: "build/capturas").apply { mkdirs() }

    private fun pantalla(nombre: String) {
        repeat(3) { compose.mainClock.advanceTimeBy(300); compose.waitForIdle() }
        val v = compose.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(dir, "$nombre.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun leer(b: Bitmap): Pair<String, String> {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val r = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(b.width, b.height, px))), mapOf(DecodeHintType.TRY_HARDER to true))
        return r.text to r.barcodeFormat.name
    }

    @Test
    fun codigosEnPantallaSeLeen() {
        assertEquals("4006381333931" to "EAN_13", leer(CodigoBarras.dibujar("4006381333931", TipoBarras.EAN13, 700, 160)!!))
        assertEquals("SAL-750-RM" to "CODE_128", leer(CodigoBarras.dibujar("SAL-750-RM", TipoBarras.CODE128, 700, 160)!!))
        assertEquals("036000291452" to "UPC_A", leer(CodigoBarras.dibujar("036000291452", TipoBarras.UPCA, 700, 160)!!))
        assertEquals(null, CodigoBarras.dibujar("abc", TipoBarras.EAN13, 700, 160))
        assertEquals(TipoBarras.EAN13, CodigoBarras.tipoDeFormato("EAN_13"))
        // La lista impresa: cada código con su nombre y bien separado, y que se lea.
        val lista = com.cuadrecaja.app.stock.ListaStock(
            nombre = "Traslado vinos",
            codigos = listOf(CodigoStock(nombre = "Salentein Reserva Merlot 750 ml", codigo = "4006381333931"), CodigoStock(nombre = "Tocornal Merlot", codigo = "SAL-750-RM"), CodigoStock(nombre = "Silk Spice Tinto", codigo = "036000291452", tipo = TipoBarras.UPCA)),
        )
        val r = com.cuadrecaja.app.impresion.Renderizador(compose.activity).dibujar(com.cuadrecaja.app.stock.plantillaImpresion(lista), com.cuadrecaja.app.plantillas.Datos(), 576)
        assertEquals(emptyList<String>(), r.avisos)
        File(dir, "s7_lista_impresa.png").outputStream().use { r.imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Agregar un código y después editar el anterior no debe borrar el nuevo. */
    @Test
    fun editarElAnteriorNoBorraElNuevo() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[StockViewModel::class.java]
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("🏷️   Sacar stock").performClick()
        compose.onNodeWithText("+  Nueva lista").performClick()
        compose.onNodeWithText("+  Escribir").performClick()
        compose.onAllNodesWithText("Nombre")[0].performTextInput("Primero")
        compose.onAllNodesWithText("Código")[0].performTextInput("111")
        compose.onNodeWithText("+  Escribir").performScrollTo().performClick()
        compose.onAllNodesWithText("Código")[1].performTextInput("222")
        // Ahora se corrige el primero.
        compose.onNodeWithText("Primero").performTextInput(" corregido")
        compose.waitForIdle()
        val codigos = vm.listas.last().codigos
        assertEquals(listOf("111", "222"), codigos.map { it.codigo })
        assertEquals("Primero corregido", codigos[0].nombre)
    }

    @Test
    fun crearListaYSacarStock() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[StockViewModel::class.java]
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("🏷️   Sacar stock").performClick()
        pantalla("s1_listas_vacia")
        compose.onNodeWithText("+  Nueva lista").performClick()
        compose.onNodeWithText("+  Escribir").performClick()
        compose.onAllNodesWithText("Nombre")[0].performTextInput("Salentein Reserva Merlot 750 ml")
        compose.onAllNodesWithText("Código")[0].performTextInput("4006381333931")
        // Los demás como si vinieran del escáner.
        compose.runOnUiThread {
            val l = vm.listas.last()
            vm.guardar(l.copy(nombre = "Traslado vinos", codigos = l.codigos + CodigoStock(nombre = "Tocornal Merlot", codigo = "SAL-750-RM") + CodigoStock(nombre = "Silk Spice Tinto", codigo = "036000291452", tipo = TipoBarras.UPCA)))
        }
        pantalla("s2_editor")
        compose.onNodeWithText("▶  Empezar a sacar stock").performClick()
        compose.onNodeWithText("1 de 3").assertExists()
        pantalla("s3_secuencia_1")
        compose.onNodeWithText("Siguiente  ▶").performClick()
        compose.onNodeWithText("2 de 3").assertExists()
        pantalla("s4_secuencia_2")
        // Botón de volumen abajo = siguiente; arriba = anterior.
        compose.runOnUiThread { compose.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)) }
        compose.onNodeWithText("3 de 3").assertExists()
        compose.onNodeWithText("✓  Terminar").assertExists()
        pantalla("s5_secuencia_ultima")
        // Brillo a mano y fondo negro.
        compose.onNodeWithContentDescription("Brillo y fondo").performClick()
        compose.onAllNodes(androidx.compose.ui.test.isToggleable())[0].performClick() // apagar brillo automático
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.isToggleable())[1].performClick() // fondo negro
        compose.waitForIdle()
        assertEquals(true, vm.brilloManual)
        assertEquals(true, vm.fondoNegro)
        assertEquals(vm.brillo, compose.activity.window.attributes.screenBrightness, 0.001f)
        pantalla("s5b_fondo_negro_brillo")
        compose.runOnUiThread { compose.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) }
        compose.onNodeWithText("2 de 3").assertExists()
        compose.onNodeWithText("◀  Anterior").performClick()
        compose.onNodeWithText("1 de 3").assertExists()
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        pantalla("s6_listas")
        compose.onNodeWithText("3 códigos").assertExists()
    }
}
