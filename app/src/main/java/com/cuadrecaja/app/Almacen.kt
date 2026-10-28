package com.cuadrecaja.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.PlantillaJson
import com.cuadrecaja.app.plantillas.PlantillasPorDefecto
import com.cuadrecaja.app.plantillas.nuevoId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Modo { CUADRE, IMPRESION }

/** Ajustes generales de la app. */
class Ajustes(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var modo: Modo
        get() = runCatching { Modo.valueOf(prefs.getString("modo", null)!!) }.getOrDefault(Modo.CUADRE)
        set(v) = prefs.edit().putString("modo", v.name).apply()

    /** Ancho del papel en mm: 80 (impresora de escritorio, como la SAT) o 58 (portátil). */
    var papelMm: Int
        get() = prefs.getInt("papelMm", 80)
        set(v) = prefs.edit().putInt("papelMm", v).apply()

    /** Puntos de ancho de la cabeza de impresión a 203 ppp. */
    val anchoPuntos: Int get() = if (papelMm == 58) 384 else 576
}

/** Plantillas guardadas en el teléfono. Siempre hay al menos una. */
class AlmacenPlantillas(context: Context) {
    private val archivo = File(context.filesDir, "plantillas.json")

    fun cargar(): List<Plantilla> {
        val lista = runCatching { PlantillaJson.plantillasDesdeJson(archivo.readText()) }.getOrNull()
        return if (lista.isNullOrEmpty()) listOf(PlantillasPorDefecto.notaDeEntrega()).also(::guardar) else lista
    }

    fun guardar(lista: List<Plantilla>) {
        val tmp = File(archivo.parentFile, "plantillas.json.tmp")
        tmp.writeText(PlantillaJson.plantillasAJson(lista))
        tmp.renameTo(archivo)
    }
}

enum class TipoImpresion(val etiqueta: String) { CUADRE("Cuadre de caja"), MENSAJE("Mensaje"), NOTA("Nota de entrega"), STOCK("Lista de códigos") }

data class EntradaHistorial(
    val id: String,
    val titulo: String,
    val tipo: TipoImpresion,
    val fecha: Long,
    val copias: Int,
    /** Texto del ticket (cuadre y mensaje). Las notas de entrega guardan una imagen. */
    val textoVista: String?,
)

/** Últimos 5 tickets impresos, para verlos o reimprimirlos. */
class Historial(context: Context) {
    private val carpeta = File(context.filesDir, "historial").apply { mkdirs() }
    private val indice = File(carpeta, "indice.json")

    fun entradas(): List<EntradaHistorial> = runCatching {
        val arr = JSONArray(indice.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            EntradaHistorial(
                o.getString("id"), o.getString("titulo"),
                runCatching { TipoImpresion.valueOf(o.getString("tipo")) }.getOrDefault(TipoImpresion.MENSAJE),
                o.getLong("fecha"), o.optInt("copias", 1),
                if (o.has("texto")) o.getString("texto") else null,
            )
        }
    }.getOrDefault(emptyList())

    fun agregar(titulo: String, tipo: TipoImpresion, copias: Int, datos: ByteArray, textoVista: String?, imagenVista: Bitmap?) {
        val id = nuevoId()
        File(carpeta, "$id.bin").writeBytes(datos)
        imagenVista?.let { img -> File(carpeta, "$id.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val nueva = EntradaHistorial(id, titulo, tipo, System.currentTimeMillis(), copias, textoVista)
        val lista = listOf(nueva) + entradas()
        lista.drop(MAXIMO).forEach { borrarArchivos(it.id) }
        guardarIndice(lista.take(MAXIMO))
    }

    fun datos(id: String): ByteArray? = File(carpeta, "$id.bin").takeIf { it.exists() }?.readBytes()

    fun imagen(id: String): Bitmap? = File(carpeta, "$id.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun borrar(id: String) {
        borrarArchivos(id)
        guardarIndice(entradas().filter { it.id != id })
    }

    private fun borrarArchivos(id: String) {
        File(carpeta, "$id.bin").delete()
        File(carpeta, "$id.png").delete()
    }

    private fun guardarIndice(lista: List<EntradaHistorial>) {
        val arr = JSONArray()
        lista.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("titulo", e.titulo).put("tipo", e.tipo.name)
                .put("fecha", e.fecha).put("copias", e.copias).apply { e.textoVista?.let { put("texto", it) } })
        }
        indice.writeText(arr.toString())
    }

    companion object {
        const val MAXIMO = 5
    }
}
