package com.cuadrecaja.app.plantillas

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/*
 * Una plantilla es una lista de elementos que se dibujan de arriba hacia abajo en la tirilla.
 * Todo se guarda como JSON en el teléfono.
 */

enum class Letra(val etiqueta: String, val familia: String) {
    MODERNA("Moderna", "sans-serif"),
    CLASICA("Clásica", "serif"),
    MAQUINA("Máquina", "monospace"),
    ANGOSTA("Angosta", "sans-serif-condensed"),
    MANUSCRITA("Manuscrita", "cursive"),
}

enum class Alineacion(val etiqueta: String) { IZQUIERDA("Izquierda"), CENTRO("Centro"), DERECHA("Derecha") }

enum class Borde(val etiqueta: String) {
    NINGUNO("Sin borde"),
    LINEA("Línea completa"),
    PUNTILLOS("Puntillos"),
    GUIONES("Guiones"),
    DOBLE("Doble línea"),
}

data class Estilo(
    val letra: Letra = Letra.MODERNA,
    val tamano: Int = 24,
    val negrita: Boolean = false,
    val cursiva: Boolean = false,
    val subrayado: Boolean = false,
    val invertido: Boolean = false,
    val alineacion: Alineacion = Alineacion.IZQUIERDA,
)

enum class TipoCampo(val etiqueta: String) {
    TEXTO("Texto"),
    NUMERO("Número"),
    FECHA("Fecha (automática)"),
    HORA("Hora (automática)"),
    PARRAFO("Varias líneas"),
}

enum class FormaCampo(val etiqueta: String) {
    EN_LINEA("Etiqueta: valor"),
    APILADO("Etiqueta arriba"),
    CELDA("Dentro de una celda"),
}

data class Columna(
    val titulo: String,
    val ancho: Int = 1,
    val alineacion: Alineacion = Alineacion.IZQUIERDA,
    val numerica: Boolean = false,
)

enum class DensidadQr(val etiqueta: String, val descripcion: String) {
    LIGERO("Ligero", "Pocos cuadritos, se lee rápido"),
    SENCILLO("Sencillo", "Equilibrado"),
    ESPESO("Espeso", "Más denso, resiste manchas"),
}

enum class FormaQr(val etiqueta: String) { CUADROS("Cuadros"), PUNTOS("Puntos redondos") }

enum class TipoBarras(val etiqueta: String, val ayuda: String) {
    CODE128("Code 128", "Letras y números"),
    CODE39("Code 39", "Mayúsculas y números"),
    CODE93("Code 93", "Mayúsculas y números"),
    EAN13("EAN-13", "12 o 13 dígitos"),
    EAN8("EAN-8", "7 u 8 dígitos"),
    UPCA("UPC-A", "11 o 12 dígitos"),
    ITF("ITF", "Cantidad par de dígitos"),
    CODABAR("Codabar", "Números y - $ : / . +"),
}

enum class Grosor(val etiqueta: String, val porcentaje: Int) {
    DELGADO("Delgado", 55), NORMAL("Normal", 75), GRUESO("Grueso", 100),
}

sealed class Elemento {
    abstract val id: String

    data class Texto(
        override val id: String = nuevoId(),
        val texto: String = "Texto",
        val estilo: Estilo = Estilo(),
    ) : Elemento()

    data class Campo(
        override val id: String = nuevoId(),
        val etiqueta: String = "Campo",
        val tipo: TipoCampo = TipoCampo.TEXTO,
        val forma: FormaCampo = FormaCampo.EN_LINEA,
        val borde: Borde = Borde.PUNTILLOS,
        val valorInicial: String = "",
        val estilo: Estilo = Estilo(),
    ) : Elemento()

    data class Tabla(
        override val id: String = nuevoId(),
        val columnas: List<Columna> = listOf(
            Columna("Cant.", 1, Alineacion.CENTRO, numerica = true),
            Columna("Producto", 4),
        ),
        val borde: Borde = Borde.LINEA,
        val encabezadoInvertido: Boolean = true,
        val mostrarTotal: Boolean = true,
        val filasVacias: Int = 0,
        val estilo: Estilo = Estilo(tamano = 22),
    ) : Elemento()

    data class Separador(
        override val id: String = nuevoId(),
        val borde: Borde = Borde.LINEA,
        val grosor: Int = 2,
    ) : Elemento()

    data class Espacio(
        override val id: String = nuevoId(),
        val alto: Int = 20,
    ) : Elemento()

    data class Imagen(
        override val id: String = nuevoId(),
        val archivo: String = "",
        val anchoPorcentaje: Int = 60,
        val alineacion: Alineacion = Alineacion.CENTRO,
    ) : Elemento()

    data class Qr(
        override val id: String = nuevoId(),
        val contenido: String = "{Establecimiento}",
        val tamanoPorcentaje: Int = 45,
        val densidad: DensidadQr = DensidadQr.SENCILLO,
        val forma: FormaQr = FormaQr.CUADROS,
        val alineacion: Alineacion = Alineacion.CENTRO,
        val mostrarTexto: Boolean = false,
    ) : Elemento()

    data class Barras(
        override val id: String = nuevoId(),
        val contenido: String = "123456789012",
        val tipo: TipoBarras = TipoBarras.CODE128,
        val alto: Int = 80,
        val grosor: Grosor = Grosor.NORMAL,
        val mostrarTexto: Boolean = true,
        val alineacion: Alineacion = Alineacion.CENTRO,
    ) : Elemento()

    data class Firma(
        override val id: String = nuevoId(),
        val etiqueta: String = "Firma",
        val borde: Borde = Borde.LINEA,
    ) : Elemento()
}

data class Plantilla(
    val id: String = nuevoId(),
    val nombre: String = "Nota de entrega",
    val elementos: List<Elemento> = emptyList(),
    /** Largo del ticket en mm. 0 = automático (lo que mida el contenido). El ancho lo da el papel. */
    val largoMm: Int = 0,
    /** Dónde se movió cada elemento (id -> posición). Los que no están siguen el orden normal. */
    val posiciones: Map<String, Posicion> = emptyMap(),
) {
    val campos: List<Elemento.Campo> get() = elementos.filterIsInstance<Elemento.Campo>()
    val tablas: List<Elemento.Tabla> get() = elementos.filterIsInstance<Elemento.Tabla>()
}

/**
 * Posición de un elemento movido a mano, en puntos. [dy] es cuánto se separa (o se sube, si
 * es negativo) del final de lo que está antes; así, si una tabla crece al llenarla, lo de
 * abajo baja con ella. [x] es dónde empieza a la izquierda (null = según su alineación).
 */
data class Posicion(val dy: Int = 0, val x: Int? = null)

/** Elementos que se pueden mover a los lados; los demás ocupan todo el ancho. */
fun admiteX(e: Elemento) = e is Elemento.Texto || e is Elemento.Imagen || e is Elemento.Qr || e is Elemento.Barras

/** Dónde empieza el primer elemento en la tirilla (en puntos). */
const val INICIO_Y = 4f

/**
 * Mueve un elemento a [arriba] (y a [x], si no es null) sin que los demás se muevan de donde
 * se ven: reordena los elementos de arriba hacia abajo y recalcula cuánto se separa cada uno.
 * [zonas] = id -> (arriba, abajo) tal como están dibujados ahora.
 */
fun reubicar(p: Plantilla, zonas: Map<String, Pair<Float, Float>>, id: String, arriba: Float, x: Int?): Plantilla {
    val ordenados = p.elementos.withIndex().sortedWith(
        compareBy<IndexedValue<Elemento>>(
            { if (it.value.id == id) arriba else zonas[it.value.id]?.first ?: Float.MAX_VALUE },
            { if (it.value.id == id) 1 else 0 },
            { it.index },
        ),
    ).map { it.value }
    val nuevas = HashMap<String, Posicion>()
    var cursor = INICIO_Y
    for (e in ordenados) {
        val z = zonas[e.id]
        val top = if (e.id == id) arriba else z?.first ?: cursor
        val alto = z?.let { it.second - it.first } ?: 0f
        val dy = Math.round(top - cursor)
        val px = if (e.id == id && x != null && admiteX(e)) x else p.posiciones[e.id]?.x
        if (dy != 0 || px != null) nuevas[e.id] = Posicion(dy, px)
        cursor = maxOf(cursor, maxOf(0f, cursor + dy) + alto)
    }
    return p.copy(elementos = ordenados, posiciones = nuevas)
}

/** Quita la posición a mano de esos elementos (vuelven a ir uno tras otro). */
fun Plantilla.sinPosicion(vararg ids: String, soloX: Boolean = false): Plantilla = copy(
    posiciones = posiciones.mapNotNull { (k, v) ->
        when {
            k !in ids -> k to v
            soloX -> if (v.dy != 0) k to v.copy(x = null) else null
            else -> null
        }
    }.toMap(),
)

fun nuevoId(): String = UUID.randomUUID().toString().take(8)

/** Lo que el usuario llena en la nota de entrega. */
data class Datos(
    val campos: Map<String, String> = emptyMap(),
    val filas: Map<String, List<List<String>>> = emptyMap(),
)

object PlantillasPorDefecto {
    fun notaDeEntrega() = Plantilla(
        nombre = "Nota de entrega",
        elementos = listOf(
            Elemento.Texto(
                texto = "NOTA DE ENTREGA",
                estilo = Estilo(tamano = 34, negrita = true, invertido = true, alineacion = Alineacion.CENTRO),
            ),
            Elemento.Espacio(alto = 12),
            Elemento.Campo(etiqueta = "Fecha", tipo = TipoCampo.FECHA, forma = FormaCampo.EN_LINEA, borde = Borde.NINGUNO),
            Elemento.Campo(etiqueta = "Hora", tipo = TipoCampo.HORA, forma = FormaCampo.EN_LINEA, borde = Borde.NINGUNO),
            Elemento.Espacio(alto = 8),
            Elemento.Campo(etiqueta = "Establecimiento", forma = FormaCampo.CELDA, borde = Borde.LINEA),
            Elemento.Campo(etiqueta = "Nombre del que retira", forma = FormaCampo.CELDA, borde = Borde.LINEA),
            Elemento.Espacio(alto = 12),
            Elemento.Tabla(),
            Elemento.Espacio(alto = 30),
            Elemento.Firma(etiqueta = "Firma de quien retira", borde = Borde.PUNTILLOS),
            Elemento.Espacio(alto = 10),
        ),
    )
}

// ---------- JSON ----------

object PlantillaJson {

    fun plantillasAJson(lista: List<Plantilla>): String =
        JSONArray().apply { lista.forEach { put(plantilla(it)) } }.toString()

    fun plantillasDesdeJson(texto: String): List<Plantilla> {
        val arr = JSONArray(texto)
        return (0 until arr.length()).map { plantilla(arr.getJSONObject(it)) }
    }

    fun plantilla(p: Plantilla): JSONObject = JSONObject()
        .put("id", p.id)
        .put("nombre", p.nombre)
        .put("largoMm", p.largoMm)
        .put("elementos", JSONArray().apply { p.elementos.forEach { put(elemento(it)) } })
        .put("posiciones", JSONObject().apply {
            p.posiciones.filterKeys { k -> p.elementos.any { it.id == k } }.forEach { (k, v) ->
                put(k, JSONObject().put("dy", v.dy).apply { v.x?.let { put("x", it) } })
            }
        })

    fun plantilla(o: JSONObject): Plantilla {
        val arr = o.optJSONArray("elementos") ?: JSONArray()
        return Plantilla(
            id = o.optString("id", nuevoId()),
            nombre = o.optString("nombre", "Plantilla"),
            elementos = (0 until arr.length()).mapNotNull { elemento(arr.getJSONObject(it)) },
            largoMm = o.optInt("largoMm", 0).coerceIn(0, 1000),
            posiciones = o.optJSONObject("posiciones")?.let { pos ->
                pos.keys().asSequence().associateWith { k ->
                    val v = pos.getJSONObject(k)
                    Posicion(v.optInt("dy", 0), if (v.has("x")) v.getInt("x") else null)
                }
            } ?: emptyMap(),
        )
    }

    private fun estilo(e: Estilo) = JSONObject()
        .put("letra", e.letra.name).put("tamano", e.tamano).put("negrita", e.negrita)
        .put("cursiva", e.cursiva).put("subrayado", e.subrayado).put("invertido", e.invertido)
        .put("alineacion", e.alineacion.name)

    private fun estilo(o: JSONObject?): Estilo {
        if (o == null) return Estilo()
        return Estilo(
            letra = enumDe(o.optString("letra"), Letra.MODERNA),
            tamano = o.optInt("tamano", 24),
            negrita = o.optBoolean("negrita"),
            cursiva = o.optBoolean("cursiva"),
            subrayado = o.optBoolean("subrayado"),
            invertido = o.optBoolean("invertido"),
            alineacion = enumDe(o.optString("alineacion"), Alineacion.IZQUIERDA),
        )
    }

    fun elemento(e: Elemento): JSONObject {
        val o = JSONObject().put("id", e.id)
        when (e) {
            is Elemento.Texto -> o.put("tipo", "texto").put("texto", e.texto).put("estilo", estilo(e.estilo))
            is Elemento.Campo -> o.put("tipo", "campo").put("etiqueta", e.etiqueta).put("tipoCampo", e.tipo.name)
                .put("forma", e.forma.name).put("borde", e.borde.name).put("valorInicial", e.valorInicial)
                .put("estilo", estilo(e.estilo))
            is Elemento.Tabla -> o.put("tipo", "tabla")
                .put("columnas", JSONArray().apply {
                    e.columnas.forEach {
                        put(JSONObject().put("titulo", it.titulo).put("ancho", it.ancho)
                            .put("alineacion", it.alineacion.name).put("numerica", it.numerica))
                    }
                })
                .put("borde", e.borde.name).put("encabezadoInvertido", e.encabezadoInvertido)
                .put("mostrarTotal", e.mostrarTotal).put("filasVacias", e.filasVacias).put("estilo", estilo(e.estilo))
            is Elemento.Separador -> o.put("tipo", "separador").put("borde", e.borde.name).put("grosor", e.grosor)
            is Elemento.Espacio -> o.put("tipo", "espacio").put("alto", e.alto)
            is Elemento.Imagen -> o.put("tipo", "imagen").put("archivo", e.archivo)
                .put("anchoPorcentaje", e.anchoPorcentaje).put("alineacion", e.alineacion.name)
            is Elemento.Qr -> o.put("tipo", "qr").put("contenido", e.contenido).put("tamanoPorcentaje", e.tamanoPorcentaje)
                .put("densidad", e.densidad.name).put("forma", e.forma.name).put("alineacion", e.alineacion.name)
                .put("mostrarTexto", e.mostrarTexto)
            is Elemento.Barras -> o.put("tipo", "barras").put("contenido", e.contenido).put("tipoBarras", e.tipo.name)
                .put("alto", e.alto).put("grosor", e.grosor.name).put("mostrarTexto", e.mostrarTexto)
                .put("alineacion", e.alineacion.name)
            is Elemento.Firma -> o.put("tipo", "firma").put("etiqueta", e.etiqueta).put("borde", e.borde.name)
        }
        return o
    }

    fun elemento(o: JSONObject): Elemento? {
        val id = o.optString("id", nuevoId())
        return when (o.optString("tipo")) {
            "texto" -> Elemento.Texto(id, o.optString("texto"), estilo(o.optJSONObject("estilo")))
            "campo" -> Elemento.Campo(
                id, o.optString("etiqueta"), enumDe(o.optString("tipoCampo"), TipoCampo.TEXTO),
                enumDe(o.optString("forma"), FormaCampo.EN_LINEA), enumDe(o.optString("borde"), Borde.PUNTILLOS),
                o.optString("valorInicial"), estilo(o.optJSONObject("estilo")),
            )
            "tabla" -> {
                val cols = o.optJSONArray("columnas") ?: JSONArray()
                Elemento.Tabla(
                    id,
                    (0 until cols.length()).map {
                        val c = cols.getJSONObject(it)
                        Columna(c.optString("titulo"), c.optInt("ancho", 1).coerceAtLeast(1),
                            enumDe(c.optString("alineacion"), Alineacion.IZQUIERDA), c.optBoolean("numerica"))
                    },
                    enumDe(o.optString("borde"), Borde.LINEA), o.optBoolean("encabezadoInvertido", true),
                    o.optBoolean("mostrarTotal", true), o.optInt("filasVacias", 0), estilo(o.optJSONObject("estilo")),
                )
            }
            "separador" -> Elemento.Separador(id, enumDe(o.optString("borde"), Borde.LINEA), o.optInt("grosor", 2))
            "espacio" -> Elemento.Espacio(id, o.optInt("alto", 20))
            "imagen" -> Elemento.Imagen(id, o.optString("archivo"), o.optInt("anchoPorcentaje", 60),
                enumDe(o.optString("alineacion"), Alineacion.CENTRO))
            "qr" -> Elemento.Qr(id, o.optString("contenido"), o.optInt("tamanoPorcentaje", 45),
                enumDe(o.optString("densidad"), DensidadQr.SENCILLO), enumDe(o.optString("forma"), FormaQr.CUADROS),
                enumDe(o.optString("alineacion"), Alineacion.CENTRO), o.optBoolean("mostrarTexto"))
            "barras" -> Elemento.Barras(id, o.optString("contenido"), enumDe(o.optString("tipoBarras"), TipoBarras.CODE128),
                o.optInt("alto", 80), enumDe(o.optString("grosor"), Grosor.NORMAL), o.optBoolean("mostrarTexto", true),
                enumDe(o.optString("alineacion"), Alineacion.CENTRO))
            "firma" -> Elemento.Firma(id, o.optString("etiqueta"), enumDe(o.optString("borde"), Borde.LINEA))
            else -> null
        }
    }

    private inline fun <reified T : Enum<T>> enumDe(nombre: String, porDefecto: T): T =
        enumValues<T>().firstOrNull { it.name == nombre } ?: porDefecto
}

/** Reemplaza {Nombre del campo} por lo que se llenó. También {fecha}, {hora} y {total}. */
fun reemplazarCampos(texto: String, plantilla: Plantilla, datos: Datos, extras: Map<String, String> = emptyMap()): String {
    if (!texto.contains('{')) return texto
    val valores = HashMap<String, String>()
    extras.forEach { (k, v) -> valores[k.lowercase()] = v }
    plantilla.campos.forEach { c ->
        val clave = c.etiqueta.trim().lowercase()
        val v = datos.campos[c.id]?.takeIf { it.isNotBlank() } ?: c.valorInicial
        // Un campo Fecha u Hora vacío deja la fecha u hora automática de {fecha}/{hora}.
        if (v.isNotBlank() || clave !in valores) valores[clave] = v
    }
    return Regex("\\{([^{}]+)\\}").replace(texto) { m -> valores[m.groupValues[1].trim().lowercase()] ?: m.value }
}

/** Suma la primera columna numérica de la tabla (por ejemplo, el total de unidades). */
fun totalTabla(tabla: Elemento.Tabla, filas: List<List<String>>): String? {
    val col = tabla.columnas.indexOfFirst { it.numerica }
    if (col < 0) return null
    val suma = filas.sumOf { it.getOrNull(col)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0 }
    return if (suma % 1.0 == 0.0) suma.toLong().toString() else "%.2f".format(java.util.Locale.US, suma)
}
