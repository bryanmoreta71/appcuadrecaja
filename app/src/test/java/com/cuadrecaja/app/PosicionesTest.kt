package com.cuadrecaja.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.cuadrecaja.app.impresion.PaqueteComputadora
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Alineacion
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Estilo
import com.cuadrecaja.app.plantillas.FormaCampo
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.reubicar
import com.cuadrecaja.app.ui.EditorVisualScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Date

/** Elementos movidos a mano: en la misma línea, a un lado, y que lo de abajo siga a las tablas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h860dp-xhdpi")
class PosicionesTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dir = File(System.getProperty("capturas.dir") ?: "build/capturas").apply { mkdirs() }
    private val fecha = Date(1793900000000L)

    private val titulo = Elemento.Texto(texto = "PEDIDO", estilo = Estilo(tamano = 34, negrita = true))
    private val numero = Elemento.Texto(texto = "N° 0042", estilo = Estilo(tamano = 24))
    private val cliente = Elemento.Campo(etiqueta = "Cliente", forma = FormaCampo.EN_LINEA)
    private val tabla = Elemento.Tabla()
    private val gracias = Elemento.Texto(texto = "Gracias", estilo = Estilo(tamano = 26, cursiva = true))
    private val dia = Elemento.Texto(texto = "{fecha}", estilo = Estilo(tamano = 22))
    private val qr = Elemento.Qr(contenido = "{Cliente}", tamanoPorcentaje = 30)

    private fun dibujar(p: Plantilla, d: Datos = Datos()) = Renderizador(ctx).dibujar(p, d, 576, fecha)

    private fun zonas(r: Renderizador.Resultado) = r.zonas.associate { it.id to (it.arriba to it.abajo) }

    @Test
    fun dosTextosEnLaMismaLineaYLoDeAbajoSigueALaTabla() {
        var p = Plantilla(elementos = listOf(titulo, numero, cliente, tabla, gracias, dia, qr))
        var r = dibujar(p)
        val clienteAntes = r.zonas.first { it.id == cliente.id }.arriba
        // El número, a la derecha del título y a su misma altura.
        p = reubicar(p, zonas(r), numero.id, r.zonas.first { it.id == titulo.id }.arriba, 400)
        r = dibujar(p)
        val zt = r.zonas.first { it.id == titulo.id }
        val zn = r.zonas.first { it.id == numero.id }
        assertEquals(zt.arriba, zn.arriba, 0.5f)
        assertEquals(400f, zn.izquierda, 1f)
        // El campo de abajo no se movió de donde estaba.
        assertEquals(clienteAntes, r.zonas.first { it.id == cliente.id }.arriba, 0.5f)

        // La fecha al lado de "Gracias" y el QR a la derecha, debajo de la tabla.
        val zg = r.zonas.first { it.id == gracias.id }
        p = reubicar(p, zonas(r), dia.id, zg.arriba, 330)
        r = dibujar(p)
        p = reubicar(p, zonas(r), qr.id, r.zonas.first { it.id == gracias.id }.abajo + 10, 380)
        r = dibujar(p)
        assertEquals(r.zonas.first { it.id == gracias.id }.arriba, r.zonas.first { it.id == dia.id }.arriba, 0.5f)
        // Se guarda y se carga igual.
        assertEquals(p, com.cuadrecaja.app.plantillas.PlantillaJson.plantilla(com.cuadrecaja.app.plantillas.PlantillaJson.plantilla(p)))

        // Con más filas en la tabla, lo de abajo baja con ella y sigue en la misma línea.
        val filas = Datos(campos = mapOf(cliente.id to "Tienda Doña Rosa"), filas = mapOf(tabla.id to List(6) { listOf("${it + 1}", "Producto ${it + 1}") }))
        val lleno = dibujar(p, filas)
        val crecio = lleno.zonas.first { it.id == tabla.id }.abajo - r.zonas.first { it.id == tabla.id }.abajo
        assertTrue(crecio > 100)
        assertEquals(r.zonas.first { it.id == gracias.id }.arriba + crecio, lleno.zonas.first { it.id == gracias.id }.arriba, 1f)
        assertEquals(lleno.zonas.first { it.id == gracias.id }.arriba, lleno.zonas.first { it.id == dia.id }.arriba, 0.5f)
        assertEquals(380f, lleno.zonas.first { it.id == qr.id }.izquierda, 1f)
        guardar("p1_misma_linea", lleno.imagen)

        // Para el programa de Windows: la misma plantilla y los datos.
        File(dir, "paquete_posiciones.bin").writeBytes(PaqueteComputadora.crear(ctx, p, 576, Memoria(File(dir, "memoria_pos.json"))).datos)
        File(dir, "datos_posiciones.txt").writeText(
            "${cliente.id}=Tienda Doña Rosa\n" + (1..6).joinToString("") { "fila:${tabla.id}=$it|Producto $it\n" },
        )
    }

    @Test
    fun arrastrarCercaDeOtroSePegaALaMismaAltura() {
        val uno = Elemento.Texto(texto = "Uno", estilo = Estilo(tamano = 30))
        val dos = Elemento.Texto(texto = "Dos", estilo = Estilo(tamano = 30))
        val tres = Elemento.Texto(texto = "Tres", estilo = Estilo(tamano = 30, alineacion = Alineacion.DERECHA))
        var plantilla by mutableStateOf(Plantilla(elementos = listOf(uno, dos, tres)))
        compose.setContent {
            EditorVisualScreen(
                plantilla = plantilla, papelMm = 80, onCambio = { plantilla = it },
                renderizar = { Renderizador(ctx).dibujar(it, Datos(), 576, fecha, ejemplo = true) },
                guardarImagen = { null }, onListo = {}, onLlenar = {},
            )
        }
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("lienzo")).fetchSemanticsNodes().isNotEmpty() }
        val antes = dibujar(plantilla).zonas
        val z1 = antes.first { it.id == uno.id }
        val z2 = antes.first { it.id == dos.id }
        compose.onNodeWithTag("lienzo").performTouchInput {
            val escala = width / 576f
            // Tomar "Dos" y llevarlo a la derecha de "Uno", un poco más abajo de su altura.
            down(Offset((z2.izquierda + 10) * escala, (z2.arriba + 10) * escala))
            advanceEventTime(1000)
            val dy = (z1.arriba - z2.arriba + 5) * escala
            repeat(10) { moveBy(Offset(30f * escala, dy / 10)) }
        }
        compose.waitForIdle()
        pantalla("p2_guia_alineacion")
        compose.onNodeWithTag("lienzo").performTouchInput { up() }
        compose.waitForIdle()
        val despues = dibujar(plantilla).zonas
        // Quedó pegado exactamente a la misma altura que "Uno", a su derecha.
        assertEquals(despues.first { it.id == uno.id }.arriba, despues.first { it.id == dos.id }.arriba, 0.5f)
        assertTrue(despues.first { it.id == dos.id }.izquierda > 250)
        assertTrue(plantilla.posiciones.containsKey(dos.id))
        // "Tres" no se movió de donde se veía.
        assertEquals(antes.first { it.id == tres.id }.arriba, despues.first { it.id == tres.id }.arriba, 1f)
    }

    private fun guardar(nombre: String, b: Bitmap) = File(dir, "$nombre.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun pantalla(nombre: String) {
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        captura(nombre)
    }

    private fun captura(nombre: String) {
        val vista = compose.activity.window.decorView
        val b = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(b))
        guardar(nombre, b)
    }
}
