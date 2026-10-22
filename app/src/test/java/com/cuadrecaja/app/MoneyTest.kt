package com.cuadrecaja.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MoneyTest {

    private val date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 22, 18, 5) }.time

    @Test
    fun cincoBilletesDe20SonCienDolares() {
        assertEquals(10000L, sectionTotal(Kind.BILLETES, mapOf("b20" to 5)))
    }

    @Test
    fun sumaBilletesYMonedas() {
        val counts = mapOf("b20" to 5, "b1" to 3, "m25" to 7, "m1" to 4)
        assertEquals(10300L, sectionTotal(Kind.BILLETES, counts))
        assertEquals(179L, sectionTotal(Kind.MONEDAS, counts))
        assertEquals(10479L, grandTotal(counts))
        assertEquals("$104.79", formatMoney(grandTotal(counts)))
    }

    @Test
    fun formatoConMiles() {
        assertEquals("$1,234.05", formatMoney(123405))
        assertEquals("$0.00", formatMoney(0))
    }

    @Test
    fun reporteTieneDetalleYTotal() {
        val lines = buildReport(mapOf("b20" to 5, "m25" to 4), date)
        val text = lines.map { it.text }
        assertEquals("CUADRE DE CAJA", text.first())
        assertTrue(text.contains("Fecha: 22/10/2026"))
        assertTrue(text.contains("Hora:  06:05 PM"))
        assertTrue(text.contains("5 x $20".padEnd(25) + "$100.00"))
        assertTrue(text.contains("4 x $0.25".padEnd(27) + "$1.00"))
        assertEquals("$101.00", text.last())
        assertTrue(text.all { it.length <= LINE_WIDTH })
    }

    @Test
    fun reporteOmiteDenominacionesEnCero() {
        val text = buildReport(mapOf("b10" to 2), date).map { it.text }
        assertTrue(text.none { it.contains("MONEDAS") })
        assertTrue(text.none { it.contains("x $20") })
    }

    @Test
    fun escPosEsAsciiYEmpiezaConInicializar() {
        val bytes = BluetoothPrinter.buildEscPos(listOf(ReportLine("Año 25¢")))
        assertArrayEquals(byteArrayOf(0x1b, 0x40), bytes.copyOfRange(0, 2))
        assertTrue(String(bytes, Charsets.US_ASCII).contains("Ano 25c"))
    }
}

class MessageTest {
    @Test
    fun parteRenglonesSinCortarPalabras() {
        val lines = wrapText("Se dejan 20 dolares en sencillo para el turno de la noche, gracias")
        assertTrue(lines.all { it.length <= LINE_WIDTH })
        assertEquals("Se dejan 20 dolares en sencillo", lines[0])
        assertEquals("Se dejan 20 dolares en sencillo para el turno de la noche, gracias", lines.joinToString(" "))
    }

    @Test
    fun respetaSaltosDeLineaYPalabrasLargas() {
        assertEquals(listOf("Hola", "", "Chao"), wrapText("Hola\n\nChao"))
        assertEquals(listOf("a".repeat(32), "aaa"), wrapText("a".repeat(35)))
    }

    @Test
    fun mensajeTieneTituloYTexto() {
        val text = buildMessage("Falta cambio", java.util.Date()).map { it.text }
        assertEquals("MENSAJE", text.first())
        assertEquals("Falta cambio", text.last())
    }
}
