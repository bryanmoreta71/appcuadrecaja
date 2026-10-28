package com.cuadrecaja.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.impresion.CodigoBarras
import com.cuadrecaja.app.plantillas.TipoBarras
import com.cuadrecaja.app.stock.CodigoStock
import com.cuadrecaja.app.stock.ListaStock
import com.cuadrecaja.app.stock.tipoDe
import com.cuadrecaja.app.ui.theme.LocalPaleta
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val Gris = Color(0xFF6B7280)

// ---------- Listas ----------

@Composable
fun StockListasScreen(
    listas: List<ListaStock>,
    onAbrir: (ListaStock) -> Unit,
    onEditar: (ListaStock) -> Unit,
    onEliminar: (ListaStock) -> Unit,
    onNueva: () -> Unit,
) {
    var menu by remember { mutableStateOf<String?>(null) }
    var porEliminar by remember { mutableStateOf<ListaStock?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Listas de códigos de barras para pasar por el escáner, uno tras otro.", color = Gris)
        if (listas.isEmpty()) {
            Tarjeta {
                Text(
                    "Todavía no hay listas. Cree una y agregue los códigos escribiéndolos o escaneándolos con la cámara.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Gris,
                )
            }
        }
        listas.forEach { l ->
            Card(
                onClick = { onAbrir(l) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(LocalPaleta.current.suave),
                        contentAlignment = Alignment.Center,
                    ) { IconoLinea(Icono.BARRAS, LocalPaleta.current.oscuro, tamano = 26.dp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(l.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (l.codigos.size == 1) "1 código" else "${l.codigos.size} códigos",
                            color = Gris,
                            fontSize = 14.sp,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = l.id }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de ${l.nombre}", tint = Gris)
                        }
                        DropdownMenu(expanded = menu == l.id, onDismissRequest = { menu = null }) {
                            DropdownMenuItem(text = { Text("✏️   Editar", fontSize = 16.sp) }, onClick = { menu = null; onEditar(l) })
                            DropdownMenuItem(
                                text = { Text("🗑   Eliminar", fontSize = 16.sp, color = Color(0xFFB91C1C)) },
                                onClick = { menu = null; porEliminar = l },
                            )
                        }
                    }
                }
            }
        }
        BotonPrincipal("+  Nueva lista", onClick = onNueva)
    }
    porEliminar?.let { l ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Eliminar \"${l.nombre}\" con sus ${l.codigos.size} códigos?") },
            confirmButton = { TextButton(onClick = { onEliminar(l); porEliminar = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } },
        )
    }
}

// ---------- Editar una lista ----------

/** Abre la cámara para leer un código de barras. Devuelve (texto, formato) o nada si se cancela. */
@Composable
private fun rememberEscaner(onLeido: (String, String?) -> Unit) =
    rememberLauncherForActivityResult(ScanContract()) { r ->
        if (r.contents != null) onLeido(r.contents, r.formatName)
    }

private fun opcionesEscaner() = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
    .setPrompt("Apunte al código de barras")
    .setBeepEnabled(true)
    .setOrientationLocked(false)

@Composable
fun StockEditorScreen(lista: ListaStock, onCambio: (ListaStock) -> Unit, onEmpezar: () -> Unit) {
    var enfocar by remember { mutableStateOf<String?>(null) }
    // Qué código se está reemplazando al escanear (null = uno nuevo).
    var reemplazar by remember { mutableStateOf<String?>(null) }
    val escaner = rememberEscaner { texto, formato ->
        val tipo = CodigoBarras.tipoDeFormato(formato)
        val id = reemplazar
        if (id != null) {
            onCambio(lista.copy(codigos = lista.codigos.map { if (it.id == id) it.copy(codigo = texto, tipo = tipo) else it }))
            reemplazar = null
        } else {
            val nuevo = CodigoStock(codigo = texto, tipo = tipo)
            onCambio(lista.copy(codigos = lista.codigos + nuevo))
            enfocar = nuevo.id // para escribirle el nombre enseguida
        }
    }

    fun cambiar(c: CodigoStock) = onCambio(lista.copy(codigos = lista.codigos.map { if (it.id == c.id) c else it }))
    fun mover(i: Int, d: Int) {
        val j = i + d
        if (j !in lista.codigos.indices) return
        val l = lista.codigos.toMutableList()
        l[i] = l[j].also { l[j] = l[i] }
        onCambio(lista.copy(codigos = l))
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = lista.nombre,
                onValueChange = { onCambio(lista.copy(nombre = it)) },
                label = { Text("Nombre de la lista") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Titulo("Códigos en orden")
            lista.codigos.forEachIndexed { i, c ->
                FilaCodigo(
                    numero = i + 1,
                    c = c,
                    enfocar = enfocar == c.id,
                    puedeSubir = i > 0,
                    puedeBajar = i < lista.codigos.lastIndex,
                    onCambio = ::cambiar,
                    onSubir = { mover(i, -1) },
                    onBajar = { mover(i, 1) },
                    onEliminar = { onCambio(lista.copy(codigos = lista.codigos.filter { it.id != c.id })) },
                    onEscanear = { reemplazar = c.id; escaner.launch(opcionesEscaner()) },
                    onEnfocado = { enfocar = null },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { reemplazar = null; escaner.launch(opcionesEscaner()) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = CircleShape,
                ) { Text("📷  Escanear", fontSize = 16.sp) }
                OutlinedButton(
                    onClick = {
                        val nuevo = CodigoStock()
                        onCambio(lista.copy(codigos = lista.codigos + nuevo))
                        enfocar = nuevo.id
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = CircleShape,
                ) { Text("+  Escribir", fontSize = 16.sp) }
            }
            Text("Los cambios se guardan solos.", color = Gris, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            BotonPrincipal("▶  Empezar a sacar stock", enabled = lista.codigos.any { it.codigo.isNotBlank() }, onClick = onEmpezar)
        }
    }
}

@Composable
private fun FilaCodigo(
    numero: Int,
    c: CodigoStock,
    enfocar: Boolean,
    puedeSubir: Boolean,
    puedeBajar: Boolean,
    onCambio: (CodigoStock) -> Unit,
    onSubir: () -> Unit,
    onBajar: () -> Unit,
    onEliminar: () -> Unit,
    onEscanear: () -> Unit,
    onEnfocado: () -> Unit,
) {
    val paleta = LocalPaleta.current
    val foco = remember { FocusRequester() }
    LaunchedEffect(enfocar) {
        if (enfocar) {
            foco.requestFocus()
            onEnfocado()
        }
    }
    Tarjeta {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(paleta.suave), contentAlignment = Alignment.Center) {
                    Text("$numero", fontWeight = FontWeight.Bold, color = paleta.oscuro, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                AccionCodigo(Icono.SUBIR, "Subir", puedeSubir, onSubir)
                AccionCodigo(Icono.BAJAR, "Bajar", puedeBajar, onBajar)
                IconButton(onClick = onEscanear, modifier = Modifier.semantics { contentDescription = "Escanear de nuevo" }) {
                    Text("📷", fontSize = 18.sp)
                }
                AccionCodigo(Icono.BORRAR, "Quitar código", true, onEliminar, Color(0xFFDC2626))
            }
            OutlinedTextField(
                value = c.nombre,
                onValueChange = { onCambio(c.copy(nombre = it)) },
                label = { Text("Nombre") },
                placeholder = { Text("Ej.: Salentein Reserva Merlot 750 ml") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
            )
            OutlinedTextField(
                value = c.codigo,
                onValueChange = { onCambio(c.copy(codigo = it.trim())) },
                label = { Text("Código") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipTipo("Automático (${tipoDe(c.copy(tipo = null)).etiqueta})", c.tipo == null) { onCambio(c.copy(tipo = null)) }
                TipoBarras.entries.forEach { t -> ChipTipo(t.etiqueta, c.tipo == t) { onCambio(c.copy(tipo = t)) } }
            }
            if (c.codigo.isNotBlank()) {
                val bitmap = remember(c.codigo, tipoDe(c)) { CodigoBarras.dibujar(c.codigo, tipoDe(c), 600, 90) }
                if (bitmap == null) {
                    Text(
                        "⚠️  \"${c.codigo}\" no sirve como ${tipoDe(c).etiqueta} (${tipoDe(c).ayuda}). Elija otro tipo.",
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF7ED)).padding(10.dp),
                        color = Color(0xFF9A3412),
                        fontSize = 14.sp,
                    )
                } else {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "Vista del código",
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccionCodigo(icono: Icono, descripcion: String, activo: Boolean, onClick: () -> Unit, color: Color = Gris) {
    IconButton(onClick = onClick, enabled = activo, modifier = Modifier.semantics { contentDescription = descripcion }) {
        IconoLinea(icono, if (activo) color else Color(0xFFCBD5E1), tamano = 20.dp)
    }
}

@Composable
private fun ChipTipo(texto: String, elegido: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = elegido,
        onClick = onClick,
        label = { Text(texto, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalPaleta.current.principal, selectedLabelColor = Color.White),
    )
}

// ---------- Sacar stock: la secuencia ----------

/**
 * Muestra los códigos de la lista uno por uno, grandes, para pasarlos por el escáner.
 * Se avanza con los botones, deslizando o con los botones de volumen del celular.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockSecuenciaScreen(lista: ListaStock, indice: Int, onIndice: (Int) -> Unit, onTerminar: () -> Unit, onEditar: () -> Unit) {
    val paleta = LocalPaleta.current
    val codigos = lista.codigos.filter { it.codigo.isNotBlank() }

    // Brillo al máximo y pantalla siempre encendida, para que el escáner lea bien.
    val actividad = LocalContext.current as? Activity
    DisposableEffect(actividad) {
        val ventana = actividad?.window
        val antes = ventana?.attributes?.screenBrightness
        ventana?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ventana?.attributes = ventana?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            ventana?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ventana?.attributes = ventana?.attributes?.apply { screenBrightness = antes ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    if (codigos.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Esta lista no tiene códigos.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEditar, shape = CircleShape) { Text("Agregar códigos") }
        }
        return
    }

    val actual = indice.coerceIn(0, codigos.lastIndex)
    val pager = rememberPagerState(initialPage = actual) { codigos.size }
    // Botones / volumen -> página; deslizar -> índice.
    LaunchedEffect(actual) { if (pager.currentPage != actual) pager.animateScrollToPage(actual) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it != indice) onIndice(it) } }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lista.nombre, Modifier.weight(1f), color = Gris, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${actual + 1} de ${codigos.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = paleta.oscuro)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (actual + 1) / codigos.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
        }

        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { pagina ->
            val c = codigos[pagina]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    c.nombre.ifBlank { "Código ${pagina + 1}" },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(24.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val ancho = constraints.maxWidth
                    val alto = with(LocalDensity.current) { 170.dp.roundToPx() }
                    val bitmap = remember(c.codigo, tipoDe(c), ancho) { CodigoBarras.dibujar(c.codigo, tipoDe(c), ancho, alto) }
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = "Código de barras ${c.codigo}",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                        )
                    } else {
                        Text(
                            "⚠️  Este código no se puede mostrar como ${tipoDe(c).etiqueta}. Edítelo y elija otro tipo.",
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF7ED)).padding(14.dp),
                            color = Color(0xFF9A3412),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(c.codigo, fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 2.sp)
                Text(tipoDe(c).etiqueta, color = Gris, fontSize = 13.sp)
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onIndice(actual - 1) },
                    enabled = actual > 0,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = CircleShape,
                ) { Text("◀  Anterior", fontSize = 17.sp) }
                if (actual < codigos.lastIndex) {
                    Button(
                        onClick = { onIndice(actual + 1) },
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = CircleShape,
                    ) { Text("Siguiente  ▶", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                } else {
                    Button(onClick = onTerminar, modifier = Modifier.weight(1f).height(60.dp), shape = CircleShape) {
                        Text("✓  Terminar", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                "También puede deslizar o usar los botones de volumen",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Gris,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
