package com.cuadrecaja.app

import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoCampo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * Memoria de lo que se ha escrito en las notas de entrega (vendedores, establecimientos,
 * productos...) para sugerirlo la próxima vez. Se guarda por nombre del campo o de la
 * columna, así sirve en todas las plantillas que usen el mismo nombre.
 */
class Memoria(private val archivo: File) {

    private data class Entrada(val texto: String, val usos: Int, val ultimo: Long)

    private val datos: MutableMap<String, MutableList<Entrada>> = cargar()

    /** Sugerencias para lo que se está escribiendo: primero lo que empieza igual, luego lo más usado. */
    fun sugerencias(clave: String, escrito: String, maximo: Int = 5): List<String> {
        val lista = datos[clave].orEmpty()
        val buscado = normalizar(escrito)
        return lista
            .mapNotNull { e ->
                val n = normalizar(e.texto)
                val orden = when {
                    buscado.isEmpty() -> 0
                    n == buscado -> return@mapNotNull null // ya está escrito completo
                    n.startsWith(buscado) -> 0
                    n.split(' ').any { it.startsWith(buscado) } -> 1
                    n.contains(buscado) -> 2
                    else -> return@mapNotNull null
                }
                Triple(e, orden, n)
            }
            .sortedWith(compareBy<Triple<Entrada, Int, String>> { it.second }.thenByDescending { it.first.usos }.thenByDescending { it.first.ultimo })
            .take(maximo)
            .map { it.first.texto }
    }

    /** Todo lo guardado para un campo, lo más usado primero (para mandarlo a la computadora). */
    fun textos(clave: String, maximo: Int = 150): List<String> =
        datos[clave].orEmpty().sortedWith(compareByDescending<Entrada> { it.usos }.thenByDescending { it.ultimo }).take(maximo).map { it.texto }

    fun recordar(clave: String, texto: String, ahora: Long = System.currentTimeMillis()) {
        val t = texto.trim()
        if (t.isEmpty()) return
        val lista = datos.getOrPut(clave) { mutableListOf() }
        val i = lista.indexOfFirst { normalizar(it.texto) == normalizar(t) }
        if (i >= 0) lista[i] = Entrada(t, lista[i].usos + 1, ahora) else lista += Entrada(t, 1, ahora)
        // Se quedan los más usados y recientes.
        if (lista.size > MAXIMO_POR_CAMPO) {
            val quedan = lista.sortedWith(compareByDescending<Entrada> { it.usos }.thenByDescending { it.ultimo }).take(MAXIMO_POR_CAMPO)
            lista.retainAll(quedan.toSet())
        }
    }

    fun olvidar(clave: String, texto: String) {
        datos[clave]?.removeAll { normalizar(it.texto) == normalizar(texto) }
        guardar()
    }

    fun borrarTodo() {
        datos.clear()
        guardar()
    }

    val cantidad: Int get() = datos.values.sumOf { it.size }

    /** Después de imprimir una nota: guarda lo escrito en los campos y en las columnas de texto. */
    fun recordarNota(plantilla: Plantilla, d: Datos) {
        plantilla.campos
            .filter { it.tipo != TipoCampo.FECHA && it.tipo != TipoCampo.HORA }
            .forEach { c -> d.campos[c.id]?.let { recordar(claveCampo(c.etiqueta), it) } }
        plantilla.tablas.forEach { t ->
            t.columnas.forEachIndexed { i, col ->
                if (col.numerica) return@forEachIndexed
                d.filas[t.id].orEmpty().forEach { fila -> fila.getOrNull(i)?.let { recordar(claveColumna(col.titulo), it) } }
            }
        }
        guardar()
    }

    private fun cargar(): MutableMap<String, MutableList<Entrada>> {
        val mapa = mutableMapOf<String, MutableList<Entrada>>()
        runCatching {
            val o = JSONObject(archivo.readText())
            for (clave in o.keys()) {
                val arr = o.getJSONArray(clave)
                mapa[clave] = (0 until arr.length()).map {
                    val e = arr.getJSONObject(it)
                    Entrada(e.getString("t"), e.optInt("u", 1), e.optLong("f"))
                }.toMutableList()
            }
        }
        return mapa
    }

    fun guardar() {
        val o = JSONObject()
        datos.forEach { (clave, lista) ->
            o.put(clave, JSONArray().apply { lista.forEach { put(JSONObject().put("t", it.texto).put("u", it.usos).put("f", it.ultimo)) } })
        }
        archivo.parentFile?.mkdirs()
        val tmp = File(archivo.path + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(archivo)
    }

    companion object {
        const val MAXIMO_POR_CAMPO = 300

        fun claveCampo(etiqueta: String) = "campo:" + normalizar(etiqueta)
        fun claveColumna(titulo: String) = "columna:" + normalizar(titulo)

        /** Sin mayúsculas, tildes ni espacios de más: "Salentine  Vino" = "salentine vino". */
        fun normalizar(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("\\s+"), " ")
    }
}
