package com.cuadrecaja.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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
        val major = device.bluetoothClass?.majorDeviceClass
        // También sirve una computadora con el receptor de Windows (carpeta windows/).
        if (major == BluetoothClass.Device.Major.IMAGING || major == BluetoothClass.Device.Major.COMPUTER) return true
        val name = device.name?.uppercase().orEmpty()
        return PRINTER_NAME_HINTS.any { name.contains(it) }
    }

    /**
     * Busca dispositivos Bluetooth cercanos. Se usa cuando la impresora no se deja emparejar
     * desde los ajustes del teléfono (por ejemplo, cuando el teléfono la toma como
     * "acceso a Internet" en vez de impresora).
     */
    @SuppressLint("MissingPermission")
    fun discover(): Flow<BluetoothDevice> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND ->
                        intent.bluetoothDevice()?.let { if (it.bondState != BluetoothDevice.BOND_BONDED) trySend(it) }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        if (adapter?.startDiscovery() != true) close()
        awaitClose {
            adapter?.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Empareja la impresora directamente desde la app, sin pedirle al teléfono que la "conecte".
     * El teléfono puede pedir un PIN: casi siempre es 0000 o 1234.
     */
    @SuppressLint("MissingPermission")
    suspend fun pair(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        adapter?.cancelDiscovery()
        return withTimeoutOrNull(90_000) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.bluetoothDevice()?.address != device.address) return
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                            context.unregisterReceiver(this)
                            if (cont.isActive) cont.resume(state == BluetoothDevice.BOND_BONDED)
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context, receiver,
                    IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                if (!device.createBond()) {
                    context.unregisterReceiver(receiver)
                    cont.resume(false)
                }
            }
        } ?: false
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

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
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
