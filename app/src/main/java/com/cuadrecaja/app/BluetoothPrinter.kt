package com.cuadrecaja.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.Normalizer
import java.util.UUID

/**
 * Impresión en impresoras térmicas Bluetooth (ESC/POS, 58 mm) por Bluetooth clásico (SPP),
 * que es como se conectan las impresoras portátiles tipo POS.
 *
 * La impresora debe estar emparejada en los ajustes de Bluetooth del teléfono.
 * La app la detecta sola entre los dispositivos emparejados y recuerda la que se usó.
 */
class BluetoothPrinter(private val context: Context) {

    private val prefs = context.getSharedPreferences("printer", Context.MODE_PRIVATE)

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    var savedAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_ADDRESS, value).apply()

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList().orEmpty()

    /**
     * Elige la impresora automáticamente: la que se usó la última vez, o si no,
     * la única emparejada que parezca impresora. Devuelve null si no puede decidir.
     */
    @SuppressLint("MissingPermission")
    fun findPrinter(): BluetoothDevice? {
        val devices = pairedDevices()
        devices.firstOrNull { it.address == savedAddress }?.let { return it }
        val printers = devices.filter(::looksLikePrinter)
        return printers.singleOrNull() ?: devices.singleOrNull()
    }

    @SuppressLint("MissingPermission")
    fun looksLikePrinter(device: BluetoothDevice): Boolean {
        if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING) return true
        val name = device.name?.uppercase().orEmpty()
        return PRINTER_NAME_HINTS.any { name.contains(it) }
    }

    @SuppressLint("MissingPermission")
    suspend fun print(device: BluetoothDevice, lines: List<ReportLine>) = withContext(Dispatchers.IO) {
        adapter?.cancelDiscovery()
        val data = buildEscPos(lines)
        val socket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            // Algunas impresoras solo aceptan la conexión "insegura".
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        }
        socket.use {
            it.outputStream.write(data)
            it.outputStream.flush()
            // Dar tiempo a que la impresora reciba todo antes de cerrar la conexión.
            Thread.sleep(800)
        }
        savedAddress = device.address
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val PRINTER_NAME_HINTS = listOf(
            "PRINT", "POS", "PT-", "MTP", "MPT", "RPP", "BT-", "TP-", "58", "80", "THERMAL", "IMPRES",
        )

        private const val ESC: Byte = 0x1b
        private const val GS: Byte = 0x1d

        /** Quita tildes y símbolos que la impresora no tiene en su tabla de caracteres. */
        fun toAscii(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace("¢", "c")
                .replace(Regex("[^\\x20-\\x7e]"), "")

        fun buildEscPos(lines: List<ReportLine>): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(ESC, 0x40)) // inicializar
            for (line in lines) {
                out.write(byteArrayOf(ESC, 0x61, if (line.center) 1 else 0)) // alineación
                out.write(byteArrayOf(ESC, 0x45, if (line.bold) 1 else 0)) // negrita
                out.write(byteArrayOf(GS, 0x21, if (line.big) 0x11 else 0x00)) // doble alto y ancho
                out.write(toAscii(line.text).toByteArray(Charsets.US_ASCII))
                out.write(0x0a)
            }
            out.write(byteArrayOf(ESC, 0x61, 0, ESC, 0x45, 0, GS, 0x21, 0))
            out.write(byteArrayOf(0x0a, 0x0a, 0x0a, 0x0a)) // avanzar papel para cortar
            return out.toByteArray()
        }
    }
}
