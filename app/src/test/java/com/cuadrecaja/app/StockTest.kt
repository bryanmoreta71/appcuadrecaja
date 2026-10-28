package com.cuadrecaja.app

import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.stock.CodigoStock
import com.cuadrecaja.app.stock.ListaStock
import com.cuadrecaja.app.stock.StockJson
import com.cuadrecaja.app.stock.digitoEanValido
import com.cuadrecaja.app.stock.tipoAutomatico
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockTest {
    @Test
    fun tipoSegunElCodigo() {
        assertEquals(TipoBarras.EAN13, tipoAutomatico("4006381333931"))
        assertEquals(TipoBarras.UPCA, tipoAutomatico("036000291452"))
        assertEquals(TipoBarras.EAN8, tipoAutomatico("96385074"))
        assertEquals(TipoBarras.CODE128, tipoAutomatico("4006381333932")) // dígito de control malo
        assertEquals(TipoBarras.CODE128, tipoAutomatico("SAL-750-RM"))
        assertEquals(TipoBarras.CODE128, tipoAutomatico("12345"))
    }

    @Test
    fun digitoDeControl() {
        assertTrue(digitoEanValido("4006381333931"))
        assertFalse(digitoEanValido("400638133393"))
    }

    @Test
    fun listasSeGuardanYCarganIgual() {
        val l = listOf(
            ListaStock(nombre = "Vinos", codigos = listOf(CodigoStock(nombre = "Salentein Merlot", codigo = "4006381333931"), CodigoStock(nombre = "Caja", codigo = "X-1", tipo = TipoBarras.CODE39))),
            ListaStock(nombre = "Vacía"),
        )
        assertEquals(l, StockJson.desdeJson(StockJson.aJson(l)))
    }
}
