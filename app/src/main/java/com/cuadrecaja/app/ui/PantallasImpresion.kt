package com.cuadrecaja.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.EntradaHistorial
import com.cuadrecaja.app.Modo
import com.cuadrecaja.app.TipoImpresion
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.TipoCampo
import com.cuadrecaja.app.plantillas.totalTabla
import com.cuadrecaja.app.ui.theme.LocalPaleta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Gris = Color(0xFF6B7280)

@Composable
fun InicioImpresion(
    entradas: List<EntradaHistorial>,
    onCambiarModo: (Modo) -> Unit,
    onNota: () -> Unit,
    onMensaje: () -> Unit,
    onPlantillas: () -> Unit,
    onHistorial: () -> Unit,
    onAbrirEntrada: (EntradaHistorial) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectorModo(Modo.IMPRESION, onCambiarModo)
                Spacer(Modifier.height(10.dp))
                Text("Centro de impresión", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Notas de entrega, mensajes y plantillas a su medida",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mosaico("📦", "Nota de entrega", "Llenar e imprimir", Modifier.weight(1f), onNota)
            Mosaico("✉️", "Mensaje", "Texto libre", Modifier.weight(1f), onMensaje)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mosaico("🎨", "Plantillas", "Diseñar la nota", Modifier.weight(1f), onPlantillas)
            Mosaico("🕘", "Historial", "Últimos ${com.cuadrecaja.app.Historial.MAXIMO}", Modifier.weight(1f), onHistorial)
        }
        if (entradas.isNotEmpty()) {
            Titulo("Impreso recientemente")
            entradas.take(3).forEach { FilaHistorial(it) { onAbrirEntrada(it) } }
        }
    }
}

// ---------- Nota de entrega ----------

@OptIn(ExperimentalLayoutApi::class)
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
) {
    var verPrevia by rememberSaveable { mutableStateOf(false) }
    var pedirCopias by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (plantillas.size > 1) {
            Titulo("Plantilla")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plantillas.forEach { p ->
                    FilterChip(
                        selected = p.id == plantilla.id,
                        onClick = { onElegirPlantilla(p.id) },
                        label = { Text(p.nombre) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LocalPaleta.current.principal,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        val campos = plantilla.campos
        if (campos.isNotEmpty()) {
            Tarjeta {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Titulo("Datos")
                    campos.forEach { c -> CampoParaLlenar(c, datos.campos[c.id].orEmpty()) { onCampo(c.id, it) } }
                }
            }
        }

        plantilla.tablas.forEach { t ->
            TablaParaLlenar(t, datos.filas[t.id].orEmpty()) { onFilas(t.id, it) }
        }

        if (campos.isEmpty() && plantilla.tablas.isEmpty()) {
            Tarjeta {
                Text(
                    "Esta plantilla no tiene campos para llenar. Puede agregarlos en Plantillas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Gris,
                )
            }
        }

        OutlinedButton(onClick = { verPrevia = !verPrevia }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (verPrevia) "Ocultar vista previa" else "👁  Ver vista previa")
        }
        if (verPrevia) {
            val resultado = remember(plantilla, datos) { renderizar(plantilla, datos) }
            VistaPrevia(resultado.imagen)
            Avisos(resultado.avisos)
        }

        BotonPrincipal(if (printing) "Imprimiendo…" else "🖨️  Imprimir nota de entrega", enabled = !printing) { pedirCopias = true }
        EstadoImpresion(status)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onLimpiar) { Text("Limpiar todo") }
            TextButton(onClick = onEditarPlantilla) { Text("Editar plantilla") }
        }
    }

    if (pedirCopias) {
        DialogoCopias(
            onCancelar = { pedirCopias = false },
            onImprimir = {
                pedirCopias = false
                onImprimir(it)
            },
        )
    }
}

@Composable
private fun CampoParaLlenar(c: Elemento.Campo, valor: String, onCambio: (String) -> Unit) {
    val automatico = c.tipo == TipoCampo.FECHA || c.tipo == TipoCampo.HORA
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(if (automatico) "${c.etiqueta} (automática si se deja vacía)" else c.etiqueta) },
        placeholder = {
            Text(
                when {
                    automatico -> "Automática: " + (if (c.tipo == TipoCampo.FECHA) Renderizador.fecha(Date()) else Renderizador.hora(Date()))
                    c.valorInicial.isNotBlank() -> c.valorInicial
                    else -> "En blanco = línea para llenar a mano"
                },
            )
        },
        singleLine = c.tipo != TipoCampo.PARRAFO,
        minLines = if (c.tipo == TipoCampo.PARRAFO) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (c.tipo == TipoCampo.NUMERO) KeyboardType.Number else KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TablaParaLlenar(t: Elemento.Tabla, filas: List<List<String>>, onFilas: (List<List<String>>) -> Unit) {
    val lista = filas.ifEmpty { listOf(t.columnas.map { "" }) }
    Tarjeta {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Titulo("Productos", Modifier.weight(1f))
                totalTabla(t, lista)?.let { Text("Total: $it", fontWeight = FontWeight.Bold, color = LocalPaleta.current.oscuro) }
            }
            lista.forEachIndexed { i, fila ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.columnas.forEachIndexed { c, col ->
                        OutlinedTextField(
                            value = fila.getOrElse(c) { "" },
                            onValueChange = { nuevo ->
                                val nuevaFila = t.columnas.indices.map { if (it == c) nuevo else fila.getOrElse(it) { "" } }
                                onFilas(lista.toMutableList().also { it[i] = nuevaFila })
                            },
                            label = { Text(col.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (col.numerica) KeyboardType.Number else KeyboardType.Text),
                            shape = RoundedCornerShape(12.dp),
                            // Las cantidades necesitan espacio para escribir aunque la columna impresa sea angosta.
                            modifier = Modifier.weight(if (col.numerica) 1.6f else col.ancho.coerceIn(2, 4).toFloat()),
                        )
                    }
                    IconButton(onClick = { onFilas(lista.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Quitar producto", tint = Gris)
                    }
                }
            }
            OutlinedButton(
                onClick = { onFilas(lista + listOf(t.columnas.map { "" })) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("+  Agregar producto") }
        }
    }
}

@Composable
fun VistaPrevia(imagen: Bitmap) {
    Tirilla {
        Image(
            bitmap = imagen.asImageBitmap(),
            contentDescription = "Vista previa del ticket",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
fun Avisos(avisos: List<String>) {
    avisos.forEach {
        Text(
            "⚠️  $it",
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF7ED)).padding(12.dp),
            color = Color(0xFF9A3412),
        )
    }
}

@Composable
fun DialogoCopias(onCancelar: () -> Unit, onImprimir: (Int) -> Unit) {
    var copias by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Cuántas copias?") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Contador(copias, 1, 10) { copias = it }
                Text(
                    if (copias == 1) "Se imprime una nota." else "Se imprime una, se corta, y así hasta $copias.",
                    color = Gris,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImprimir(copias) }) { Text("Imprimir $copias", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

// ---------- Historial ----------

private fun fechaCorta(ms: Long) = SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale.US).format(Date(ms))

private fun icono(t: TipoImpresion) = when (t) {
    TipoImpresion.CUADRE -> "💵"
    TipoImpresion.MENSAJE -> "✉️"
    TipoImpresion.NOTA -> "📦"
}

@Composable
fun FilaHistorial(e: EntradaHistorial, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(LocalPaleta.current.suave),
                contentAlignment = Alignment.Center,
            ) { Text(icono(e.tipo), fontSize = 22.sp) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(e.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    fechaCorta(e.fecha) + if (e.copias > 1) "  ·  ${e.copias} copias" else "",
                    color = Gris,
                    fontSize = 14.sp,
                )
            }
            Text("›", fontSize = 26.sp, color = Gris)
        }
    }
}

@Composable
fun HistorialScreen(entradas: List<EntradaHistorial>, onAbrir: (EntradaHistorial) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Se guardan los últimos ${com.cuadrecaja.app.Historial.MAXIMO} tickets impresos.",
            color = Gris,
        )
        if (entradas.isEmpty()) {
            Tarjeta { Text("Todavía no se ha impreso nada.", style = MaterialTheme.typography.bodyLarge, color = Gris) }
        }
        entradas.forEach { FilaHistorial(it) { onAbrir(it) } }
    }
}

@Composable
fun DetalleHistorialScreen(
    entrada: EntradaHistorial,
    imagen: Bitmap?,
    status: String,
    printing: Boolean,
    onReimprimir: () -> Unit,
    onEliminar: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "${entrada.tipo.etiqueta}  ·  ${fechaCorta(entrada.fecha)}" + if (entrada.copias > 1) "  ·  ${entrada.copias} copias" else "",
            color = Gris,
        )
        when {
            imagen != null -> VistaPrevia(imagen)
            entrada.textoVista != null -> Tirilla {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Text(entrada.textoVista, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        BotonPrincipal(if (printing) "Imprimiendo…" else "🖨️  Reimprimir", enabled = !printing, onClick = onReimprimir)
        EstadoImpresion(status)
        TextButton(onClick = onEliminar, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Eliminar del historial", color = Color(0xFFB91C1C))
        }
    }
}

// ---------- Ajustes ----------

@Composable
fun AjustesScreen(
    modo: Modo,
    papelMm: Int,
    impresora: String?,
    onModo: (Modo) -> Unit,
    onPapel: (Int) -> Unit,
    onOlvidarImpresora: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tarjeta {
            Segmentos("Modo de la app", Modo.entries, modo, { if (it == Modo.CUADRE) "Cuadre" else "Impresión" }, onModo)
        }
        Tarjeta {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmentos("Ancho del papel", listOf(80, 58), papelMm, { "$it mm" }, onPapel)
                Text(
                    "80 mm para impresoras de escritorio como la SAT; 58 mm para las portátiles. " +
                        "Se usa en las notas de entrega.",
                    color = Gris,
                    fontSize = 14.sp,
                )
            }
        }
        Tarjeta {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Titulo("Impresora")
                Text(impresora ?: "Ninguna guardada todavía. Se elige al imprimir por primera vez.", style = MaterialTheme.typography.bodyLarge)
                if (impresora != null) {
                    OutlinedButton(onClick = onOlvidarImpresora, shape = RoundedCornerShape(14.dp)) { Text("Olvidar impresora") }
                }
            }
        }
    }
}
