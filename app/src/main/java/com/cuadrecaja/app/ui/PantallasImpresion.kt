package com.cuadrecaja.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.EntradaHistorial
import com.cuadrecaja.app.Memoria
import com.cuadrecaja.app.Modo
import com.cuadrecaja.app.TipoImpresion
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
    recuerdos: Int = 0,
    onBorrarMemoria: () -> Unit = {},
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
        Tarjeta {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Titulo("Memoria de las notas de entrega")
                Text(
                    if (recuerdos == 0) "Todavía no hay nada guardado. Se guarda lo que escribe en cada nota que imprime."
                    else "$recuerdos textos guardados (nombres, establecimientos, productos...). Se sugieren al escribir.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (recuerdos > 0) {
                    var confirmar by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { confirmar = true }, shape = RoundedCornerShape(14.dp)) { Text("Borrar memoria") }
                    if (confirmar) {
                        AlertDialog(
                            onDismissRequest = { confirmar = false },
                            title = { Text("Borrar memoria") },
                            text = { Text("¿Olvidar todos los nombres y productos guardados?") },
                            confirmButton = { TextButton(onClick = { onBorrarMemoria(); confirmar = false }) { Text("Borrar") } },
                            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
                        )
                    }
                }
            }
        }
    }
}
