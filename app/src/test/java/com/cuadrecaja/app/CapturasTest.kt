package com.cuadrecaja.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import com.cuadrecaja.app.impresion.EscPos
import com.cuadrecaja.app.impresion.Imagenes
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Alineacion
import com.cuadrecaja.app.plantillas.Borde
import com.cuadrecaja.app.plantillas.Columna
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.DensidadQr
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Estilo
import com.cuadrecaja.app.plantillas.FormaCampo
import com.cuadrecaja.app.plantillas.FormaQr
import com.cuadrecaja.app.plantillas.Grosor
import com.cuadrecaja.app.plantillas.Letra
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.PlantillasPorDefecto
import com.cuadrecaja.app.plantillas.TipoBarras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Dibuja los tickets y las pantallas sin teléfono (Robolectric) y guarda imágenes en
 * build/capturas para revisarlas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h860dp-xhdpi")
class CapturasTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val dir = File(System.getProperty("capturas.dir") ?: "build/capturas").apply { mkdirs() }
    private val fecha = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 24, 15, 30) }.time

    private fun guardar(nombre: String, b: Bitmap) {
        File(dir, "$nombre.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Hojas inferiores y diálogos se dibujan en otra ventana. */
    private fun ventana(nombre: String) {
        compose.waitForIdle()
        val vista = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val b = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(b))
        guardar(nombre, b)
    }

    private fun pantalla(nombre: String) {
        compose.waitForIdle()
        val vista = compose.activity.window.decorView
        val b = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(b))
        guardar(nombre, b)
    }

    @Test
    fun ticketsDibujados() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Logo de prueba: degradado con un círculo, para ver el tramado.
        val logo = Bitmap.createBitmap(300, 120, Bitmap.Config.ARGB_8888)
        Canvas(logo).apply {
            for (x in 0 until 300) drawRect(x.toFloat(), 0f, x + 1f, 120f, android.graphics.Paint().apply { color = Color.rgb(x * 255 / 300, x * 255 / 300, 255) })
            drawCircle(60f, 60f, 45f, android.graphics.Paint().apply { color = Color.BLACK })
        }
        File(Imagenes.carpeta(ctx), "logo.png").outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val nota = PlantillasPorDefecto.notaDeEntrega()
        val datos = Datos(
            campos = mapOf(nota.campos[2].id to "Tienda Doña Rosa", nota.campos[3].id to "Carlos Pérez"),
            filas = mapOf(nota.tablas[0].id to listOf(listOf("12", "Agua 500 ml"), listOf("3", "Arroz 5 lb (saco grande con descripción larga)"), listOf("1", "Aceite"))),
        )
        val r1 = Renderizador(ctx).dibujar(nota, datos, 576, fecha)
        guardar("ticket_nota_80mm", r1.imagen)
        assertTrue(r1.avisos.toString(), r1.avisos.isEmpty())
        assertEquals(576, r1.imagen.width)

        val todo = Plantilla(
            nombre = "Todo",
            elementos = listOf(
                Elemento.Imagen(archivo = "logo.png", anchoPorcentaje = 70),
                Elemento.Texto(texto = "Distribuidora El Sol", estilo = Estilo(letra = Letra.CLASICA, tamano = 40, negrita = true, alineacion = Alineacion.CENTRO)),
                Elemento.Texto(texto = "Gracias por su compra", estilo = Estilo(letra = Letra.MANUSCRITA, tamano = 30, alineacion = Alineacion.CENTRO)),
                Elemento.Texto(texto = "Letra máquina subrayada", estilo = Estilo(letra = Letra.MAQUINA, tamano = 22, subrayado = true)),
                Elemento.Separador(borde = Borde.DOBLE, grosor = 2),
                Elemento.Campo(etiqueta = "Cliente", forma = FormaCampo.EN_LINEA),
                Elemento.Campo(etiqueta = "Teléfono", forma = FormaCampo.APILADO, borde = Borde.GUIONES),
                Elemento.Campo(etiqueta = "Dirección", forma = FormaCampo.CELDA, borde = Borde.PUNTILLOS),
                Elemento.Tabla(
                    columnas = listOf(Columna("Cant", 1, Alineacion.CENTRO, true), Columna("Producto", 3), Columna("Lote", 2, Alineacion.DERECHA)),
                    borde = Borde.PUNTILLOS, encabezadoInvertido = false, filasVacias = 2,
                ),
                Elemento.Separador(borde = Borde.GUIONES),
                Elemento.Qr(contenido = "https://ejemplo.com/entrega/12345", densidad = DensidadQr.LIGERO, tamanoPorcentaje = 35),
                Elemento.Qr(contenido = "https://ejemplo.com/entrega/12345", densidad = DensidadQr.ESPESO, forma = FormaQr.PUNTOS, tamanoPorcentaje = 45, mostrarTexto = true),
                Elemento.Barras(contenido = "786123456789", tipo = TipoBarras.EAN13, grosor = Grosor.NORMAL),
                Elemento.Barras(contenido = "ENTREGA-0042", tipo = TipoBarras.CODE128, grosor = Grosor.GRUESO, alto = 60),
                Elemento.Barras(contenido = "abc", tipo = TipoBarras.EAN8),
                Elemento.Firma(etiqueta = "Recibí conforme"),
            ),
        )
        val r2 = Renderizador(ctx).dibujar(todo, Datos(), 576, fecha, ejemplo = true)
        guardar("ticket_todo_80mm", r2.imagen)
        assertEquals(1, r2.avisos.size) // solo el EAN-8 inválido
        val r3 = Renderizador(ctx).dibujar(todo, Datos(), 384, fecha, ejemplo = true)
        guardar("ticket_todo_58mm", r3.imagen)

        val raster = EscPos.imagen(r1.imagen.width, r1.imagen.height, Renderizador.puntosNegros(r1.imagen))
        println("Nota 80 mm: ${r1.imagen.height} filas, ${raster.size} bytes")
        File(dir, "nota_2copias.bin").writeBytes(EscPos.trabajo(raster, 2))
        File(dir, "cuadre.bin").writeBytes(BluetoothPrinter.buildEscPos(buildReport(mapOf("b20" to 5), fecha)))
    }

    @Test
    fun pantallasDeLaApp() {
        compose.onNodeWithText("Billetes").performClick()
        compose.onAllNodesWithText("+")[2].performClick() // $20
        compose.onAllNodesWithText("+")[2].performClick()
        pantalla("01_billetes")
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        pantalla("02_inicio_cuadre")

        compose.onNodeWithText("🖨️  Impresión").performClick()
        pantalla("03_inicio_impresion")

        compose.onNodeWithText("Nota de entrega").performClick()
        compose.onNodeWithText("Establecimiento").performTextInput("Tienda Doña Rosa")
        compose.onNodeWithText("Nombre del que retira").performTextInput("Carlos Pérez")
        pantalla("04_nota_entrega")
        compose.onNodeWithText("👁  Ver vista previa").performScrollTo().performClick()
        compose.onNodeWithText("Ocultar vista previa").performScrollTo()
        pantalla("05_nota_vista_previa")
        compose.onNodeWithText("🖨️  Imprimir nota de entrega").performScrollTo().performClick()
        ventana("06_copias")
        compose.onNodeWithText("Cancelar").performClick()

        compose.onNodeWithText("Editar plantilla").performScrollTo().performClick()
        pantalla("07_editor")
        compose.onNodeWithText("+  Agregar elemento").performScrollTo().performClick()
        ventana("08_agregar_elemento")
        compose.onNodeWithText("Código QR").performClick()
        ventana("09_editar_qr")
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("👁  Vista previa").performClick()
        pantalla("10_editor_vista_previa")

        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Más opciones", useUnmergedTree = true)
        pantalla("11_vuelta_inicio")
    }
}
