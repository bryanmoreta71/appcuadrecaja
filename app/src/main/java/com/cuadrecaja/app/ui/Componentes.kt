package com.cuadrecaja.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.Modo
import com.cuadrecaja.app.ui.theme.LocalPaleta

/** Tarjeta grande con degradado del color del modo. */
@Composable
fun Encabezado(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LocalPaleta.current.degradado)
            .padding(20.dp),
    ) { content() }
}

/** Selector "Cuadre | Impresión" que cambia toda la app. */
@Composable
fun SelectorModo(modo: Modo, onCambiar: (Modo) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(4.dp),
    ) {
        for ((m, etiqueta) in listOf(Modo.CUADRE to "💵  Cuadre", Modo.IMPRESION to "🖨️  Impresión")) {
            val activo = m == modo
            Text(
                etiqueta,
                color = if (activo) LocalPaleta.current.oscuro else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (activo) Color.White else Color.Transparent)
                    .clickable { onCambiar(m) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun Mosaico(icono: String, titulo: String, subtitulo: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(LocalPaleta.current.suave),
                contentAlignment = Alignment.Center,
            ) { Text(icono, fontSize = 26.sp) }
            Spacer(Modifier.height(4.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280))
        }
    }
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Box(Modifier.padding(padding)) { content() } }
}

@Composable
fun BotonPrincipal(texto: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(20.dp),
    ) { Text(texto, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Color(0xFF6B7280),
    )
}

/** Mensaje de estado de la impresión ("Imprimiendo…", "✓ Impreso", errores). */
@Composable
fun EstadoImpresion(texto: String) {
    if (texto.isEmpty()) return
    val ok = texto.startsWith("✓")
    Text(
        texto,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (ok) LocalPaleta.current.suave else Color(0xFFF3F4F6))
            .padding(12.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = if (ok) LocalPaleta.current.oscuro else Color(0xFF374151),
    )
}

/** Botones − n + para cantidades. */
@Composable
fun Contador(valor: Int, min: Int, max: Int, onCambio: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FilledTonalIconButton(onClick = { onCambio((valor - 1).coerceAtLeast(min)) }, modifier = Modifier.size(52.dp)) {
            Text("−", fontSize = 26.sp)
        }
        Text("$valor", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onCambio((valor + 1).coerceAtMost(max)) }, modifier = Modifier.size(52.dp)) {
            Text("+", fontSize = 26.sp)
        }
    }
}

/** Fila de opciones donde se elige una (fichas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Opciones(titulo: String, opciones: List<T>, elegida: T, etiqueta: (T) -> String, onElegir: (T) -> Unit) {
    Column {
        Titulo(titulo)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { o ->
                FilterChip(
                    selected = o == elegida,
                    onClick = { onElegir(o) },
                    label = { Text(etiqueta(o)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LocalPaleta.current.principal,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
    }
}

/** Botones segmentados (por ejemplo izquierda | centro | derecha). */
@Composable
fun <T> Segmentos(titulo: String, opciones: List<T>, elegida: T, etiqueta: (T) -> String, onElegir: (T) -> Unit) {
    Column {
        Titulo(titulo)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opciones.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = o == elegida,
                    onClick = { onElegir(o) },
                    shape = SegmentedButtonDefaults.itemShape(i, opciones.size),
                ) { Text(etiqueta(o), maxLines = 1) }
            }
        }
    }
}

@Composable
fun Deslizador(titulo: String, valor: Int, rango: IntRange, sufijo: String = "", onCambio: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo(titulo, Modifier.weight(1f))
            Text("$valor$sufijo", fontWeight = FontWeight.Bold, color = LocalPaleta.current.oscuro)
        }
        Slider(
            value = valor.toFloat(),
            onValueChange = { onCambio(it.toInt()) },
            valueRange = rango.first.toFloat()..rango.last.toFloat(),
        )
    }
}

@Composable
fun Interruptor(texto: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCambio(!valor) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = valor, onCheckedChange = onCambio)
    }
}
