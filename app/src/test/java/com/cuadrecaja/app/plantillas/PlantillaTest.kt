package com.cuadrecaja.app.plantillas

import com.cuadrecaja.app.impresion.EscPos
import com.cuadrecaja.app.impresion.Imagenes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantillaTest {

    private val completa = Plantilla(
        nombre = "Completa",
        elementos = listOf(
            Elemento.Texto(texto = "Hola {Cliente}", estilo = Estilo(letra = Letra.CLASICA, tamano = 40, negrita = true, invertido = true)),
            Elemento.Campo(etiqueta = "Cliente", tipo = TipoCampo.TEXTO, forma = FormaCampo.CELDA, borde = Borde.PUNTILLOS),
            Elemento.Tabla(columnas = listOf(Columna("Cant", 1, Alineacion.CENTRO, true), Columna("Producto", 3)), borde = Borde.GUIONES, filasVacias = 2),
            Elemento.Separador(borde = Borde.DOBLE, grosor = 3),
            Elemento.Espacio(alto = 40),
            Elemento.Imagen(archivo = "logo.png", anchoPorcentaje = 50, alineacion = Alineacion.DERECHA),
            Elemento.Qr(contenido = "{Cliente}", densidad = DensidadQr.ESPESO, forma = FormaQr.PUNTOS, mostrarTexto = true),
            Elemento.Barras(contenido = "7861234567890", tipo = TipoBarras.EAN13, grosor = Grosor.GRUESO, alto = 100),
            Elemento.Firma(etiqueta = "Recibido", borde = Borde.GUIONES),
        ),
    )

    @Test
    fun plantillaSeGuardaYCargaIgual() {
        val texto = PlantillaJson.plantillasAJson(listOf(completa, PlantillasPorDefecto.notaDeEntrega()))
        val cargadas = PlantillaJson.plantillasDesdeJson(texto)
        assertEquals(completa, cargadas[0])
        assertEquals(2, cargadas.size)
    }

    @Test
    fun tipoDesconocidoSeIgnora() {
        val p = PlantillaJson.plantilla(org.json.JSONObject("""{"id":"x","nombre":"N","elementos":[{"tipo":"otro"},{"tipo":"espacio","alto":5}]}"""))
        assertEquals(listOf<Elemento>(Elemento.Espacio(p.elementos[0].id, 5)), p.elementos)
    }

    @Test
    fun reemplazaCamposPorNombre() {
        val campo = completa.campos.first()
        val datos = Datos(campos = mapOf(campo.id to "Tienda Rosita"))
        assertEquals("Hola Tienda Rosita - 24/10", reemplazarCampos("Hola {cliente} - {fecha}", completa, datos, mapOf("fecha" to "24/10")))
        assertEquals("Sin {Nada}", reemplazarCampos("Sin {Nada}", completa, datos))
    }

    @Test
    fun totalSumaLaColumnaDeCantidad() {
        val t = completa.tablas.first()
        assertEquals("7", totalTabla(t, listOf(listOf("2", "Arroz"), listOf("5", "Azúcar"), listOf("", "Nota"))))
        assertEquals("1.50", totalTabla(t, listOf(listOf("1,5", "Queso"))))
        assertEquals(null, totalTabla(t.copy(columnas = listOf(Columna("Producto"))), emptyList()))
    }

    @Test
    fun imagenEnBandasConCabeceraCorrecta() {
        // 10 x 3 puntos: ancho 2 bytes por fila. Primera columna negra.
        val negro = BooleanArray(30) { it % 10 == 0 }
        val bytes = EscPos.imagen(10, 3, negro)
        assertArrayEquals(byteArrayOf(0x1d, 0x76, 0x30, 0, 2, 0, 3, 0), bytes.copyOfRange(0, 8))
        assertEquals(8 + 6, bytes.size)
        assertEquals(0x80.toByte(), bytes[8])
        assertEquals(0.toByte(), bytes[9])
    }

    @Test
    fun imagenAltaSeParteEnVariasBandas() {
        val bytes = EscPos.imagen(8, 300, BooleanArray(2400))
        // 300 filas = 128 + 128 + 44, cada banda con su cabecera de 8 bytes.
        assertEquals(300 + 3 * 8, bytes.size)
    }

    @Test
    fun copiasSeCortanEntreSi() {
        val img = byteArrayOf(7, 7)
        val t = EscPos.trabajo(img, 3)
        val corte = byteArrayOf(0x1d, 0x56, 66, 0)
        var cortes = 0
        for (i in 0..t.size - 4) if (t.copyOfRange(i, i + 4).contentEquals(corte)) cortes++
        assertEquals(2, cortes) // entre copias; la última la corta el receptor
        assertArrayEquals(byteArrayOf(0x1b, 0x40), t.copyOfRange(0, 2))
        assertEquals(1, t.indices.count { i -> i + 1 < t.size && t[i] == 0x1b.toByte() && t[i + 1] == 0x40.toByte() })
    }

    @Test
    fun tramadoDejaSoloBlancoYNegro() {
        val g = FloatArray(100) { 128f }
        Imagenes.tramarGrises(g, 10, 10)
        assertTrue(g.all { it == 0f || it == 255f })
        val negros = g.count { it == 0f }
        assertTrue("gris medio da mitad de puntos negros: $negros", negros in 40..60)
    }
}
