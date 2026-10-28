package com.cuadrecaja.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Alineacion
import com.cuadrecaja.app.plantillas.Borde
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Estilo
import com.cuadrecaja.app.plantillas.FormaCampo
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoCampo
import com.cuadrecaja.app.plantillas.admiteX
import com.cuadrecaja.app.plantillas.reubicar
import com.cuadrecaja.app.plantillas.sinPosicion
import com.cuadrecaja.app.plantillas.nuevoId
import com.cuadrecaja.app.ui.theme.LocalPaleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val GrisTexto = Color(0xFF4B5563)
private val GrisSuave = Color(0xFFF3F4F6)
private val FondoLienzo = Color(0xFFECEDF2)

/** Un botón del panel de abajo: qué dibuja y qué elemento agrega. */
private class Herramienta(val icono: Icono, val nombre: String, val crear: (() -> Elemento)?)

private val paginasHerramientas = listOf(
    listOf(
        Herramienta(Icono.TEXTO, "Texto") { Elemento.Texto(texto = "Texto") },
        Herramienta(Icono.QR, "Código QR") { Elemento.Qr() },
        Herramienta(Icono.BARRAS, "Código de barras") { Elemento.Barras() },
        Herramienta(Icono.IMAGEN, "Imagen", null), // abre la galería
        Herramienta(Icono.TABLA, "Tabla") { Elemento.Tabla() },
        Herramienta(Icono.CAMPO, "Campo") { Elemento.Campo(etiqueta = "Nuevo campo") },
        Herramienta(Icono.LINEA, "Línea") { Elemento.Separador() },
        Herramienta(Icono.FIRMA, "Firma") { Elemento.Firma() },
    ),
    listOf(
        Herramienta(Icono.TITULO, "Título") {
            Elemento.Texto(texto = "TÍTULO", estilo = Estilo(tamano = 34, negrita = true, invertido = true, alineacion = Alineacion.CENTRO))
        },
        Herramienta(Icono.CELDA, "Celda") { Elemento.Campo(etiqueta = "Nueva celda", forma = FormaCampo.CELDA, borde = Borde.LINEA) },
        Herramienta(Icono.FECHA, "Fecha") { Elemento.Campo(etiqueta = "Fecha", tipo = TipoCampo.FECHA, borde = Borde.NINGUNO) },
        Herramienta(Icono.HORA, "Hora") { Elemento.Campo(etiqueta = "Hora", tipo = TipoCampo.HORA, borde = Borde.NINGUNO) },
        Herramienta(Icono.PARRAFO, "Párrafo") { Elemento.Campo(etiqueta = "Observaciones", tipo = TipoCampo.PARRAFO, forma = FormaCampo.APILADO) },
        Herramienta(Icono.ESPACIO, "Espacio") { Elemento.Espacio() },
    ),
)

internal fun iconoDe(e: Elemento) = when (e) {
    is Elemento.Texto -> if (e.estilo.invertido) Icono.TITULO else Icono.TEXTO
    is Elemento.Campo -> when {
        e.tipo == TipoCampo.FECHA -> Icono.FECHA
        e.tipo == TipoCampo.HORA -> Icono.HORA
        e.forma == FormaCampo.CELDA -> Icono.CELDA
        e.tipo == TipoCampo.PARRAFO -> Icono.PARRAFO
        else -> Icono.CAMPO
    }
    is Elemento.Tabla -> Icono.TABLA
    is Elemento.Separador -> Icono.LINEA
    is Elemento.Espacio -> Icono.ESPACIO
    is Elemento.Imagen -> Icono.IMAGEN
    is Elemento.Qr -> Icono.QR
    is Elemento.Barras -> Icono.BARRAS
    is Elemento.Firma -> Icono.FIRMA
}

private fun conIdNuevo(e: Elemento): Elemento = when (e) {
    is Elemento.Texto -> e.copy(id = nuevoId())
    is Elemento.Campo -> e.copy(id = nuevoId(), etiqueta = e.etiqueta + " 2")
    is Elemento.Tabla -> e.copy(id = nuevoId())
    is Elemento.Separador -> e.copy(id = nuevoId())
    is Elemento.Espacio -> e.copy(id = nuevoId())
    is Elemento.Imagen -> e.copy(id = nuevoId())
    is Elemento.Qr -> e.copy(id = nuevoId())
    is Elemento.Barras -> e.copy(id = nuevoId())
    is Elemento.Firma -> e.copy(id = nuevoId())
}

private fun alineacionDe(e: Elemento): Alineacion? = when (e) {
    is Elemento.Texto -> e.estilo.alineacion
    is Elemento.Imagen -> e.alineacion
    is Elemento.Qr -> e.alineacion
    is Elemento.Barras -> e.alineacion
    else -> null
}

/** Al arrastrar: dónde se tomó (respecto a la esquina del elemento) y dónde está el dedo, en puntos. */
private data class Arrastre(val id: String, val tomadoX: Float, val tomadoY: Float, val x: Float, val y: Float)

/** Guía de alineación que se muestra al arrastrar: una línea de (x1, y1) a (x2, y2). */
private data class Guia(val clave: String, val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** Dónde queda lo que se arrastra (ya pegado a las guías) y qué guías se muestran. */
private data class Colocacion(val izquierda: Float, val arriba: Float, val guias: List<Guia>)

/**
 * Pega el elemento a lo que tiene cerca: a la misma altura que otro (alineación horizontal)
 * o al mismo borde o centro que el de arriba o abajo (alineación vertical).
 */
private fun colocar(
    z: Renderizador.Zona,
    izquierda: Float,
    arriba: Float,
    otras: List<Renderizador.Zona>,
    anchoPapel: Float,
    umbral: Float,
    aLosLados: Boolean,
): Colocacion {
    val w = z.derecha - z.izquierda
    val h = maxOf(z.abajo - z.arriba, 16f)
    var l = izquierda
    var t = arriba
    val guias = mutableListOf<Guia>()
    val anchoCompleto = anchoPapel * 0.9f

    // Misma altura que un elemento que está a un lado (arriba, centro o abajo).
    var mejorY: Triple<Float, Renderizador.Zona, String>? = null
    for (o in otras) {
        if (o.derecha - o.izquierda >= anchoCompleto) continue
        val oh = maxOf(o.abajo - o.arriba, 16f)
        listOf(
            Triple(o.arriba - t, "arriba", o.arriba),
            Triple((o.arriba + oh / 2) - (t + h / 2), "centro", o.arriba + oh / 2),
            Triple(o.abajo - (t + h), "abajo", o.abajo),
        ).forEach { (d, tipo, _) ->
            if (kotlin.math.abs(d) <= umbral && (mejorY == null || kotlin.math.abs(d) < kotlin.math.abs(mejorY!!.first))) mejorY = Triple(d, o, tipo)
        }
    }
    mejorY?.let { (d, o, tipo) ->
        t += d
        val yGuia = when (tipo) { "arriba" -> t; "centro" -> t + h / 2; else -> t + h }
        guias += Guia("h:${o.id}:$tipo", minOf(l, o.izquierda) - 6, yGuia, maxOf(l + w, o.derecha) + 6, yGuia)
    }

    // Mismo borde izquierdo, centro o borde derecho que uno de arriba o de abajo.
    if (aLosLados) {
        var mejorX: Triple<Float, Renderizador.Zona, String>? = null
        var distanciaMejor = Float.MAX_VALUE
        for (o in otras) {
            if (o.derecha - o.izquierda >= anchoCompleto) continue
            val lejos = when {
                o.abajo <= t -> t - o.abajo
                o.arriba >= t + h -> o.arriba - (t + h)
                else -> continue // está al lado, no arriba ni abajo
            }
            listOf(
                o.izquierda - l to "izquierda",
                (o.izquierda + o.derecha) / 2 - (l + w / 2) to "centro",
                o.derecha - (l + w) to "derecha",
            ).forEach { (d, tipo) ->
                val a = kotlin.math.abs(d)
                if (a <= umbral && (mejorX == null || a < kotlin.math.abs(mejorX!!.first) - 0.5f ||
                        (a <= kotlin.math.abs(mejorX!!.first) + 0.5f && lejos < distanciaMejor))) {
                    mejorX = Triple(d, o, tipo)
                    distanciaMejor = lejos
                }
            }
        }
        // El centro del papel también sirve de guía.
        val alCentro = anchoPapel / 2 - (l + w / 2)
        if (mejorX == null && kotlin.math.abs(alCentro) <= umbral) {
            l += alCentro
            guias += Guia("v:papel", anchoPapel / 2, t - 14, anchoPapel / 2, t + h + 14)
        }
        mejorX?.let { (d, o, tipo) ->
            l += d
            val xGuia = when (tipo) { "izquierda" -> l; "centro" -> l + w / 2; else -> l + w }
            // Una línea corta que une los dos elementos.
            val y1 = if (o.abajo <= t) o.abajo - 10 else t + h + 10
            val y2 = if (o.abajo <= t) t + 10 else o.arriba + 10
            guias += Guia("v:${o.id}:$tipo", xGuia, minOf(y1, y2) - 4, xGuia, maxOf(y1, y2) + 4)
        }
    }
    return Colocacion(l, t, guias)
}

/**
 * Editor visual de plantillas: el ticket en el centro tal como se imprime, tocar un
 * elemento para editarlo, mantenerlo presionado para moverlo y el panel de abajo para
 * agregar cosas. Los cambios se guardan solos; deshacer/rehacer arriba.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EditorVisualScreen(
    plantilla: Plantilla,
    papelMm: Int,
    onCambio: (Plantilla) -> Unit,
    renderizar: (Plantilla) -> Renderizador.Resultado,
    guardarImagen: (Uri) -> String?,
    onListo: () -> Unit,
    onLlenar: () -> Unit,
    /** Para "Mensaje": lo mismo que el editor, pero no es una plantilla; solo se imprime. */
    modoMensaje: Boolean = false,
    estado: String = "",
    imprimiendo: Boolean = false,
    seleccionInicial: String? = null,
) {
    val paleta = LocalPaleta.current
    var seleccion by remember { mutableStateOf(seleccionInicial) }
    // En un mensaje no hay nada que llenar después: sin campos ni tablas.
    val paginas = if (!modoMensaje) paginasHerramientas else paginasHerramientas.flatten()
        .filter { it.nombre !in setOf("Campo", "Tabla", "Celda", "Párrafo") }
        .chunked(8)
    val deshacer = remember(plantilla.id) { mutableStateListOf<Plantilla>() }
    val rehacer = remember(plantilla.id) { mutableStateListOf<Plantilla>() }
    var ultimaEdicion by remember { mutableStateOf<Pair<String?, Long>>(null to 0L) }
    var renombrar by remember { mutableStateOf(false) }
    var elegirLargo by remember { mutableStateOf(false) }
    var limpiar by remember { mutableStateOf(false) }
    var irA by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    var escala by remember { mutableFloatStateOf(1f) }
    val actual by rememberUpdatedState(plantilla)

    BackHandler(enabled = seleccion != null) { seleccion = null }

    fun cambiar(nueva: Plantilla, elementoId: String? = null) {
        val ahora = SystemClock.uptimeMillis()
        // Escribir en el mismo elemento seguido cuenta como un solo cambio para deshacer.
        val agrupar = elementoId != null && ultimaEdicion.first == elementoId && ahora - ultimaEdicion.second < 1500
        if (!agrupar) {
            deshacer += actual
            if (deshacer.size > 60) deshacer.removeAt(0)
        }
        rehacer.clear()
        ultimaEdicion = elementoId to ahora
        onCambio(nueva)
    }

    fun agregar(e: Elemento) {
        cambiar(actual.copy(elementos = actual.elementos + e))
        seleccion = e.id
        irA = e.id
    }

    // Se dibuja aparte para que escribir no se trabe; mientras, se ve el dibujo anterior.
    var resultado by remember { mutableStateOf<Renderizador.Resultado?>(null) }
    LaunchedEffect(plantilla, papelMm) {
        if (resultado != null) delay(70)
        resultado = withContext(Dispatchers.Default) { renderizar(plantilla) }
    }
    val imagenBitmap = remember(resultado) { resultado?.imagen?.asImageBitmap() }

    // Al agregar un elemento, bajar hasta él.
    LaunchedEffect(irA, resultado) {
        val id = irA ?: return@LaunchedEffect
        val zona = resultado?.zonas?.firstOrNull { it.id == id } ?: return@LaunchedEffect
        scroll.animateScrollTo(((zona.arriba * escala) - 120).toInt().coerceAtLeast(0))
        irA = null
    }

    val elegirImagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) guardarImagen(uri)?.let { agregar(Elemento.Imagen(archivo = it)) }
    }

    Column(Modifier.fillMaxSize().background(FondoLienzo)) {
        // ---------- Barra superior: deshacer, rehacer, nombre, limpiar ----------
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                deshacer.removeLastOrNull()?.let { anterior -> rehacer += actual; onCambio(anterior); ultimaEdicion = null to 0L }
            }, enabled = deshacer.isNotEmpty()) {
                IconoLinea(Icono.DESHACER, if (deshacer.isNotEmpty()) GrisTexto else Color(0xFFCBD5E1), tamano = 26.dp)
            }
            IconButton(onClick = {
                rehacer.removeLastOrNull()?.let { siguiente -> deshacer += actual; onCambio(siguiente); ultimaEdicion = null to 0L }
            }, enabled = rehacer.isNotEmpty()) {
                IconoLinea(Icono.REHACER, if (rehacer.isNotEmpty()) GrisTexto else Color(0xFFCBD5E1), tamano = 26.dp)
            }
            Text(
                if (modoMensaje) "Mensaje" else plantilla.nombre + "  ✎",
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(enabled = !modoMensaje) { renombrar = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            IconButton(onClick = { limpiar = true }, enabled = plantilla.elementos.isNotEmpty()) {
                IconoLinea(Icono.LIMPIAR, GrisTexto, tamano = 26.dp)
            }
        }
        HorizontalDivider(color = Color(0xFFE5E7EB))
        Row(
            Modifier.fillMaxWidth().background(Color.White).clickable { elegirLargo = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoLinea(Icono.PAPEL, GrisTexto, tamano = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Papel $papelMm mm · " + if (plantilla.largoMm == 0) "largo automático" else "largo ${plantilla.largoMm} mm",
                fontSize = 16.sp,
            )
            Text("  ⌄", fontSize = 18.sp, color = GrisTexto)
        }

        // ---------- El ticket ----------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 18.dp)) {
                if (imagenBitmap != null && resultado != null) {
                    Lienzo(
                        imagen = imagenBitmap,
                        zonas = resultado!!.zonas,
                        seleccion = seleccion,
                        vacia = plantilla.elementos.isEmpty(),
                        onEscala = { escala = it },
                        onTocar = { seleccion = it },
                        movibles = plantilla.elementos.filter(::admiteX).map { it.id }.toSet(),
                        onMover = { id, arriba, x ->
                            val zonas = resultado?.zonas.orEmpty().associate { it.id to (it.arriba to it.abajo) }
                            val nueva = reubicar(actual, zonas, id, arriba, x)
                            if (nueva != actual) cambiar(nueva)
                        },
                    )
                    resultado!!.avisos.forEach {
                        Spacer(Modifier.height(10.dp))
                        Text("⚠️  $it", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF7ED)).padding(10.dp), color = Color(0xFF9A3412), fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (seleccion == null) "Toque un elemento para editarlo · manténgalo presionado para moverlo"
                    else "Mantenga presionado y arrástrelo a donde quiera; se pega a lo que tiene cerca",
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp,
                )
            }
        }

        // ---------- Panel de abajo ----------
        val elegido = plantilla.elementos.firstOrNull { it.id == seleccion }
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color.White, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
        ) {
            if (elegido == null) {
                val pager = rememberPagerState { paginas.size }
                HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 14.dp)) { pagina ->
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        maxItemsInEachRow = 4,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        paginas[pagina].forEach { h ->
                            BotonHerramienta(h.icono, h.nombre, Modifier.weight(1f)) {
                                val crear = h.crear
                                if (crear == null) elegirImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else agregar(crear())
                            }
                        }
                        // Completar la última fila para que los botones no se estiren.
                        repeat((4 - paginas[pagina].size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(paginas.size) { i ->
                        Box(
                            Modifier.padding(3.dp).size(width = if (pager.currentPage == i) 18.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape).background(if (pager.currentPage == i) GrisTexto else Color(0xFFD1D5DB)),
                        )
                    }
                }
                if (modoMensaje && estado.isNotEmpty()) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) { EstadoImpresion(estado) }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onListo,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = CircleShape,
                    ) { Text("Listo", fontSize = 17.sp) }
                    Button(
                        onClick = onLlenar,
                        enabled = !imprimiendo,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = CircleShape,
                    ) { Text(if (imprimiendo) "Imprimiendo…" else "🖨  Imprimir", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            } else {
                val i = plantilla.elementos.indexOf(elegido)
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(paleta.suave), contentAlignment = Alignment.Center) {
                        IconoLinea(iconoDe(elegido), paleta.oscuro, tamano = 22.dp)
                    }
                    Text(
                        nombreDe(elegido), Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    AccionPanel(Icono.SUBIR, "Subir", i > 0) {
                        val l = plantilla.elementos.toMutableList(); l.add(i - 1, l.removeAt(i))
                        cambiar(plantilla.copy(elementos = l).sinPosicion(l[i - 1].id, l[i].id))
                    }
                    AccionPanel(Icono.BAJAR, "Bajar", i < plantilla.elementos.lastIndex) {
                        val l = plantilla.elementos.toMutableList(); l.add(i + 1, l.removeAt(i))
                        cambiar(plantilla.copy(elementos = l).sinPosicion(l[i].id, l[i + 1].id))
                    }
                    AccionPanel(Icono.COPIAR, "Duplicar", true) {
                        val copia = conIdNuevo(elegido)
                        val l = plantilla.elementos.toMutableList(); l.add(i + 1, copia); cambiar(plantilla.copy(elementos = l))
                        seleccion = copia.id
                    }
                    AccionPanel(Icono.BORRAR, "Eliminar", true, Color(0xFFDC2626)) {
                        cambiar(plantilla.copy(elementos = plantilla.elementos.filter { it.id != elegido.id }).sinPosicion(elegido.id))
                        seleccion = null
                    }
                    if (modoMensaje) AccionPanel(Icono.IMPRESORA, "Imprimir", !imprimiendo, paleta.principal) { onLlenar() }
                    AccionPanel(Icono.CERRAR, "Listo", true) { seleccion = null }
                }
                HorizontalDivider(Modifier.padding(top = 6.dp), color = Color(0xFFE5E7EB))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (modoMensaje && estado.isNotEmpty()) EstadoImpresion(estado)
                    EditorElemento(elegido, plantilla, { nuevo ->
                        var p = plantilla.copy(elementos = plantilla.elementos.map { e -> if (e.id == nuevo.id) nuevo else e })
                        // Elegir una alineación quita la posición a mano a los lados.
                        if (alineacionDe(nuevo) != alineacionDe(elegido)) p = p.sinPosicion(nuevo.id, soloX = true)
                        cambiar(p, nuevo.id)
                    }, guardarImagen)
                }
            }
        }
    }

    if (renombrar) {
        var nombre by remember { mutableStateOf(plantilla.nombre) }
        AlertDialog(
            onDismissRequest = { renombrar = false },
            title = { Text("Nombre de la plantilla") },
            text = { OutlinedTextField(nombre, { nombre = it }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    if (nombre.isNotBlank()) cambiar(plantilla.copy(nombre = nombre.trim()))
                    renombrar = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renombrar = false }) { Text("Cancelar") } },
        )
    }

    if (limpiar) {
        AlertDialog(
            onDismissRequest = { limpiar = false },
            title = { Text("Limpiar plantilla") },
            text = { Text("¿Quitar todos los elementos? Puede deshacerlo con la flecha de arriba.") },
            confirmButton = {
                TextButton(onClick = {
                    cambiar(plantilla.copy(elementos = emptyList()))
                    seleccion = null
                    limpiar = false
                }) { Text("Quitar todo") }
            },
            dismissButton = { TextButton(onClick = { limpiar = false }) { Text("Cancelar") } },
        )
    }

    if (elegirLargo) {
        val contenidoMm = (resultado?.zonas?.lastOrNull()?.abajo ?: 0f) / Renderizador.PUNTOS_POR_MM
        DialogoLargo(plantilla.largoMm, papelMm, contenidoMm.toInt(), onCerrar = { elegirLargo = false }) {
            cambiar(plantilla.copy(largoMm = it))
        }
    }
}

@Composable
private fun AccionPanel(icono: Icono, descripcion: String, activo: Boolean, color: Color = GrisTexto, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = activo, modifier = Modifier.semantics { contentDescription = descripcion }) {
        IconoLinea(icono, if (activo) color else Color(0xFFCBD5E1), tamano = 22.dp)
    }
}

@Composable
private fun BotonHerramienta(icono: Icono, nombre: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(GrisSuave), contentAlignment = Alignment.Center) {
            IconoLinea(icono, Color(0xFF1F2937), tamano = 28.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(nombre, fontSize = 13.sp, color = GrisTexto, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
    }
}

/**
 * El ticket dibujado con un recuadro punteado por elemento. Tocar elige; mantener presionado
 * y arrastrar lo mueve a donde se quiera (los de todo el ancho, solo arriba o abajo). Cerca de
 * otro elemento aparece una guía y vibra corto: se pega a la misma altura o al mismo borde.
 */
@Composable
private fun Lienzo(
    imagen: ImageBitmap,
    zonas: List<Renderizador.Zona>,
    seleccion: String?,
    vacia: Boolean,
    movibles: Set<String>,
    onEscala: (Float) -> Unit,
    onTocar: (String?) -> Unit,
    onMover: (id: String, arriba: Float, x: Int?) -> Unit,
) {
    val paleta = LocalPaleta.current
    val contexto = LocalContext.current
    val zonasActuales by rememberUpdatedState(zonas)
    val movilesActuales by rememberUpdatedState(movibles)
    var arrastre by remember { mutableStateOf<Arrastre?>(null) }
    var colocacion by remember { mutableStateOf<Colocacion?>(null) }
    val colorGuia = Color(0xFFE11D48)

    /** El elemento más chico que está bajo el dedo (si hay dos en la misma línea, el que se tocó). */
    fun zonaEn(x: Float, y: Float): Renderizador.Zona? = zonasActuales
        .filter { y >= it.arriba && y <= maxOf(it.abajo, it.arriba + 16f) && x >= it.izquierda - 12 && x <= it.derecha + 12 }
        .minByOrNull { (it.derecha - it.izquierda) * maxOf(it.abajo - it.arriba, 16f) }
        ?: zonasActuales.firstOrNull { y >= it.arriba && y <= maxOf(it.abajo, it.arriba + 16f) }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(10.dp))
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) { BoxWithConstraints(Modifier.fillMaxWidth()) {
        val anchoPx = constraints.maxWidth.toFloat()
        val escala = anchoPx / imagen.width
        LaunchedEffect(escala) { onEscala(escala) }
        val densidad = LocalDensity.current
        val umbral = with(densidad) { 9.dp.toPx() } / escala
        val alto = with(densidad) { (imagen.height * escala).toDp() }.coerceAtLeast(if (vacia) 220.dp else 0.dp)

        fun mover(a: Arrastre) {
            val z = zonasActuales.firstOrNull { it.id == a.id } ?: return
            val aLosLados = a.id in movilesActuales
            val anterior = colocacion
            val nueva = colocar(
                z,
                izquierda = if (aLosLados) a.x - a.tomadoX else z.izquierda,
                arriba = a.y - a.tomadoY,
                otras = zonasActuales.filter { it.id != a.id },
                anchoPapel = imagen.width.toFloat(),
                umbral = umbral,
                aLosLados = aLosLados,
            )
            // Vibra cortito cada vez que aparece una guía nueva.
            val antes = anterior?.guias.orEmpty().map { it.clave }.toSet()
            if (nueva.guias.any { it.clave !in antes }) vibrarCorto(contexto)
            colocacion = nueva
        }

        Box(Modifier.fillMaxWidth().height(alto)) {
            Image(
                imagen,
                contentDescription = "Ticket",
                modifier = Modifier.fillMaxWidth().height(with(densidad) { (imagen.height * escala).toDp() }),
                contentScale = ContentScale.FillWidth,
                filterQuality = FilterQuality.Low,
            )
            if (vacia) {
                Text(
                    "Plantilla vacía\nToque un botón de abajo para agregar texto, QR, códigos de barras, imágenes…",
                    Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF9CA3AF),
                )
            }
            Canvas(
                Modifier
                    .matchParentSize()
                    .testTag("lienzo")
                    .pointerInput(escala) {
                        detectTapGestures { pos -> onTocar(zonaEn(pos.x / escala, pos.y / escala)?.id) }
                    }
                    .pointerInput(escala) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                val x = pos.x / escala
                                val y = pos.y / escala
                                val z = zonaEn(x, y) ?: return@detectDragGesturesAfterLongPress
                                onTocar(z.id)
                                val a = Arrastre(z.id, x - z.izquierda, y - z.arriba, x, y)
                                arrastre = a
                                colocacion = null
                                mover(a)
                            },
                            onDrag = { cambio, d ->
                                cambio.consume()
                                arrastre = arrastre?.let { it.copy(x = it.x + d.x / escala, y = it.y + d.y / escala) }?.also(::mover)
                            },
                            onDragEnd = {
                                val a = arrastre
                                val c = colocacion
                                arrastre = null
                                colocacion = null
                                val z = zonasActuales.firstOrNull { it.id == a?.id }
                                if (a != null && c != null && z != null) {
                                    val dx = c.izquierda - z.izquierda
                                    val x = if (a.id in movilesActuales && kotlin.math.abs(dx) >= 2f) Math.round(z.x + dx) else null
                                    if (x != null || kotlin.math.abs(c.arriba - z.arriba) >= 1f) onMover(a.id, c.arriba, x)
                                }
                            },
                            onDragCancel = { arrastre = null; colocacion = null },
                        )
                    },
            ) {
                val punteado = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                val a = arrastre
                val c = colocacion
                fun marco(z: Renderizador.Zona, dx: Float = 0f, dy: Float = 0f): Pair<Offset, Size> {
                    val l = (z.izquierda - 3) * escala + dx
                    val t = z.arriba * escala + dy
                    val w = (z.derecha - z.izquierda + 6) * escala
                    val h = maxOf(z.abajo - z.arriba, 16f) * escala
                    return Offset(l.coerceAtLeast(1f), t) to Size(w.coerceAtMost(size.width - 2f), h)
                }
                zonas.forEach { z ->
                    val (o, tam) = marco(z)
                    if (z.id == seleccion && a == null) {
                        drawRoundRect(paleta.principal.copy(alpha = 0.08f), o, tam, CornerRadius(6.dp.toPx()))
                        drawRoundRect(paleta.principal, o, tam, CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
                    } else if (a == null) {
                        drawRoundRect(Color(0xFFCBD5E1), o + Offset(1f, 1f), Size(tam.width - 2f, tam.height - 2f), CornerRadius(5.dp.toPx()), style = punteado)
                    } else if (z.id != a.id) {
                        drawRoundRect(Color(0xFFE2E8F0), o, tam, CornerRadius(5.dp.toPx()), style = punteado)
                    }
                }
                if (a != null && c != null) {
                    val z = zonas.firstOrNull { it.id == a.id }
                    if (z != null) {
                        val hPuntos = maxOf(z.abajo - z.arriba, 16f)
                        val l = z.izquierda.toInt().coerceIn(0, imagen.width - 1)
                        val r = z.derecha.toInt().coerceIn(l + 1, imagen.width)
                        val t = z.arriba.toInt().coerceIn(0, imagen.height - 1)
                        val alto = hPuntos.toInt().coerceAtMost(imagen.height - t)
                        // El lugar de donde sale queda tenue.
                        drawRect(Color.White.copy(alpha = 0.75f), Offset(l * escala, t * escala), Size((r - l) * escala, alto * escala))
                        // El elemento siguiendo el dedo.
                        val dx = c.izquierda * escala
                        val dy = c.arriba * escala
                        drawImage(
                            imagen,
                            srcOffset = IntOffset(l, t),
                            srcSize = IntSize(r - l, alto),
                            dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                            dstSize = IntSize(((r - l) * escala).toInt(), (alto * escala).toInt()),
                            alpha = 0.9f,
                        )
                        drawRoundRect(
                            paleta.principal, Offset(dx - 3 * escala, dy), Size((r - l + 6) * escala, hPuntos * escala),
                            CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()),
                        )
                        // Guías de alineación.
                        c.guias.forEach { g ->
                            drawLine(colorGuia, Offset(g.x1 * escala, g.y1 * escala), Offset(g.x2 * escala, g.y2 * escala), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}
}

/** Una vibración muy corta, como la de las apps de etiquetas al alinear. */
private fun vibrarCorto(contexto: Context) {
    runCatching {
        val v = contexto.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoLargo(largo: Int, papelMm: Int, contenidoMm: Int, onCerrar: () -> Unit, onElegir: (Int) -> Unit) {
    var valor by remember { mutableStateOf(largo) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Tamaño de la impresión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ancho: $papelMm mm (lo da el papel, se cambia en Ajustes).", color = Color(0xFF6B7280))
                Text("El contenido mide unos $contenidoMm mm de largo.", color = Color(0xFF6B7280))
                Titulo("Largo del ticket")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 100, 150, 200, 250, 300).forEach { mm ->
                        FilterChip(
                            selected = valor == mm,
                            onClick = { valor = mm },
                            label = { Text(if (mm == 0) "Automático" else "$mm mm") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalPaleta.current.principal, selectedLabelColor = Color.White),
                        )
                    }
                }
                Deslizador("Personalizado", if (valor == 0) maxOf(contenidoMm, 50) else valor, 50..600, " mm") { valor = it }
                Text(
                    if (valor == 0) "Automático: el ticket mide lo que mida su contenido."
                    else "El ticket medirá $valor mm; si el contenido es más largo, se imprime completo igual.",
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onElegir(valor); onCerrar() }) { Text("Aplicar", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
