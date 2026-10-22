package com.cuadrecaja.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.cuadrecaja.app.formatMoney
import com.cuadrecaja.app.grandTotal
import com.cuadrecaja.app.sectionTotal

val Green = Color(0xFF0F7B4A)
val GreenDark = Color(0xFF0A5C37)

@Composable
fun HomeScreen(
    counts: Map<String, Int>,
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
        BigButton("💵", "Billetes", formatMoney(sectionTotal(Kind.BILLETES, counts))) { onOpen(Kind.BILLETES) }
        BigButton("🪙", "Monedas", formatMoney(sectionTotal(Kind.MONEDAS, counts))) { onOpen(Kind.MONEDAS) }
        TotalBox("Total en caja", grandTotal(counts))
        Button(
            onClick = onCuadrar,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Cuadrar caja", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Empezar de nuevo")
        }
    }
}

@Composable
private fun BigButton(icon: String, label: String, amount: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 44.sp)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(amount, fontSize = 18.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun TotalBox(label: String, cents: Long, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(GreenDark, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 18.sp)
        Text(formatMoney(cents), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDE3E0)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${if (kind == Kind.BILLETES) "Billete" else "Moneda"} ${d.label}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(formatMoney(qty * d.cents), fontSize = 20.sp, color = GreenDark, fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 28.sp)
    }
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
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(6.dp))
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(reportText, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Button(
            onClick = onPrint,
            enabled = !printing,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (printing) "Imprimiendo…" else "🖨️  Imprimir reporte", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (status.isNotEmpty()) {
            Text(status, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onChangePrinter, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cambiar impresora")
        }
    }
}
