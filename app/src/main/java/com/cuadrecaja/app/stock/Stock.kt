package com.cuadrecaja.app.stock

import android.content.Context
import com.cuadrecaja.app.plantillas.Alineacion
import com.cuadrecaja.app.plantillas.Borde
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Estilo
import com.cuadrecaja.app.plantillas.Grosor
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.plantillas.nuevoId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Sacar stock": listas de códigos de barras guardados, cada uno con su nombre, para
 * mostrarlos en la pantalla uno tras otro y pasarlos por el escáner.
 */
data class CodigoStock(
    val id: String = nuevoId(),
    val nombre: String = "",
    val codigo: String = "",
    /** null = se elige solo según el código (ver [tipoDe]). */
    val tipo: TipoBarras? = null,
)

data class ListaStock(
    val id: String = nuevoId(),
    val nombre: String = "Nueva lista",
    val codigos: List<CodigoStock> = emptyList(),
)

/** El tipo de código a usar: el elegido, o el que corresponde al contenido. */
fun tipoDe(c: CodigoStock): TipoBarras = c.tipo ?: tipoAutomatico(c.codigo)

fun tipoAutomatico(codigo: String): TipoBarras {
    val t = codigo.trim()
    if (!t.all(Char::isDigit)) return TipoBarras.CODE128
    return when (t.length) {
        13 -> if (digitoEanValido(t)) TipoBarras.EAN13 else TipoBarras.CODE128
        12 -> if (digitoEanValido(t)) TipoBarras.UPCA else TipoBarras.CODE128
        8 -> if (digitoEanValido(t)) TipoBarras.EAN8 else TipoBarras.CODE128
        else -> TipoBarras.CODE128
    }
}

/** Dígito de control de EAN-13 / EAN-8 / UPC-A (pesos 3 y 1 desde la derecha). */
fun digitoEanValido(t: String): Boolean {
    if (t.length < 2 || !t.all(Char::isDigit)) return false
    val datos = t.dropLast(1).reversed()
    val suma = datos.mapIndexed { i, ch -> (ch - '0') * if (i % 2 == 0) 3 else 1 }.sum()
    return (10 - suma % 10) % 10 == t.last() - '0'
}

/** La lista para imprimir, sin diseño: nombre, código de barras y números, uno tras otro. */
fun plantillaImpresion(l: ListaStock): Plantilla {
    val elementos = mutableListOf<Elemento>(
        Elemento.Texto(texto = l.nombre, estilo = Estilo(tamano = 30, negrita = true, alineacion = Alineacion.CENTRO)),
        Elemento.Texto(texto = "{fecha}  {hora}", estilo = Estilo(tamano = 20, alineacion = Alineacion.CENTRO)),
        Elemento.Separador(borde = Borde.LINEA, grosor = 2),
    )
    l.codigos.filter { it.codigo.isNotBlank() }.forEachIndexed { i, c ->
        if (i > 0) elementos += Elemento.Separador(borde = Borde.GUIONES, grosor = 2)
        elementos += Elemento.Espacio(alto = 8)
        elementos += Elemento.Texto(texto = "${i + 1}. " + c.nombre.ifBlank { "Código ${i + 1}" }, estilo = Estilo(tamano = 24, negrita = true))
        elementos += Elemento.Espacio(alto = 6)
        elementos += Elemento.Barras(contenido = c.codigo, tipo = tipoDe(c), alto = 80, grosor = Grosor.NORMAL, mostrarTexto = true)
        elementos += Elemento.Espacio(alto = 10)
    }
    return Plantilla(nombre = l.nombre, elementos = elementos)
}

object StockJson {
    fun aJson(listas: List<ListaStock>): String = JSONArray().apply {
        listas.forEach { l ->
            put(
                JSONObject().put("id", l.id).put("nombre", l.nombre).put(
                    "codigos",
                    JSONArray().apply {
                        l.codigos.forEach { c ->
                            put(JSONObject().put("id", c.id).put("nombre", c.nombre).put("codigo", c.codigo).put("tipo", c.tipo?.name ?: ""))
                        }
                    },
                ),
            )
        }
    }.toString()

    fun desdeJson(texto: String): List<ListaStock> {
        val arr = JSONArray(texto)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cs = o.optJSONArray("codigos") ?: JSONArray()
            ListaStock(
                id = o.optString("id", nuevoId()),
                nombre = o.optString("nombre", "Lista"),
                codigos = (0 until cs.length()).map { j ->
                    val c = cs.getJSONObject(j)
                    CodigoStock(
                        id = c.optString("id", nuevoId()),
                        nombre = c.optString("nombre"),
                        codigo = c.optString("codigo"),
                        tipo = TipoBarras.entries.firstOrNull { it.name == c.optString("tipo") },
                    )
                },
            )
        }
    }
}

class AlmacenStock(context: Context) {
    private val archivo = File(context.filesDir, "stock.json")

    fun cargar(): List<ListaStock> = runCatching { StockJson.desdeJson(archivo.readText()) }.getOrDefault(emptyList())

    fun guardar(listas: List<ListaStock>) {
        val tmp = File(archivo.parentFile, "stock.json.tmp")
        tmp.writeText(StockJson.aJson(listas))
        tmp.renameTo(archivo)
    }
}
