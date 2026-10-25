package com.cuadrecaja.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
        // El ticket se vuelve a dibujar un momento después de cada cambio: dejar pasar ese tiempo.
        repeat(3) {
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            Thread.sleep(150)
        }
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
        File(dir, "nota_empaquetada.bin").writeBytes(BluetoothPrinter.empaquetar(EscPos.trabajo(raster, 2)))
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

        // Memoria de notas anteriores.
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ImpresionViewModel::class.java]
        compose.activity.runOnUiThread {
            listOf("Salentine vino tinto 750", "Salentine vino blanco 750", "Agua 500 ml").forEach { vm.memoria.recordar(Memoria.claveColumna("Producto"), it) }
            vm.memoria.recordar(Memoria.claveCampo("Nombre del que retira"), "Bernardo Guillén")
            vm.memoria.recordar(Memoria.claveCampo("Nombre del que retira"), "Carlos Pérez")
        }

        compose.onNodeWithText("Nota de entrega").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("ticket")).fetchSemanticsNodes().isNotEmpty() }
        pantalla("04_nota_ticket")

        // Tocar un campo en el mismo ticket abre ahí el cuadro para escribir.
        val plantilla = vm.plantillas.first()
        val zonas = Renderizador(compose.activity).dibujar(plantilla, Datos(), 576).zonas
        fun tocar(etiqueta: String) {
            val id = plantilla.elementos.first {
                (it is Elemento.Campo && it.etiqueta == etiqueta) || (it is Elemento.Tabla && etiqueta == "tabla")
            }.id
            val z = zonas.first { it.id == id }
            compose.onNodeWithTag("ticket").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width / 2f, (z.arriba + z.abajo) / 2f * width / 576f))
            }
            compose.waitForIdle()
        }
        tocar("Establecimiento")
        compose.onNodeWithContentDescription("Establecimiento").performTextInput("Tienda Doña Rosa")
        pantalla("04a_escribiendo")
        tocar("Nombre del que retira")
        // Vacío no muestra historial; al escribir, solo lo que coincide.
        assertEquals(0, compose.onAllNodesWithText("Carlos Pérez").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Nombre del que retira").performTextInput("Ber")
        pantalla("04b_memoria_nombre")
        assertEquals(0, compose.onAllNodesWithText("Carlos Pérez").fetchSemanticsNodes().size)
        compose.onNodeWithText("Bernardo Guillén").performClick()
        tocar("tabla")
        compose.onNodeWithText("Producto").performTextInput("salen")
        pantalla("04c_memoria_producto")
        compose.onNodeWithText("Salentine vino tinto 750").performClick()
        compose.onAllNodesWithText("Cant.")[0].performTextInput("12")
        assertEquals(listOf(listOf("12", "Salentine vino tinto 750")), vm.filas[plantilla.tablas.first().id])
        compose.onNodeWithText("Listo").performClick()
        pantalla("05_nota_llena")
        // "+ Agregar producto" debajo de la última fila del ticket agrega otra fila.
        compose.onNodeWithText("＋  Agregar producto").performClick()
        compose.waitForIdle()
        assertEquals(2, vm.filas[plantilla.tablas.first().id]!!.size)
        compose.onAllNodesWithText("Producto")[1].performTextInput("Agua 500 ml")
        compose.onNodeWithText("＋  Agregar producto").performClick()
        compose.waitForIdle()
        assertEquals(3, vm.filas[plantilla.tablas.first().id]!!.size)
        // Con la última fila vacía no se agregan más.
        compose.onNodeWithText("＋  Agregar producto").performClick()
        assertEquals(3, vm.filas[plantilla.tablas.first().id]!!.size)
        pantalla("05a_agregar_producto")
        compose.onNodeWithText("Listo").performClick()
        pantalla("05b_nota_dos_productos")
        assertEquals("Bernardo Guillén", vm.campos[plantilla.campos.first { it.etiqueta == "Nombre del que retira" }.id])
        compose.onNodeWithText("  Imprimir").performClick()
        ventana("06_copias")
        compose.onNodeWithText("Cancelar").performClick()

        compose.onNodeWithContentDescription("Editar plantilla").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("lienzo")).fetchSemanticsNodes().isNotEmpty() }
        pantalla("07_editor")

        // Tocar el título: se elige y abajo salen sus opciones.
        compose.onNodeWithTag("lienzo").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 15f)) }
        pantalla("08_editor_texto_elegido")
        compose.onNodeWithContentDescription("Listo").performClick()

        // Mantener presionado el título y arrastrarlo hacia abajo: cambia el orden.
        val antes = vm.plantillas.first().elementos.first().id
        compose.onNodeWithTag("lienzo").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width / 2f, 15f))
            advanceEventTime(1000)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, 40f)) }
        }
        pantalla("09_editor_moviendo")
        compose.onNodeWithTag("lienzo").performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("el título debió moverse", vm.plantillas.first().elementos.first().id != antes)
        compose.onNodeWithContentDescription("Listo").performClick()

        compose.onNodeWithText("Código QR").performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        pantalla("10_editor_qr_agregado")
        compose.onNodeWithContentDescription("Listo").performClick()
        compose.onNodeWithText("Papel 80 mm · largo automático").performClick()
        ventana("10b_largo")
        compose.onNodeWithText("150 mm").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals(150, vm.plantillas.first().largoMm)
        pantalla("10c_editor_largo")

        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Más opciones", useUnmergedTree = true)
        pantalla("11_vuelta_inicio")
    }
}
