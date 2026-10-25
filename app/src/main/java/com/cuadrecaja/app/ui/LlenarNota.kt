package com.cuadrecaja.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.Memoria
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoCampo
import com.cuadrecaja.app.plantillas.totalTabla
import com.cuadrecaja.app.ui.theme.LocalPaleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.roundToInt

private val Fondo = Color(0xFFECEDF2)
private val Gris = Color(0xFF6B7280)

/**
 * Llenar la nota de entrega sobre el mismo ticket: se ve como sale impreso, se toca un campo
 * y ahí mismo se abre para escribir. Mientras se escribe aparecen solo las sugerencias que
 * coinciden con lo escrito.
 */
@Composable
fun NotaEntregaScreen(
    plantillas: List<Plantilla>,
    plantilla: Plantilla,
    datos: Datos,
    onElegirPlantilla: (String) -> Unit,
    onCampo: (String, String) -> Unit,
    onFilas: (String, List<List<String>>) -> Unit,
    renderizar: (Plantilla, Datos) -> Renderizador.Resultado,
    status: String,
    printing: Boolean,
    onImprimir: (Int) -> Unit,
    onLimpiar: () -> Unit,
    onEditarPlantilla: () -> Unit,
    sugerencias: (clave: String, escrito: String) -> List<String> = { _, _ -> emptyList() },
    onOlvidar: (clave: String, texto: String) -> Unit = { _, _ -> },
) {
    val paleta = LocalPaleta.current
    var activo by remember(plantilla.id) { mutableStateOf<String?>(null) }
    var pedirCopias by remember { mutableStateOf(false) }
    var confirmarLimpiar by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // El ticket se vuelve a dibujar con lo escrito (un poquito después, para no trabar el teclado).
    var resultado by remember { mutableStateOf<Renderizador.Resultado?>(null) }
    LaunchedEffect(plantilla, datos) {
        if (resultado != null) delay(120)
        resultado = withContext(Dispatchers.Default) { renderizar(plantilla, datos) }
    }

    val llenables = plantilla.elementos.filter { it is Elemento.Campo || it is Elemento.Tabla }.map { it.id }
    fun siguiente(id: String): String? = llenables.getOrNull(llenables.indexOf(id) + 1)
    fun cerrar() {
        activo = null
        focus.clearFocus()
    }

    Column(Modifier.fillMaxSize().background(Fondo)) {
        if (plantillas.size > 1) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                plantillas.forEach { p ->
                    FilterChip(
                        selected = p.id == plantilla.id,
                        onClick = { onElegirPlantilla(p.id) },
                        label = { Text(p.nombre) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = paleta.principal, selectedLabelColor = Color.White),
                    )
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val r = resultado
            if (r != null) {
                Hoja(
                    resultado = r,
                    plantilla = plantilla,
                    datos = datos,
                    activo = activo,
                    llenables = llenables,
                    onTocar = { id -> if (id == null) cerrar() else activo = id },
                    onCampo = onCampo,
                    onFilas = onFilas,
                    onSiguiente = { id -> siguiente(id)?.let { activo = it } ?: cerrar() },
                    onCerrar = ::cerrar,
                    sugerencias = sugerencias,
                    onOlvidar = onOlvidar,
                )
                r.avisos.forEach {
                    Spacer(Modifier.height(10.dp))
                    Text("⚠️  $it", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF7ED)).padding(10.dp), color = Color(0xFF9A3412), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (llenables.isEmpty()) "Esta plantilla no tiene campos para llenar. Agréguelos en Editar plantilla."
                else "Toque un campo del ticket para llenarlo",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Gris,
                fontSize = 14.sp,
            )
            // Espacio para poder subir el último campo por encima del teclado.
            Spacer(Modifier.height(if (activo != null) 260.dp else 8.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color.White, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EstadoImpresion(status)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonRedondo(Icono.LIMPIAR, "Limpiar nota") { confirmarLimpiar = true }
                BotonRedondo(Icono.LAPIZ, "Editar plantilla", onClick = onEditarPlantilla)
                Button(
                    onClick = { cerrar(); pedirCopias = true },
                    enabled = !printing,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = CircleShape,
                ) {
                    IconoLinea(Icono.IMPRESORA, Color.White, tamano = 22.dp)
                    Text(if (printing) "  Imprimiendo…" else "  Imprimir", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }

    if (pedirCopias) {
        DialogoCopias(onCancelar = { pedirCopias = false }, onImprimir = { pedirCopias = false; onImprimir(it) })
    }
    if (confirmarLimpiar) {
        AlertDialog(
            onDismissRequest = { confirmarLimpiar = false },
            title = { Text("Limpiar nota") },
            text = { Text("¿Borrar todo lo escrito en esta nota?") },
            confirmButton = { TextButton(onClick = { cerrar(); onLimpiar(); confirmarLimpiar = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarLimpiar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BotonRedondo(icono: Icono, descripcion: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(54.dp).semantics { contentDescription = descripcion },
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { IconoLinea(icono, Gris, tamano = 22.dp) }
}

@Composable
private fun Hoja(
    resultado: Renderizador.Resultado,
    plantilla: Plantilla,
    datos: Datos,
    activo: String?,
    llenables: List<String>,
    onTocar: (String?) -> Unit,
    onCampo: (String, String) -> Unit,
    onFilas: (String, List<List<String>>) -> Unit,
    onSiguiente: (String) -> Unit,
    onCerrar: () -> Unit,
    sugerencias: (String, String) -> List<String>,
    onOlvidar: (String, String) -> Unit,
) {
    val paleta = LocalPaleta.current
    val imagen = remember(resultado) { resultado.imagen.asImageBitmap() }
    val zonas by rememberUpdatedState(resultado.zonas)
    var escala by remember { mutableFloatStateOf(1f) }

    Box(
        Modifier
            .fillMaxWidth()
            // Por encima de lo que viene después, para que el cuadro de escribir y las sugerencias no queden tapados.
            .zIndex(1f)
            .shadow(10.dp, RoundedCornerShape(10.dp), clip = false)
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            escala = constraints.maxWidth.toFloat() / imagen.width
            val alto = with(LocalDensity.current) { (imagen.height * escala).toDp() }
            Box(Modifier.fillMaxWidth().height(alto)) {
                Image(imagen, "Nota de entrega", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
                Canvas(
                    Modifier
                        .matchParentSize()
                        .testTag("ticket")
                        .pointerInput(escala) {
                            detectTapGestures { pos ->
                                val y = pos.y / escala
                                val z = zonas.firstOrNull { y >= it.arriba - 4 && y <= maxOf(it.abajo, it.arriba + 16f) + 4 && it.id in llenables }
                                onTocar(z?.id)
                            }
                        },
                ) {
                    // Los campos para llenar se marcan suave, para saber qué se puede tocar.
                    resultado.zonas.filter { it.id in llenables && it.id != activo }.forEach { z ->
                        val top = z.arriba * escala - 2f
                        val h = (z.abajo - z.arriba) * escala + 4f
                        drawRoundRect(paleta.principal.copy(alpha = 0.06f), Offset(0f, top), Size(size.width, h), CornerRadius(6.dp.toPx()))
                        drawRoundRect(
                            paleta.principal.copy(alpha = 0.45f), Offset(0f, top), Size(size.width, h), CornerRadius(6.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                }

                val zona = resultado.zonas.firstOrNull { it.id == activo }
                val elemento = plantilla.elementos.firstOrNull { it.id == activo }
                if (zona != null && elemento != null) {
                    Box(Modifier.fillMaxWidth().offset { IntOffset(0, (zona.arriba * escala).roundToInt() - 8) }) {
                        when (elemento) {
                            is Elemento.Campo -> CampoEnTicket(
                                elemento, datos.campos[elemento.id].orEmpty(),
                                onCambio = { onCampo(elemento.id, it) },
                                onSiguiente = { onSiguiente(elemento.id) },
                                onCerrar = onCerrar,
                                sugerencias = sugerencias,
                                onOlvidar = onOlvidar,
                                ultimo = llenables.lastOrNull() == elemento.id,
                            )
                            is Elemento.Tabla -> TablaEnTicket(
                                elemento, datos.filas[elemento.id].orEmpty(),
                                onFilas = { onFilas(elemento.id, it) },
                                onListo = { onSiguiente(elemento.id) },
                                sugerencias = sugerencias,
                                onOlvidar = onOlvidar,
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

/**
 * Texto del campo con su cursor. Cuando el texto cambia desde afuera (al elegir una
 * sugerencia), el cursor se pone al final para seguir escribiendo.
 */
@Composable
private fun textoConCursor(valor: String): MutableState<TextFieldValue> {
    val estado = remember { mutableStateOf(TextFieldValue(valor, TextRange(valor.length))) }
    if (estado.value.text != valor) estado.value = TextFieldValue(valor, TextRange(valor.length))
    return estado
}

@Composable
private fun colores(principal: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = principal,
)

@Composable
private fun CampoEnTicket(
    c: Elemento.Campo,
    valor: String,
    onCambio: (String) -> Unit,
    onSiguiente: () -> Unit,
    onCerrar: () -> Unit,
    sugerencias: (String, String) -> List<String>,
    onOlvidar: (String, String) -> Unit,
    ultimo: Boolean,
) {
    val paleta = LocalPaleta.current
    val automatico = c.tipo == TipoCampo.FECHA || c.tipo == TipoCampo.HORA
    val clave = Memoria.claveCampo(c.etiqueta)
    val foco = remember { FocusRequester() }
    var texto by textoConCursor(valor)
    LaunchedEffect(c.id) { foco.requestFocus() }

    // Cuadro compacto encima del campo del ticket: el nombre chiquito arriba y lo que se escribe abajo.
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(10.dp), clip = false)
                .background(Color.White, RoundedCornerShape(10.dp))
                .border(2.dp, paleta.principal, RoundedCornerShape(10.dp))
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.etiqueta, fontSize = 11.sp, color = paleta.principal, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Box {
                    if (texto.text.isEmpty()) {
                        Text(
                            when {
                                automatico -> "Automática: " + (if (c.tipo == TipoCampo.FECHA) Renderizador.fecha(Date()) else Renderizador.hora(Date()))
                                c.valorInicial.isNotBlank() -> c.valorInicial
                                else -> "Escriba aquí (vacío = línea para llenar a mano)"
                            },
                            fontSize = 16.sp,
                            color = Color(0xFF9CA3AF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = texto,
                        onValueChange = {
                            texto = it
                            if (it.text != valor) onCambio(it.text)
                        },
                        textStyle = TextStyle(fontSize = 17.sp, color = Color(0xFF111827)),
                        cursorBrush = SolidColor(paleta.principal),
                        singleLine = c.tipo != TipoCampo.PARRAFO,
                        minLines = if (c.tipo == TipoCampo.PARRAFO) 3 else 1,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (c.tipo == TipoCampo.NUMERO) KeyboardType.Number else KeyboardType.Text,
                            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { onSiguiente() }, onDone = { onCerrar() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(foco).semantics { contentDescription = c.etiqueta },
                    )
                }
            }
            IconButton(onClick = onCerrar, modifier = Modifier.size(40.dp).semantics { contentDescription = "Listo" }) {
                Text("✓", fontSize = 20.sp, color = paleta.principal, fontWeight = FontWeight.Bold)
            }
        }
        // Solo al escribir, y solo lo que coincide con lo escrito.
        if (!automatico && c.tipo != TipoCampo.NUMERO && texto.text.isNotBlank()) {
            Box(Modifier.shadow(10.dp, RoundedCornerShape(12.dp), clip = false).background(Color.White, RoundedCornerShape(12.dp))) {
                Sugerencias(sugerencias(clave, texto.text), texto.text, onElegir = onCambio, onOlvidar = { onOlvidar(clave, it) })
            }
        }
    }
}

@Composable
private fun TablaEnTicket(
    t: Elemento.Tabla,
    filas: List<List<String>>,
    onFilas: (List<List<String>>) -> Unit,
    onListo: () -> Unit,
    sugerencias: (String, String) -> List<String>,
    onOlvidar: (String, String) -> Unit,
) {
    val paleta = LocalPaleta.current
    val lista = filas.ifEmpty { listOf(t.columnas.map { "" }) }
    var foco by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val primerCampo = remember { FocusRequester() }
    // Al abrir la tabla o agregar un producto, el cursor va a la fila nueva.
    LaunchedEffect(lista.size) { primerCampo.requestFocus() }

    fun cambiarCelda(i: Int, c: Int, nuevo: String) {
        val fila = lista[i]
        onFilas(lista.toMutableList().also { l -> l[i] = t.columnas.indices.map { if (it == c) nuevo else fila.getOrElse(it) { "" } } })
    }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(14.dp), clip = false)
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Productos", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            totalTabla(t, lista)?.let { Text("Total: $it", fontWeight = FontWeight.Bold, color = paleta.oscuro) }
        }
        lista.forEachIndexed { i, fila ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.columnas.forEachIndexed { c, col ->
                    val valor = fila.getOrElse(c) { "" }
                    var texto by textoConCursor(valor)
                    OutlinedTextField(
                        value = texto,
                        onValueChange = {
                            texto = it
                            if (it.text != valor) cambiarCelda(i, c, it.text)
                        },
                        label = { Text(col.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (col.numerica) KeyboardType.Number else KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = colores(paleta.principal),
                        modifier = Modifier
                            .weight(if (col.numerica) 1.5f else col.ancho.coerceIn(2, 4).toFloat())
                            .then(if (i == lista.lastIndex && c == 0) Modifier.focusRequester(primerCampo) else Modifier)
                            .onFocusChanged { e -> if (e.isFocused) foco = i to c else if (foco == i to c) foco = null },
                    )
                }
                IconButton(onClick = { onFilas(lista.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.size(36.dp)) {
                    IconoLinea(Icono.CERRAR, Gris, tamano = 18.dp)
                }
            }
            val f = foco
            if (f != null && f.first == i && t.columnas.getOrNull(f.second)?.numerica == false) {
                val escrito = fila.getOrElse(f.second) { "" }
                if (escrito.isNotBlank()) {
                    val clave = Memoria.claveColumna(t.columnas[f.second].titulo)
                    Sugerencias(sugerencias(clave, escrito), escrito, onElegir = { cambiarCelda(i, f.second, it) }, onOlvidar = { onOlvidar(clave, it) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onFilas(lista + listOf(t.columnas.map { "" })) },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) { Text("+ Producto") }
            Button(onClick = onListo, modifier = Modifier.weight(1f), shape = CircleShape) { Text("Listo") }
        }
    }
}

/** Sugerencias debajo del campo: solo las que coinciden con lo escrito, con esa parte en negrita. */
@Composable
private fun Sugerencias(lista: List<String>, escrito: String, onElegir: (String) -> Unit, onOlvidar: (String) -> Unit) {
    if (lista.isEmpty()) return
    val paleta = LocalPaleta.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).background(paleta.suave.copy(alpha = 0.6f))) {
        lista.forEach { texto ->
            Row(
                Modifier.fillMaxWidth().clickable { onElegir(texto) }.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoLinea(Icono.HORA, Gris, tamano = 16.dp)
                Text(
                    resaltar(texto, escrito, paleta.oscuro),
                    Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 11.dp),
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onOlvidar(texto) }, modifier = Modifier.size(40.dp).semantics { contentDescription = "Olvidar $texto" }) {
                    IconoLinea(Icono.CERRAR, Color(0xFF9CA3AF), tamano = 14.dp)
                }
            }
        }
    }
}

/** Pone en negrita la parte que coincide con lo escrito (sin importar mayúsculas ni tildes). */
private fun resaltar(texto: String, escrito: String, color: Color): androidx.compose.ui.text.AnnotatedString {
    val n = Memoria.normalizar(texto)
    val b = Memoria.normalizar(escrito)
    // normalizar puede juntar espacios; si cambia el largo, se muestra sin resaltar.
    val i = if (b.isEmpty() || n.length != texto.length) -1 else n.indexOf(b)
    return androidx.compose.ui.text.buildAnnotatedString {
        if (i < 0) {
            append(texto)
            return@buildAnnotatedString
        }
        append(texto.substring(0, i))
        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = color))
        append(texto.substring(i, i + b.length))
        pop()
        append(texto.substring(i + b.length))
    }
}
