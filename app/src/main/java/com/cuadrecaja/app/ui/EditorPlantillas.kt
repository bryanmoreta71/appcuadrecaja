package com.cuadrecaja.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.impresion.Imagenes
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Alineacion
import com.cuadrecaja.app.plantillas.Borde
import com.cuadrecaja.app.plantillas.Columna
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.DensidadQr
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Estilo
import com.cuadrecaja.app.plantillas.FormaCampo
import com.cuadrecaja.app.plantillas.FormaQr
import com.cuadrecaja.app.plantillas.Grosor
import com.cuadrecaja.app.plantillas.Letra
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.plantillas.TipoCampo
import com.cuadrecaja.app.ui.theme.LocalPaleta
import java.io.File

private val Gris = Color(0xFF6B7280)

// ---------- Lista de plantillas ----------

@Composable
fun PlantillasScreen(
    plantillas: List<Plantilla>,
    onEditar: (Plantilla) -> Unit,
    onNueva: () -> Unit,
    onDuplicar: (Plantilla) -> Unit,
    onEliminar: (Plantilla) -> Unit,
) {
    var porEliminar by remember { mutableStateOf<Plantilla?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Las plantillas son el diseño de la nota de entrega. Lo que agregue aquí aparece para llenar en Nota de entrega.", color = Gris)
        plantillas.forEach { p ->
            Card(
                onClick = { onEditar(p) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(LocalPaleta.current.suave),
                        contentAlignment = Alignment.Center,
                    ) { Text("🧾", fontSize = 24.sp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.nombre, style = MaterialTheme.typography.titleMedium)
                        Text("${p.elementos.size} elementos · ${p.campos.size} campos para llenar", color = Gris, fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDuplicar(p) }) { Text("Duplicar") }
                    if (plantillas.size > 1) TextButton(onClick = { porEliminar = p }) { Text("Eliminar", color = Color(0xFFB91C1C)) }
                    TextButton(onClick = { onEditar(p) }) { Text("Editar", fontWeight = FontWeight.Bold) }
                }
            }
        }
        BotonPrincipal("+  Nueva plantilla", onClick = onNueva)
    }
    porEliminar?.let { p ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("Eliminar plantilla") },
            text = { Text("¿Eliminar \"${p.nombre}\"?") },
            confirmButton = { TextButton(onClick = { onEliminar(p); porEliminar = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } },
        )
    }
}

// ---------- Editor ----------

private data class TipoNuevo(val icono: String, val nombre: String, val crear: () -> Elemento)

private val tiposNuevos = listOf(
    TipoNuevo("🔤", "Texto") { Elemento.Texto() },
    TipoNuevo("✍️", "Campo para llenar") { Elemento.Campo(etiqueta = "Nuevo campo") },
    TipoNuevo("📋", "Tabla de productos") { Elemento.Tabla() },
    TipoNuevo("➖", "Línea") { Elemento.Separador() },
    TipoNuevo("↕️", "Espacio") { Elemento.Espacio() },
    TipoNuevo("🖼️", "Imagen / logo") { Elemento.Imagen() },
    TipoNuevo("🔳", "Código QR") { Elemento.Qr() },
    TipoNuevo("▥", "Código de barras") { Elemento.Barras() },
    TipoNuevo("🖊️", "Firma") { Elemento.Firma() },
)

private fun nombreDe(e: Elemento) = when (e) {
    is Elemento.Texto -> "Texto"
    is Elemento.Campo -> "Campo para llenar"
    is Elemento.Tabla -> "Tabla de productos"
    is Elemento.Separador -> "Línea"
    is Elemento.Espacio -> "Espacio"
    is Elemento.Imagen -> "Imagen"
    is Elemento.Qr -> "Código QR"
    is Elemento.Barras -> "Código de barras"
    is Elemento.Firma -> "Firma"
}

private fun iconoDe(e: Elemento) = when (e) {
    is Elemento.Texto -> "🔤"
    is Elemento.Campo -> "✍️"
    is Elemento.Tabla -> "📋"
    is Elemento.Separador -> "➖"
    is Elemento.Espacio -> "↕️"
    is Elemento.Imagen -> "🖼️"
    is Elemento.Qr -> "🔳"
    is Elemento.Barras -> "▥"
    is Elemento.Firma -> "🖊️"
}

private fun resumen(e: Elemento) = when (e) {
    is Elemento.Texto -> e.texto.replace('\n', ' ')
    is Elemento.Campo -> "${e.etiqueta} · ${e.forma.etiqueta}"
    is Elemento.Tabla -> e.columnas.joinToString(" | ") { it.titulo } + " · ${e.borde.etiqueta}"
    is Elemento.Separador -> "${e.borde.etiqueta} · grosor ${e.grosor}"
    is Elemento.Espacio -> "${e.alto} puntos de alto"
    is Elemento.Imagen -> if (e.archivo.isBlank()) "Sin imagen elegida" else "${e.anchoPorcentaje}% del ancho"
    is Elemento.Qr -> "${e.densidad.etiqueta} · ${e.contenido}"
    is Elemento.Barras -> "${e.tipo.etiqueta} · ${e.contenido}"
    is Elemento.Firma -> e.etiqueta
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorPlantillaScreen(
    plantilla: Plantilla,
    onCambio: (Plantilla) -> Unit,
    renderizar: (Plantilla, Datos) -> Renderizador.Resultado,
    guardarImagen: (Uri) -> String?,
) {
    var pestana by rememberSaveable { mutableStateOf(0) }
    var editando by remember { mutableStateOf<String?>(null) }
    var agregando by remember { mutableStateOf(false) }

    fun reemplazar(e: Elemento) = onCambio(plantilla.copy(elementos = plantilla.elementos.map { if (it.id == e.id) e else it }))
    fun mover(i: Int, d: Int) {
        val j = i + d
        if (j !in plantilla.elementos.indices) return
        val l = plantilla.elementos.toMutableList()
        l[i] = l[j].also { l[j] = l[i] }
        onCambio(plantilla.copy(elementos = l))
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pestana, containerColor = Color.White) {
            Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("🎨  Diseño") })
            Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("👁  Vista previa") })
        }
        if (pestana == 1) {
            val resultado = remember(plantilla) { renderizar(plantilla, Datos()) }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Así sale impresa (con datos de ejemplo en las tablas).", color = Gris)
                VistaPrevia(resultado.imagen)
                Avisos(resultado.avisos)
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = plantilla.nombre,
                onValueChange = { onCambio(plantilla.copy(nombre = it)) },
                label = { Text("Nombre de la plantilla") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Titulo("Elementos (de arriba hacia abajo)")
            plantilla.elementos.forEachIndexed { i, e ->
                Card(
                    onClick = { editando = e.id },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(LocalPaleta.current.suave),
                            contentAlignment = Alignment.Center,
                        ) { Text(iconoDe(e), fontSize = 18.sp) }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(nombreDe(e), fontWeight = FontWeight.SemiBold)
                            Text(resumen(e), color = Gris, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { mover(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
                        IconButton(onClick = { mover(i, 1) }, enabled = i < plantilla.elementos.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Bajar")
                        }
                        IconButton(onClick = { onCambio(plantilla.copy(elementos = plantilla.elementos.filter { it.id != e.id })) }) {
                            Icon(Icons.Filled.Delete, "Quitar", tint = Color(0xFFB91C1C))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BotonPrincipal("+  Agregar elemento") { agregando = true }
            Text("Los cambios se guardan solos.", color = Gris, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (agregando) {
        ModalBottomSheet(onDismissRequest = { agregando = false }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text("Agregar elemento", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiposNuevos.forEach { t ->
                        Card(
                            onClick = {
                                val nuevo = t.crear()
                                onCambio(plantilla.copy(elementos = plantilla.elementos + nuevo))
                                agregando = false
                                editando = nuevo.id
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = LocalPaleta.current.suave),
                            modifier = Modifier.size(width = 104.dp, height = 96.dp),
                        ) {
                            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(t.icono, fontSize = 26.sp)
                                Text(
                                    t.nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                    color = LocalPaleta.current.oscuro, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val enEdicion = plantilla.elementos.firstOrNull { it.id == editando }
    if (enEdicion != null) {
        ModalBottomSheet(
            onDismissRequest = { editando = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(iconoDe(enEdicion) + "  " + nombreDe(enEdicion), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editando = null }) { Text("Listo", fontWeight = FontWeight.Bold) }
                }
                EditorElemento(enEdicion, plantilla, ::reemplazar, guardarImagen)
            }
        }
    }
}

@Composable
private fun EditorElemento(e: Elemento, plantilla: Plantilla, onCambio: (Elemento) -> Unit, guardarImagen: (Uri) -> String?) {
    when (e) {
        is Elemento.Texto -> {
            Texto("Texto", e.texto, multilinea = true) { onCambio(e.copy(texto = it)) }
            AyudaCampos(plantilla)
            EditorEstilo(e.estilo) { onCambio(e.copy(estilo = it)) }
        }
        is Elemento.Campo -> {
            Texto("Nombre del campo", e.etiqueta) { onCambio(e.copy(etiqueta = it)) }
            Opciones("Tipo de dato", TipoCampo.entries, e.tipo, { it.etiqueta }) { onCambio(e.copy(tipo = it)) }
            Opciones("Forma", FormaCampo.entries, e.forma, { it.etiqueta }) { onCambio(e.copy(forma = it)) }
            Opciones("Celda / línea", Borde.entries, e.borde, { it.etiqueta }) { onCambio(e.copy(borde = it)) }
            Texto("Valor por defecto (opcional)", e.valorInicial) { onCambio(e.copy(valorInicial = it)) }
            EditorEstilo(e.estilo, conAlineacion = e.forma != FormaCampo.EN_LINEA) { onCambio(e.copy(estilo = it)) }
        }
        is Elemento.Tabla -> EditorTabla(e, onCambio)
        is Elemento.Separador -> {
            Opciones("Estilo de línea", Borde.entries.filter { it != Borde.NINGUNO }, e.borde, { it.etiqueta }) { onCambio(e.copy(borde = it)) }
            Deslizador("Grosor", e.grosor, 1..8) { onCambio(e.copy(grosor = it)) }
        }
        is Elemento.Espacio -> Deslizador("Alto", e.alto, 4..160, " pt") { onCambio(e.copy(alto = it)) }
        is Elemento.Imagen -> EditorImagen(e, onCambio, guardarImagen)
        is Elemento.Qr -> {
            Texto("Contenido del QR (texto, enlace, número...)", e.contenido) { onCambio(e.copy(contenido = it)) }
            AyudaCampos(plantilla)
            Opciones("Densidad", DensidadQr.entries, e.densidad, { it.etiqueta }) { onCambio(e.copy(densidad = it)) }
            Text(e.densidad.descripcion, color = Gris, fontSize = 13.sp)
            Opciones("Forma de los cuadritos", FormaQr.entries, e.forma, { it.etiqueta }) { onCambio(e.copy(forma = it)) }
            Deslizador("Tamaño", e.tamanoPorcentaje, 15..100, "%") { onCambio(e.copy(tamanoPorcentaje = it)) }
            Segmentos("Posición", Alineacion.entries, e.alineacion, { it.etiqueta }) { onCambio(e.copy(alineacion = it)) }
            Interruptor("Mostrar el texto debajo", e.mostrarTexto) { onCambio(e.copy(mostrarTexto = it)) }
        }
        is Elemento.Barras -> {
            Texto("Contenido del código", e.contenido) { onCambio(e.copy(contenido = it)) }
            AyudaCampos(plantilla)
            Opciones("Tipo de código de barras", TipoBarras.entries, e.tipo, { it.etiqueta }) { onCambio(e.copy(tipo = it)) }
            Text("${e.tipo.etiqueta}: ${e.tipo.ayuda}", color = Gris, fontSize = 13.sp)
            Opciones("Ancho de las barras", Grosor.entries, e.grosor, { it.etiqueta }) { onCambio(e.copy(grosor = it)) }
            Deslizador("Alto", e.alto, 30..200, " pt") { onCambio(e.copy(alto = it)) }
            Segmentos("Posición", Alineacion.entries, e.alineacion, { it.etiqueta }) { onCambio(e.copy(alineacion = it)) }
            Interruptor("Mostrar los números debajo", e.mostrarTexto) { onCambio(e.copy(mostrarTexto = it)) }
        }
        is Elemento.Firma -> {
            Texto("Texto debajo de la línea", e.etiqueta) { onCambio(e.copy(etiqueta = it)) }
            Opciones("Línea", Borde.entries.filter { it != Borde.NINGUNO }, e.borde, { it.etiqueta }) { onCambio(e.copy(borde = it)) }
        }
    }
}

@Composable
private fun Texto(titulo: String, valor: String, multilinea: Boolean = false, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(titulo) },
        singleLine = !multilinea,
        minLines = if (multilinea) 2 else 1,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AyudaCampos(plantilla: Plantilla) {
    val nombres = (plantilla.campos.map { "{${it.etiqueta}}" } + listOf("{fecha}", "{hora}")).distinctBy { it.lowercase() }
    Text("Puede poner lo que se llene en un campo escribiendo: " + nombres.joinToString("  "), color = Gris, fontSize = 13.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorEstilo(estilo: Estilo, conAlineacion: Boolean = true, onCambio: (Estilo) -> Unit) {
    Opciones("Tipo de letra", Letra.entries, estilo.letra, { it.etiqueta }) { onCambio(estilo.copy(letra = it)) }
    Deslizador("Tamaño de letra", estilo.tamano, 14..72) { onCambio(estilo.copy(tamano = it)) }
    Column {
        Titulo("Estilo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ficha("Negrita", estilo.negrita) { onCambio(estilo.copy(negrita = it)) }
            Ficha("Cursiva", estilo.cursiva) { onCambio(estilo.copy(cursiva = it)) }
            Ficha("Subrayado", estilo.subrayado) { onCambio(estilo.copy(subrayado = it)) }
            Ficha("Fondo negro", estilo.invertido) { onCambio(estilo.copy(invertido = it)) }
        }
    }
    if (conAlineacion) {
        Segmentos("Alineación", Alineacion.entries, estilo.alineacion, { it.etiqueta }) { onCambio(estilo.copy(alineacion = it)) }
    }
}

@Composable
private fun Ficha(texto: String, activa: Boolean, onCambio: (Boolean) -> Unit) {
    FilterChip(
        selected = activa,
        onClick = { onCambio(!activa) },
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalPaleta.current.principal, selectedLabelColor = Color.White),
    )
}

@Composable
private fun EditorTabla(t: Elemento.Tabla, onCambio: (Elemento) -> Unit) {
    Titulo("Columnas")
    t.columnas.forEachIndexed { i, col ->
        fun cambiar(c: Columna) = onCambio(t.copy(columnas = t.columnas.mapIndexed { j, x -> if (j == i) c else x }))
        Tarjeta(Modifier.background(Color.White)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = col.titulo,
                        onValueChange = { cambiar(col.copy(titulo = it)) },
                        label = { Text("Título de la columna ${i + 1}") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    if (t.columnas.size > 1) {
                        IconButton(onClick = { onCambio(t.copy(columnas = t.columnas.filterIndexed { j, _ -> j != i })) }) {
                            Icon(Icons.Filled.Delete, "Quitar columna", tint = Color(0xFFB91C1C))
                        }
                    }
                }
                Deslizador("Ancho", col.ancho, 1..6) { cambiar(col.copy(ancho = it)) }
                Segmentos("Alineación", Alineacion.entries, col.alineacion, { it.etiqueta }) { cambiar(col.copy(alineacion = it)) }
                Interruptor("Es una cantidad (se suma al total)", col.numerica) { cambiar(col.copy(numerica = it)) }
            }
        }
    }
    if (t.columnas.size < 5) {
        OutlinedButton(
            onClick = { onCambio(t.copy(columnas = t.columnas + Columna("Columna ${t.columnas.size + 1}", 2))) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("+  Agregar columna") }
    }
    Opciones("Tipo de celdas", Borde.entries, t.borde, { it.etiqueta }) { onCambio(t.copy(borde = it)) }
    Interruptor("Encabezado con fondo negro", t.encabezadoInvertido) { onCambio(t.copy(encabezadoInvertido = it)) }
    Interruptor("Fila de total", t.mostrarTotal) { onCambio(t.copy(mostrarTotal = it)) }
    Deslizador("Filas vacías extra (para llenar a mano)", t.filasVacias, 0..10) { onCambio(t.copy(filasVacias = it)) }
    EditorEstilo(t.estilo, conAlineacion = false) { onCambio(t.copy(estilo = it)) }
}

@Composable
private fun EditorImagen(e: Elemento.Imagen, onCambio: (Elemento) -> Unit, guardarImagen: (Uri) -> String?) {
    val context = LocalContext.current
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // La imagen anterior no se borra: una copia de la plantilla puede estar usándola.
        if (uri != null) guardarImagen(uri)?.let { nuevo -> onCambio(e.copy(archivo = nuevo)) }
    }
    val bitmap = remember(e.archivo) {
        File(Imagenes.carpeta(context), e.archivo).takeIf { e.archivo.isNotBlank() && it.exists() }
            ?.let { BitmapFactory.decodeFile(it.path) }
    }
    if (bitmap != null) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF3F4F6)).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap.asImageBitmap(), contentDescription = "Imagen elegida", modifier = Modifier.heightIn(max = 160.dp))
        }
    }
    BotonPrincipal(if (bitmap == null) "🖼️  Elegir imagen" else "Cambiar imagen") {
        elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    Text("Se imprime en blanco y negro. Los logos con fondo blanco quedan mejor.", color = Gris, fontSize = 13.sp)
    Deslizador("Ancho", e.anchoPorcentaje, 10..100, "%") { onCambio(e.copy(anchoPorcentaje = it)) }
    Segmentos("Posición", Alineacion.entries, e.alineacion, { it.etiqueta }) { onCambio(e.copy(alineacion = it)) }
}
