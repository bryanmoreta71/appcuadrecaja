package com.cuadrecaja.app.impresion

import java.io.ByteArrayOutputStream

/** Comandos ESC/POS para imprimir una imagen (la plantilla ya dibujada) con varias copias. */
object EscPos {
    private const val ESC: Byte = 0x1b
    private const val GS: Byte = 0x1d

    /** Filas por cada comando de imagen: algunas impresoras no aceptan imágenes muy altas de una vez. */
    private const val FILAS_POR_BANDA = 128

    /**
     * Convierte una imagen en blanco y negro (true = punto negro) al comando de imagen
     * GS v 0, partida en bandas.
     */
    fun imagen(ancho: Int, alto: Int, negro: BooleanArray): ByteArray {
        require(negro.size == ancho * alto)
        val bytesPorFila = (ancho + 7) / 8
        val out = ByteArrayOutputStream()
        var inicio = 0
        while (inicio < alto) {
            val filas = minOf(FILAS_POR_BANDA, alto - inicio)
            out.write(byteArrayOf(GS, 0x76, 0x30, 0))
            out.write(bytesPorFila and 0xff); out.write(bytesPorFila shr 8)
            out.write(filas and 0xff); out.write(filas shr 8)
            for (y in inicio until inicio + filas) {
                for (bx in 0 until bytesPorFila) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x < ancho && negro[y * ancho + x]) b = b or (0x80 shr bit)
                    }
                    out.write(b)
                }
            }
            inicio += filas
        }
        return out.toByteArray()
    }

    /**
     * Arma el trabajo completo: la imagen tantas veces como copias, con avance y corte entre
     * cada una. Después de la última no se corta aquí: lo hace el receptor de Windows (o se
     * arranca a mano en las impresoras portátiles), igual que con el cuadre.
     */
    fun trabajo(imagen: ByteArray, copias: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(ESC, 0x40))      // inicializar
        out.write(byteArrayOf(ESC, 0x20, 0))   // separación normal (el receptor la cambia para el cuadre)
        out.write(byteArrayOf(ESC, 0x61, 0))   // alineación izquierda: la imagen ya trae su propio diseño
        val n = copias.coerceIn(1, 20)
        for (i in 1..n) {
            out.write(imagen)
            if (i < n) {
                out.write(byteArrayOf(ESC, 0x64, 5))       // avanzar hasta la cuchilla
                out.write(byteArrayOf(GS, 0x56, 66, 0))    // cortar
            }
        }
        out.write(byteArrayOf(0x0a, 0x0a, 0x0a, 0x0a))
        return out.toByteArray()
    }
}
