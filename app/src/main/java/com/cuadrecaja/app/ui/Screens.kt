package com.cuadrecaja.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadrecaja.app.Denomination
import com.cuadrecaja.app.Denominations
import com.cuadrecaja.app.Kind
import com.cuadrecaja.app.Modo
import com.cuadrecaja.app.formatMoney
import com.cuadrecaja.app.grandTotal
import com.cuadrecaja.app.sectionTotal
import com.cuadrecaja.app.ui.theme.LocalPaleta

@Composable
fun HomeScreen(
    counts: Map<String, Int>,
    onCambiarModo: (Modo) -> Unit,
    onOpen: (Kind) -> Unit,
    onCuadrar: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectorModo(Modo.CUADRE, onCambiarModo)
                Spacer(Modifier.height(10.dp))
                Text("Total en caja", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                Text(formatMoney(grandTotal(counts)), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                val piezas = counts.values.sum()
                Text(
                    if (piezas == 0) "Toque Billetes o Monedas para empezar a contar" else "$piezas billetes y monedas contados",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mosaico("💵", "Billetes", formatMoney(sectionTotal(Kind.BILLETES, counts)), Modifier.weight(1f)) { onOpen(Kind.BILLETES) }
            Mosaico("🪙", "Monedas", formatMoney(sectionTotal(Kind.MONEDAS, counts)), Modifier.weight(1f)) { onOpen(Kind.MONEDAS) }
        }
        Spacer(Modifier.height(4.dp))
        BotonPrincipal("Cuadrar caja", onClick = onCuadrar)
        TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Empezar de nuevo")
        }
    }
}

/** Barra inferior con el total, del color del modo. */
@Composable
fun TotalBox(label: String, cents: Long, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalPaleta.current.degradado)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp)
        Text(formatMoney(cents), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CountScreen(kind: Kind, counts: Map<String, Int>, onChange: (Denomination, Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Denominations.of(kind), key = { it.id }) { d ->
                DenominationRow(kind, d, counts[d.id] ?: 0) { onChange(d, it) }
            }
        }
        TotalBox(
            if (kind == Kind.BILLETES) "Total billetes" else "Total monedas",
            sectionTotal(kind, counts),
            Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DenominationRow(kind: Kind, d: Denomination, qty: Int, onChange: (Int) -> Unit) {
    val paleta = LocalPaleta.current
    Tarjeta(padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(paleta.suave)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(d.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = paleta.oscuro)
                }
                Text(
                    if (kind == Kind.BILLETES) "  billete" else "  moneda",
                    color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f),
                )
                Text(formatMoney(qty * d.cents), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("−") { onChange(qty - 1) }
                OutlinedTextField(
                    value = if (qty == 0) "" else qty.toString(),
                    onValueChange = { text ->
                        val digits = text.filter(Char::isDigit).take(6)
                        onChange(digits.toIntOrNull() ?: 0)
                    },
                    placeholder = { Text("0", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE5E7EB)),
                    textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                StepButton("+") { onChange(qty + 1) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 28.sp)
    }
}

/** Vista del ticket como una tirilla de papel. */
@Composable
fun Tirilla(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(6.dp))
            .background(Color.White, RoundedCornerShape(6.dp))
            .padding(14.dp),
        contentAlignment = Alignment.TopCenter,
    ) { content() }
}

@Composable
fun ReportScreen(reportText: String, status: String, printing: Boolean, onPrint: () -> Unit, onChangePrinter: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tirilla {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(reportText, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        BotonPrincipal(if (printing) "Imprimiendo…" else "🖨️  Imprimir reporte", enabled = !printing, onClick = onPrint)
        EstadoImpresion(status)
        TextButton(onClick = onChangePrinter, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cambiar impresora")
        }
    }
}
