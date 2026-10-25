package com.cuadrecaja.app.impresion

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.cuadrecaja.app.Memoria
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.PlantillaJson
import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.plantillas.TipoCampo
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Plantilla lista para la computadora (programa "Nota de entrega" de Windows).
 *
 * Lo que no cambia (logos, títulos, líneas, QR sin campos...) se manda ya dibujado, para
 * que salga idéntico al celular. Lo que se llena (campos, tabla de productos y los textos,
 * QR o códigos que usan {campos}) se manda como elemento, y la computadora lo dibuja con
 * los datos que se escriban allá.
 */
object PaqueteComputadora {

    /** Así empieza el paquete, para que el receptor lo distinga de un ticket para imprimir. */
    const val PREFIJO = "CCPLANTILLA1\n"

    data class Resultado(val datos: ByteArray, val avisos: List<String>)

    private val marcador = Regex("\\{([^{}]+)\\}")

    /** Usa algún {campo}, {fecha} u {hora}: hay que dibujarlo en la computadora. */
    fun usaCampos(texto: String, plantilla: Plantilla): Boolean {
        val nombres = plantilla.campos.map { it.etiqueta.trim().lowercase() }.toSet() + setOf("fecha", "hora")
        return marcador.findAll(texto).any { it.groupValues[1].trim().lowercase() in nombres }
    }

    fun esDinamico(e: Elemento, plantilla: Plantilla): Boolean = when (e) {
        is Elemento.Campo, is Elemento.Tabla -> true
        is Elemento.Texto -> usaCampos(e.texto, plantilla)
        is Elemento.Qr -> usaCampos(e.contenido, plantilla)
        is Elemento.Barras -> usaCampos(e.contenido, plantilla)
        else -> false
    }

    fun crear(context: Context, plantilla: Plantilla, anchoPuntos: Int, memoria: Memoria): Resultado {
        val dibujo = Renderizador(context).dibujar(plantilla, Datos(), anchoPuntos, Date())
        val avisos = mutableListOf<String>()
        val bloques = JSONArray()
        for (e in plantilla.elementos) {
            val zona = dibujo.zonas.firstOrNull { it.id == e.id } ?: continue
            if (esDinamico(e, plantilla)) {
                bloques.put(JSONObject().put("tipo", "elemento").put("elemento", PlantillaJson.elemento(e)))
                if (e is Elemento.Barras && e.tipo != TipoBarras.CODE128) {
                    avisos += "El código de barras con campos se imprime como Code 128 en la computadora."
                }
            } else {
                val arriba = zona.arriba.toInt()
                val alto = (zona.abajo.toInt() - arriba).coerceAtMost(dibujo.imagen.height - arriba)
                if (alto <= 0) {
                    bloques.put(JSONObject().put("tipo", "espacio").put("alto", 0))
                    continue
                }
                val pedazo = Bitmap.createBitmap(dibujo.imagen, 0, arriba, dibujo.imagen.width, alto)
                bloques.put(JSONObject().put("tipo", "imagen").put("alto", alto).put("png", png(pedazo)))
            }
        }

        // Lo que ya se escribió en el celular, para que la computadora lo sugiera también.
        val recuerdos = JSONObject()
        val claves = plantilla.campos.filter { it.tipo != TipoCampo.FECHA && it.tipo != TipoCampo.HORA }
            .map { Memoria.claveCampo(it.etiqueta) } +
            plantilla.tablas.flatMap { t -> t.columnas.filter { !it.numerica }.map { Memoria.claveColumna(it.titulo) } }
        claves.distinct().forEach { clave ->
            val textos = memoria.textos(clave)
            if (textos.isNotEmpty()) recuerdos.put(clave, JSONArray(textos))
        }

        val json = JSONObject()
            .put("version", 1)
            .put("anchoPuntos", anchoPuntos)
            .put("plantilla", JSONObject().put("id", plantilla.id).put("nombre", plantilla.nombre).put("largoMm", plantilla.largoMm))
            .put("bloques", bloques)
            .put("memoria", recuerdos)
        return Resultado((PREFIJO + json.toString()).toByteArray(Charsets.UTF_8), avisos.distinct())
    }

    private fun png(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
