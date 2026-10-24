package com.cuadrecaja.app.impresion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import com.cuadrecaja.app.plantillas.Alineacion
import com.cuadrecaja.app.plantillas.Borde
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.DensidadQr
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Estilo
import com.cuadrecaja.app.plantillas.FormaCampo
import com.cuadrecaja.app.plantillas.FormaQr
import com.cuadrecaja.app.plantillas.Letra
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.plantillas.TipoCampo
import com.cuadrecaja.app.plantillas.reemplazarCampos
import com.cuadrecaja.app.plantillas.totalTabla
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dibuja una plantilla llena como una imagen del ancho del papel (203 ppp: 384 puntos en
 * 58 mm, 576 en 80 mm). La misma imagen se usa para la vista previa y para imprimir, así
 * que lo que se ve es lo que sale.
 */
class Renderizador(private val context: Context) {

    data class Resultado(val imagen: Bitmap, val avisos: List<String>)

    private var ancho = 576
    private var margen = 6
    private val util get() = ancho - margen * 2
    private lateinit var canvas: Canvas
    private var y = 0f
    private val avisos = mutableListOf<String>()

    /** [ejemplo] = true para la vista previa del editor: pone filas de muestra en las tablas. */
    fun dibujar(plantilla: Plantilla, datos: Datos, anchoPuntos: Int, ahora: Date = Date(), ejemplo: Boolean = false): Resultado {
        ancho = anchoPuntos
        avisos.clear()
        val lienzo = Bitmap.createBitmap(ancho, ALTO_MAXIMO, Bitmap.Config.ARGB_8888)
        canvas = Canvas(lienzo)
        canvas.drawColor(Color.WHITE)
        y = 4f

        val extras = mapOf("fecha" to fecha(ahora), "hora" to hora(ahora))
        // En la vista previa del editor, los campos vacíos usados en textos, QR o códigos
        // se muestran con su nombre, para ver cómo quedan.
        val paraReemplazar = if (!ejemplo) datos else datos.copy(
            campos = plantilla.campos.associate { c -> c.id to (datos.campos[c.id]?.takeIf { it.isNotBlank() } ?: c.valorInicial.ifBlank { c.etiqueta }) },
        )
        for (e in plantilla.elementos) {
            if (y > ALTO_MAXIMO - 200) {
                avisos += "La plantilla es demasiado larga; se cortó el final."
                break
            }
            when (e) {
                is Elemento.Texto -> texto(reemplazarCampos(e.texto, plantilla, paraReemplazar, extras), e.estilo)
                is Elemento.Campo -> campo(e, valorCampo(e, datos, ahora))
                is Elemento.Tabla -> tabla(e, filasDe(e, datos, ejemplo))
                is Elemento.Separador -> separador(e)
                is Elemento.Espacio -> y += e.alto
                is Elemento.Imagen -> imagen(e)
                is Elemento.Qr -> qr(e, reemplazarCampos(e.contenido, plantilla, paraReemplazar, extras))
                is Elemento.Barras -> barras(e, reemplazarCampos(e.contenido, plantilla, paraReemplazar, extras))
                is Elemento.Firma -> firma(e)
            }
        }

        val alto = (y + 8).toInt().coerceIn(40, ALTO_MAXIMO)
        val recortada = Bitmap.createBitmap(lienzo, 0, 0, ancho, alto)
        if (recortada != lienzo) lienzo.recycle()
        return Resultado(aBlancoYNegro(recortada), avisos.toList())
    }

    // ---------- Elementos ----------

    private fun texto(t: String, estilo: Estilo) {
        if (t.isEmpty()) return
        val paint = pintura(estilo, if (estilo.invertido) Color.WHITE else Color.BLACK)
        val pad = if (estilo.invertido) 8 else 0
        val layout = diseno(t, paint, util - pad * 2, estilo.alineacion)
        if (estilo.invertido) {
            canvas.drawRect(margen.toFloat(), y, (ancho - margen).toFloat(), y + layout.height + pad * 2, relleno())
        }
        dibujarDiseno(layout, (margen + pad).toFloat(), y + pad)
        y += layout.height + pad * 2 + 4
    }

    private fun valorCampo(c: Elemento.Campo, datos: Datos, ahora: Date): String {
        val v = datos.campos[c.id]?.takeIf { it.isNotBlank() } ?: c.valorInicial
        if (v.isNotBlank()) return v
        return when (c.tipo) {
            TipoCampo.FECHA -> fecha(ahora)
            TipoCampo.HORA -> hora(ahora)
            else -> ""
        }
    }

    private fun campo(c: Elemento.Campo, valor: String) {
        val estilo = c.estilo
        when (c.forma) {
            FormaCampo.EN_LINEA -> {
                val paint = pintura(estilo, Color.BLACK)
                val etiqueta = "${c.etiqueta}: "
                if (valor.isBlank()) {
                    // Sin valor: queda una línea para llenar a mano.
                    val layout = diseno(etiqueta, pintura(estilo.copy(negrita = true), Color.BLACK), util, Alineacion.IZQUIERDA)
                    dibujarDiseno(layout, margen.toFloat(), y)
                    val finEtiqueta = margen + layout.getLineWidth(0) + 4
                    val base = y + layout.getLineBaseline(0) + 3
                    linea(finEtiqueta, base, (ancho - margen).toFloat(), base, bordeOLinea(c.borde), 2f)
                    y += layout.height + 8
                } else {
                    val sb = SpannableStringBuilder(etiqueta + valor)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, etiqueta.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val layout = diseno(sb, paint, util, estilo.alineacion)
                    dibujarDiseno(layout, margen.toFloat(), y)
                    y += layout.height + 8
                }
            }
            FormaCampo.APILADO -> {
                val chica = pintura(estilo.copy(negrita = true, tamano = (estilo.tamano * 0.75f).toInt()), Color.BLACK)
                val l1 = diseno(c.etiqueta, chica, util, estilo.alineacion)
                dibujarDiseno(l1, margen.toFloat(), y)
                y += l1.height + 2
                if (valor.isBlank()) {
                    y += estilo.tamano * 1.3f
                    linea(margen.toFloat(), y, (ancho - margen).toFloat(), y, bordeOLinea(c.borde), 2f)
                    y += 16
                } else {
                    val l2 = diseno(valor, pintura(estilo, Color.BLACK), util, estilo.alineacion)
                    dibujarDiseno(l2, margen.toFloat(), y)
                    y += l2.height + 8
                }
            }
            FormaCampo.CELDA -> {
                val pad = 8
                val top = y
                val chica = pintura(estilo.copy(negrita = true, tamano = (estilo.tamano * 0.65f).toInt().coerceAtLeast(14)), Color.BLACK)
                val l1 = diseno(c.etiqueta.uppercase(), chica, util - pad * 2, Alineacion.IZQUIERDA)
                dibujarDiseno(l1, (margen + pad).toFloat(), top + pad)
                var interior = top + pad + l1.height + 2
                if (valor.isBlank()) {
                    interior += estilo.tamano * 1.3f
                } else {
                    val l2 = diseno(valor, pintura(estilo, Color.BLACK), util - pad * 2, estilo.alineacion)
                    dibujarDiseno(l2, (margen + pad).toFloat(), interior)
                    interior += l2.height
                }
                val bottom = interior + pad
                caja(margen.toFloat(), top, (ancho - margen).toFloat(), bottom, c.borde, 2f)
                y = bottom + 6
            }
        }
    }

    private fun filasDe(t: Elemento.Tabla, datos: Datos, ejemplo: Boolean): List<List<String>> {
        val llenas = datos.filas[t.id].orEmpty().filter { fila -> fila.any { it.isNotBlank() } }
        if (llenas.isNotEmpty() || !ejemplo) return llenas
        val principal = t.columnas.indexOfFirst { !it.numerica }
        return listOf(2, 1).mapIndexed { i, n ->
            t.columnas.mapIndexed { c, col ->
                when {
                    col.numerica -> n.toString()
                    c == principal -> "Producto de ejemplo ${i + 1}"
                    else -> "-"
                }
            }
        }
    }

    private fun tabla(t: Elemento.Tabla, filas: List<List<String>>) {
        if (t.columnas.isEmpty()) return
        val pad = 6
        val izquierda = margen.toFloat()
        val derecha = (ancho - margen).toFloat()
        val pesoTotal = t.columnas.sumOf { it.ancho.coerceAtLeast(1) }
        val xs = mutableListOf(izquierda)
        t.columnas.forEach { xs += xs.last() + (derecha - izquierda) * it.ancho.coerceAtLeast(1) / pesoTotal }
        val grosor = 2f
        val conLineas = t.borde != Borde.NINGUNO

        fun fila(celdas: List<String>, estilo: Estilo, invertida: Boolean) {
            val paint = pintura(estilo, if (invertida) Color.WHITE else Color.BLACK)
            val disenos = t.columnas.mapIndexed { i, col ->
                diseno(celdas.getOrElse(i) { "" }, paint, (xs[i + 1] - xs[i] - pad * 2).toInt().coerceAtLeast(10), col.alineacion)
            }
            val alto = disenos.maxOf { it.height } + pad * 2
            if (invertida) canvas.drawRect(izquierda, y, derecha, y + alto, relleno())
            disenos.forEachIndexed { i, l -> dibujarDiseno(l, xs[i] + pad, y + pad) }
            if (conLineas && !invertida) {
                for (i in 1 until xs.size - 1) linea(xs[i], y, xs[i], y + alto, t.borde, grosor)
            }
            y += alto
            if (conLineas) linea(izquierda, y, derecha, y, t.borde, grosor)
        }

        val inicio = y
        if (conLineas && !t.encabezadoInvertido) linea(izquierda, y, derecha, y, t.borde, grosor)
        fila(t.columnas.map { it.titulo }, t.estilo.copy(negrita = true), t.encabezadoInvertido)
        if (!conLineas && !t.encabezadoInvertido) linea(izquierda, y, derecha, y, Borde.LINEA, grosor)
        filas.forEach { fila(it, t.estilo, false) }
        repeat(t.filasVacias.coerceIn(0, 30)) { fila(t.columnas.map { "" }, t.estilo, false) }

        if (t.mostrarTotal && filas.isNotEmpty()) {
            val total = totalTabla(t, filas)
            val colNum = t.columnas.indexOfFirst { it.numerica }
            if (total != null) {
                val colTexto = t.columnas.indices.firstOrNull { it != colNum } ?: colNum
                val celdas = t.columnas.indices.map {
                    when (it) { colNum -> total; colTexto -> "TOTAL"; else -> "" }
                }
                fila(celdas, t.estilo.copy(negrita = true), false)
            }
        }
        if (conLineas) {
            linea(izquierda, inicio, izquierda, y, t.borde, grosor)
            linea(derecha, inicio, derecha, y, t.borde, grosor)
        }
        y += 6
    }

    private fun separador(s: Elemento.Separador) {
        y += 6
        if (s.borde != Borde.NINGUNO) linea(margen.toFloat(), y, (ancho - margen).toFloat(), y, s.borde, s.grosor.toFloat())
        y += s.grosor + (if (s.borde == Borde.DOBLE) s.grosor * 2 + 4 else 0) + 6
    }

    private fun imagen(i: Elemento.Imagen) {
        val archivo = File(Imagenes.carpeta(context), i.archivo)
        val original = if (i.archivo.isNotBlank() && archivo.exists()) BitmapFactory.decodeFile(archivo.path) else null
        if (original == null) {
            avisos += "Falta la imagen de un elemento Imagen."
            return
        }
        val w = (util * i.anchoPorcentaje.coerceIn(10, 100) / 100f).toInt().coerceAtLeast(8)
        val h = (original.height * w / original.width.toFloat()).toInt().coerceAtLeast(1)
        val escalada = Bitmap.createScaledBitmap(original, w, h, true)
        val mono = Imagenes.tramar(escalada)
        canvas.drawBitmap(mono, xPara(w, i.alineacion), y, null)
        y += h + 6
    }

    private fun qr(q: Elemento.Qr, contenido: String) {
        if (contenido.isBlank()) {
            avisos += "Un código QR no tiene contenido."
            return
        }
        val nivel = when (q.densidad) {
            DensidadQr.LIGERO -> ErrorCorrectionLevel.L
            DensidadQr.SENCILLO -> ErrorCorrectionLevel.M
            DensidadQr.ESPESO -> ErrorCorrectionLevel.H
        }
        val matriz = try {
            QRCodeWriter().encode(
                contenido, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.ERROR_CORRECTION to nivel, EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8"),
            )
        } catch (e: Exception) {
            avisos += "No se pudo crear el código QR (contenido muy largo)."
            return
        }
        val objetivo = util * q.tamanoPorcentaje.coerceIn(15, 100) / 100
        val modulo = (objetivo / matriz.width).coerceAtLeast(2)
        val lado = modulo * matriz.width
        val x0 = xPara(lado, q.alineacion)
        val negro = relleno()
        for (my in 0 until matriz.height) for (mx in 0 until matriz.width) {
            if (!matriz.get(mx, my)) continue
            val l = x0 + mx * modulo
            val t = y + my * modulo
            if (q.forma == FormaQr.PUNTOS && !esOjoQr(mx, my, matriz.width)) {
                canvas.drawCircle(l + modulo / 2f, t + modulo / 2f, modulo * 0.48f, negro)
            } else {
                canvas.drawRect(l, t, l + modulo, t + modulo, negro)
            }
        }
        y += lado + 6
        if (q.mostrarTexto) texto(contenido, Estilo(tamano = 18, alineacion = q.alineacion))
    }

    /** Los tres cuadros grandes de las esquinas se dejan cuadrados para que el QR se lea bien. */
    private fun esOjoQr(x: Int, y: Int, n: Int) =
        (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)

    private fun barras(b: Elemento.Barras, contenido: String) {
        val formato = when (b.tipo) {
            TipoBarras.CODE128 -> BarcodeFormat.CODE_128
            TipoBarras.CODE39 -> BarcodeFormat.CODE_39
            TipoBarras.CODE93 -> BarcodeFormat.CODE_93
            TipoBarras.EAN13 -> BarcodeFormat.EAN_13
            TipoBarras.EAN8 -> BarcodeFormat.EAN_8
            TipoBarras.UPCA -> BarcodeFormat.UPC_A
            TipoBarras.ITF -> BarcodeFormat.ITF
            TipoBarras.CODABAR -> BarcodeFormat.CODABAR
        }
        val matriz = try {
            MultiFormatWriter().encode(contenido, formato, 1, 1, mapOf(EncodeHintType.MARGIN to 0))
        } catch (e: Exception) {
            avisos += "Código de barras ${b.tipo.etiqueta}: \"$contenido\" no es válido (${b.tipo.ayuda})."
            return
        }
        val objetivo = util * b.grosor.porcentaje / 100
        val modulo = objetivo / matriz.width
        if (modulo < 1) {
            avisos += "El código de barras es demasiado largo para el ancho del papel."
            return
        }
        val w = modulo * matriz.width
        val x0 = xPara(w, b.alineacion)
        val alto = b.alto.coerceIn(30, 300)
        val negro = relleno()
        var x = 0
        while (x < matriz.width) {
            if (matriz.get(x, 0)) {
                var fin = x
                while (fin < matriz.width && matriz.get(fin, 0)) fin++
                canvas.drawRect(x0 + x * modulo, y, x0 + fin * modulo, y + alto, negro)
                x = fin
            } else x++
        }
        y += alto + 4
        if (b.mostrarTexto) {
            val paint = pintura(Estilo(letra = Letra.MAQUINA, tamano = 20), Color.BLACK)
            val l = diseno(contenido, paint, w.coerceAtLeast(40), Alineacion.CENTRO)
            dibujarDiseno(l, x0, y)
            y += l.height
        }
        y += 6
    }

    private fun firma(f: Elemento.Firma) {
        y += 70
        val w = util * 0.75f
        val x0 = xPara(w.toInt(), Alineacion.CENTRO)
        linea(x0, y, x0 + w, y, bordeOLinea(f.borde), 2f)
        y += 6
        texto(f.etiqueta, Estilo(tamano = 20, alineacion = Alineacion.CENTRO))
    }

    // ---------- Ayudantes de dibujo ----------

    private fun pintura(e: Estilo, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        val estilo = when {
            e.negrita && e.cursiva -> Typeface.BOLD_ITALIC
            e.negrita -> Typeface.BOLD
            e.cursiva -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        typeface = Typeface.create(Typeface.create(e.letra.familia, Typeface.NORMAL), estilo)
        textSize = e.tamano.coerceIn(10, 120).toFloat()
        isUnderlineText = e.subrayado
        this.color = color
    }

    private fun diseno(texto: CharSequence, paint: TextPaint, ancho: Int, alineacion: Alineacion): StaticLayout =
        StaticLayout.Builder.obtain(texto, 0, texto.length, paint, ancho.coerceAtLeast(1))
            .setAlignment(
                when (alineacion) {
                    Alineacion.IZQUIERDA -> Layout.Alignment.ALIGN_NORMAL
                    Alineacion.CENTRO -> Layout.Alignment.ALIGN_CENTER
                    Alineacion.DERECHA -> Layout.Alignment.ALIGN_OPPOSITE
                },
            )
            .setIncludePad(false)
            .build()

    private fun dibujarDiseno(l: StaticLayout, x: Float, yArriba: Float) {
        canvas.save()
        canvas.translate(x, yArriba)
        l.draw(canvas)
        canvas.restore()
    }

    private fun relleno() = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }

    private fun bordeOLinea(b: Borde) = if (b == Borde.NINGUNO) Borde.PUNTILLOS else b

    private fun xPara(w: Int, a: Alineacion): Float = when (a) {
        Alineacion.IZQUIERDA -> margen.toFloat()
        Alineacion.CENTRO -> ((ancho - w) / 2).toFloat()
        Alineacion.DERECHA -> (ancho - margen - w).toFloat()
    }

    private fun linea(x1: Float, y1: Float, x2: Float, y2: Float, borde: Borde, grosor: Float) {
        if (borde == Borde.NINGUNO) return
        val p = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = grosor
            pathEffect = when (borde) {
                Borde.PUNTILLOS -> DashPathEffect(floatArrayOf(grosor * 1.5f, grosor * 2.5f), 0f)
                Borde.GUIONES -> DashPathEffect(floatArrayOf(12f, 8f), 0f)
                else -> null
            }
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        canvas.drawPath(path, p)
        if (borde == Borde.DOBLE) {
            val d = grosor + 3
            val vertical = x1 == x2
            canvas.drawPath(Path().apply {
                if (vertical) { moveTo(x1 + d, y1); lineTo(x2 + d, y2) } else { moveTo(x1, y1 + d); lineTo(x2, y2 + d) }
            }, p)
        }
    }

    private fun caja(l: Float, t: Float, r: Float, b: Float, borde: Borde, grosor: Float) {
        linea(l, t, r, t, borde, grosor)
        linea(l, b, r, b, borde, grosor)
        linea(l, t, l, b, borde, grosor)
        linea(r, t, r, b, borde, grosor)
    }

    /** Deja solo puntos negros y blancos, como los imprime la tirilla térmica. */
    private fun aBlancoYNegro(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        for (i in px.indices) px[i] = if (Imagenes.esNegro(px[i])) Color.BLACK else Color.WHITE
        b.setPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return b
    }

    companion object {
        const val ALTO_MAXIMO = 6000

        fun fecha(d: Date): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d)
        fun hora(d: Date): String = SimpleDateFormat("hh:mm a", Locale.US).format(d)

        /** Puntos negros de la imagen, para mandarla a la impresora. */
        fun puntosNegros(b: Bitmap): BooleanArray {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            return BooleanArray(px.size) { Imagenes.esNegro(px[it]) }
        }
    }
}
