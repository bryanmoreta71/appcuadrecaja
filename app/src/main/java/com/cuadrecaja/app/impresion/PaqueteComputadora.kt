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
import com.cuadrecaja.app.plantillas.Posicion
import com.cuadrecaja.app.plantillas.admiteX
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
        val renderizador = Renderizador(context)
        val avisos = mutableListOf<String>()
        val bloques = JSONArray()
        // Cada bloque va con su posición: "dy" desde el final de lo anterior (como en el
        // celular) y "x" si se movió a un lado. Así dos cosas pueden ir en la misma línea.
        for (e in plantilla.elementos) {
            val pos = plantilla.posiciones[e.id]
            val bloque = if (esDinamico(e, plantilla)) {
                if (e is Elemento.Barras && e.tipo != TipoBarras.CODE128) {
                    avisos += "El código de barras con campos se imprime como Code 128 en la computadora."
                }
                JSONObject().put("tipo", "elemento").put("elemento", PlantillaJson.elemento(e)).put("x", pos?.x?.takeIf { admiteX(e) } ?: -1)
            } else {
                // Lo fijo se dibuja solo (sin lo que tenga al lado) y con fondo transparente.
                val solo = renderizador.dibujar(
                    plantilla.copy(elementos = listOf(e), posiciones = pos?.x?.let { mapOf(e.id to Posicion(x = it)) } ?: emptyMap(), largoMm = 0),
                    Datos(), anchoPuntos, Date(),
                )
                val zona = solo.zonas.firstOrNull() ?: continue
                val arriba = zona.arriba.toInt()
                val alto = (zona.abajo.toInt() - arriba).coerceAtMost(solo.imagen.height - arriba)
                if (alto <= 0) JSONObject().put("tipo", "espacio").put("alto", 0)
                else JSONObject().put("tipo", "imagen").put("alto", alto)
                    .put("png", png(transparente(Bitmap.createBitmap(solo.imagen, 0, arriba, solo.imagen.width, alto))))
            }
            bloques.put(bloque.put("dy", pos?.dy ?: 0))
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

    private fun transparente(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        for (i in px.indices) if (px[i] == android.graphics.Color.WHITE) px[i] = android.graphics.Color.TRANSPARENT
        val salida = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        salida.setPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return salida
    }

    private fun png(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
