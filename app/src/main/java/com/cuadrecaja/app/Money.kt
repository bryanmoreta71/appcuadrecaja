package com.cuadrecaja.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Todo el dinero se maneja en centavos (Long) para evitar errores de redondeo. */
enum class Kind(val title: String) { BILLETES("Billetes"), MONEDAS("Monedas") }

data class Denomination(val id: String, val cents: Long, val label: String, val kind: Kind)

object Denominations {
    val billetes = listOf(
        Denomination("b100", 10000, "$100", Kind.BILLETES),
        Denomination("b50", 5000, "$50", Kind.BILLETES),
        Denomination("b20", 2000, "$20", Kind.BILLETES),
        Denomination("b10", 1000, "$10", Kind.BILLETES),
        Denomination("b5", 500, "$5", Kind.BILLETES),
        Denomination("b2", 200, "$2", Kind.BILLETES),
        Denomination("b1", 100, "$1", Kind.BILLETES),
    )

    val monedas = listOf(
        Denomination("m100", 100, "$1.00", Kind.MONEDAS),
        Denomination("m50", 50, "50¢", Kind.MONEDAS),
        Denomination("m25", 25, "25¢", Kind.MONEDAS),
        Denomination("m10", 10, "10¢", Kind.MONEDAS),
        Denomination("m5", 5, "5¢", Kind.MONEDAS),
        Denomination("m1", 1, "1¢", Kind.MONEDAS),
    )

    fun of(kind: Kind) = if (kind == Kind.BILLETES) billetes else monedas
}

fun formatMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = Math.abs(cents)
    return "%s$%,d.%02d".format(Locale.US, sign, abs / 100, abs % 100)
}

fun sectionTotal(kind: Kind, counts: Map<String, Int>): Long =
    Denominations.of(kind).sumOf { it.cents * (counts[it.id] ?: 0) }

fun grandTotal(counts: Map<String, Int>): Long =
    sectionTotal(Kind.BILLETES, counts) + sectionTotal(Kind.MONEDAS, counts)

/** Una línea del ticket. La impresora usa los indicadores para dar formato. */
data class ReportLine(
    val text: String,
    val bold: Boolean = false,
    val center: Boolean = false,
    val big: Boolean = false,
)

/** Ancho de una impresora térmica de 58 mm con letra normal. */
const val LINE_WIDTH = 32

private fun row(left: String, right: String): String =
    left + " ".repeat(maxOf(1, LINE_WIDTH - left.length - right.length)) + right

fun buildReport(counts: Map<String, Int>, date: Date): List<ReportLine> {
    val sep = ReportLine("-".repeat(LINE_WIDTH))
    val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
    val hora = SimpleDateFormat("hh:mm a", Locale.US).format(date)

    val lines = mutableListOf(
        ReportLine("CUADRE DE CAJA", bold = true, center = true, big = true),
        ReportLine(""),
        ReportLine("Fecha: $fecha"),
        ReportLine("Hora:  $hora"),
        sep,
    )

    for (kind in Kind.values()) {
        val counted = Denominations.of(kind).filter { (counts[it.id] ?: 0) > 0 }
        if (counted.isEmpty()) continue
        lines += ReportLine(kind.title.uppercase(), bold = true)
        for (d in counted) {
            val qty = counts.getValue(d.id)
            val name = if (kind == Kind.BILLETES) "$qty x ${d.label}" else "$qty x ${formatMoney(d.cents)}"
            lines += ReportLine(row(name, formatMoney(qty * d.cents)))
        }
        lines += ReportLine(row("Total ${kind.title.lowercase()}:", formatMoney(sectionTotal(kind, counts))))
        lines += sep
    }

    lines += ReportLine("TOTAL EN CAJA", bold = true, center = true)
    lines += ReportLine(formatMoney(grandTotal(counts)), bold = true, center = true, big = true)
    return lines
}

/** Versión en texto plano para mostrar en pantalla, tal como sale impreso. */
fun reportToText(lines: List<ReportLine>): String = lines.joinToString("\n") { line ->
    // Las líneas grandes ocupan el doble de ancho en la impresora.
    val width = if (line.big) line.text.length * 2 else line.text.length
    val text = if (line.big) line.text.toCharArray().joinToString(" ") else line.text
    if (line.center) " ".repeat(maxOf(0, (LINE_WIDTH - width) / 2)) + text else text
}

/** Parte el texto en renglones de [width] letras sin cortar palabras (salvo las muy largas). */
fun wrapText(text: String, width: Int = LINE_WIDTH): List<String> =
    text.trimEnd().lines().flatMap { paragraph ->
        if (paragraph.isBlank()) return@flatMap listOf("")
        val out = mutableListOf<String>()
        var current = ""
        for (word in paragraph.trim().split(Regex("\\s+"))) {
            var w = word
            while (w.length > width) {
                if (current.isNotEmpty()) { out += current; current = "" }
                out += w.take(width)
                w = w.drop(width)
            }
            current = when {
                current.isEmpty() -> w
                current.length + 1 + w.length <= width -> "$current $w"
                else -> { out += current; w }
            }
        }
        if (current.isNotEmpty()) out += current
        out
    }

/** Ticket de un mensaje libre, con fecha y hora. */
fun buildMessage(text: String, date: Date): List<ReportLine> {
    val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
    val hora = SimpleDateFormat("hh:mm a", Locale.US).format(date)
    return listOf(
        ReportLine("MENSAJE", bold = true, center = true, big = true),
        ReportLine(""),
        ReportLine(row("Fecha: $fecha", hora)),
        ReportLine("-".repeat(LINE_WIDTH)),
    ) + wrapText(text).map { ReportLine(it) }
}
