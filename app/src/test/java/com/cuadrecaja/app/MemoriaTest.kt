package com.cuadrecaja.app

import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.PlantillasPorDefecto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoriaTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun nueva(): Memoria = Memoria(File(carpeta.root, "memoria.json"))

    @Test
    fun conPocasLetrasSugiereElTextoCompleto() {
        val m = nueva()
        m.recordar("columna:producto", "Salentine vino tinto 750")
        m.recordar("columna:producto", "Agua 500 ml")
        assertEquals(listOf("Salentine vino tinto 750"), m.sugerencias("columna:producto", "Salen"))
        assertEquals(listOf("Salentine vino tinto 750"), m.sugerencias("columna:producto", "vino"))
        assertEquals(listOf("Salentine vino tinto 750"), m.sugerencias("columna:producto", "nto 7"))
        assertTrue(m.sugerencias("columna:producto", "cerveza").isEmpty())
    }

    @Test
    fun noImportanMayusculasNiTildes() {
        val m = nueva()
        m.recordar("campo:vendedor", "Bernardo Guillén")
        assertEquals(listOf("Bernardo Guillén"), m.sugerencias("campo:vendedor", "guillen"))
        assertEquals(listOf("Bernardo Guillén"), m.sugerencias("campo:vendedor", "BER"))
        // Si ya está escrito completo no se vuelve a sugerir.
        assertTrue(m.sugerencias("campo:vendedor", "bernardo guillen").isEmpty())
    }

    @Test
    fun vacioMuestraLoMasUsadoPrimero() {
        val m = nueva()
        m.recordar("campo:vendedor", "Ana", ahora = 1)
        m.recordar("campo:vendedor", "Bernardo Guillén", ahora = 2)
        m.recordar("campo:vendedor", "bernardo guillen", ahora = 3) // mismo nombre, otra forma
        assertEquals(listOf("bernardo guillen", "Ana"), m.sugerencias("campo:vendedor", ""))
    }

    @Test
    fun loQueEmpiezaIgualVaAntes() {
        val m = nueva()
        repeat(5) { m.recordar("columna:producto", "Vino Salentine") }
        m.recordar("columna:producto", "Salentine vino tinto 750")
        assertEquals("Salentine vino tinto 750", m.sugerencias("columna:producto", "sal").first())
    }

    @Test
    fun seGuardaYOlvida() {
        val m = nueva()
        m.recordar("columna:producto", "Salentine vino tinto 750")
        m.recordar("columna:producto", "Agua")
        m.guardar()
        val otra = nueva()
        assertEquals(2, otra.cantidad)
        otra.olvidar("columna:producto", "agua")
        assertEquals(1, nueva().cantidad)
    }

    @Test
    fun recuerdaLaNotaSinCantidadesNiFechas() {
        val p = PlantillasPorDefecto.notaDeEntrega()
        val fecha = p.campos.first { it.etiqueta == "Fecha" }
        val retira = p.campos.first { it.etiqueta == "Nombre del que retira" }
        val tabla = p.tablas.first()
        val m = nueva()
        m.recordarNota(
            p,
            Datos(
                campos = mapOf(fecha.id to "01/01/2026", retira.id to "Bernardo Guillén"),
                filas = mapOf(tabla.id to listOf(listOf("12", "Salentine vino tinto 750"), listOf("", ""))),
            ),
        )
        assertEquals(listOf("Bernardo Guillén"), m.sugerencias(Memoria.claveCampo("Nombre del que retira"), ""))
        assertEquals(listOf("Salentine vino tinto 750"), m.sugerencias(Memoria.claveColumna("Producto"), ""))
        assertTrue(m.sugerencias(Memoria.claveCampo("Fecha"), "").isEmpty())
        assertTrue(m.sugerencias(Memoria.claveColumna("Cant."), "").isEmpty())
        assertEquals(2, m.cantidad)
    }
}
