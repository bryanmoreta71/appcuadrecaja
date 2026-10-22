package com.cuadrecaja.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cuadrecaja.app.ui.CountScreen
import com.cuadrecaja.app.ui.Green
import com.cuadrecaja.app.ui.HomeScreen
import com.cuadrecaja.app.ui.ReportScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date

private sealed interface Screen {
    data object Home : Screen
    data class Count(val kind: Kind) : Screen
    data class Report(val lines: List<ReportLine>) : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: CashViewModel by viewModels()
    private lateinit var printer: BluetoothPrinter

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var printStatus by mutableStateOf("")
    private var printing by mutableStateOf(false)
    private var pickerDevices by mutableStateOf<List<BluetoothDevice>?>(null)
    private var confirmReset by mutableStateOf(false)
    private val foundDevices = mutableStateListOf<BluetoothDevice>()
    private var scanning by mutableStateOf(false)
    private var scanJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) printCurrentReport()
            else printStatus = "Se necesita permiso de Bluetooth para imprimir."
        }

    private val scanPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) startScan()
            else printStatus = "Se necesita permiso para buscar la impresora."
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (printer.isEnabled) printCurrentReport()
            else printStatus = "Encienda el Bluetooth para imprimir."
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        printer = BluetoothPrinter(this)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondaryContainer = Color(0xFFE3EEE8))) {
                val current = screen
                BackHandler(enabled = current != Screen.Home) { screen = Screen.Home }

                Scaffold(
                    containerColor = Color(0xFFF3F5F4),
                    topBar = {
                        TopAppBar(
                            title = { Text(titleFor(current), fontSize = 22.sp) },
                            navigationIcon = {
                                if (current != Screen.Home) {
                                    IconButton(onClick = { screen = Screen.Home }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Green,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                            ),
                        )
                    },
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        when (current) {
                            Screen.Home -> HomeScreen(
                                counts = viewModel.counts,
                                onOpen = { screen = Screen.Count(it) },
                                onCuadrar = ::openReport,
                                onReset = { confirmReset = true },
                            )
                            is Screen.Count -> CountScreen(current.kind, viewModel.counts, viewModel::setCount)
                            is Screen.Report -> ReportScreen(
                                reportText = reportToText(current.lines),
                                status = printStatus,
                                printing = printing,
                                onPrint = ::printCurrentReport,
                                onChangePrinter = {
                                    printer.savedAddress = null
                                    if (hasBluetoothPermission()) pickerDevices = printer.pairedDevices()
                                    else printCurrentReport()
                                },
                            )
                        }
                    }
                }

                pickerDevices?.let { devices -> PrinterPicker(devices) }
                LaunchedEffect(pickerDevices == null) { if (pickerDevices == null) stopScan() }
                if (confirmReset) ResetDialog()
            }
        }
    }

    private fun titleFor(screen: Screen) = when (screen) {
        Screen.Home -> "Cuadre de Caja"
        is Screen.Count -> screen.kind.title
        is Screen.Report -> "Reporte"
    }

    /** "Cuadrar caja": arma el reporte con fecha y hora actuales y lo manda a imprimir. */
    private fun openReport() {
        printStatus = ""
        screen = Screen.Report(buildReport(viewModel.counts.toMap(), Date()))
        printCurrentReport()
    }

    private fun hasBluetoothPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun printCurrentReport() {
        val report = screen as? Screen.Report ?: return
        if (printing) return

        if (!printer.isSupported) {
            printStatus = "Este teléfono no tiene Bluetooth."
            return
        }
        if (!hasBluetoothPermission()) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        if (!printer.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        val device = printer.findPrinter()
        if (device == null) {
            pickerDevices = printer.pairedDevices()
            startScan()
            return
        }
        print(device, report.lines)
    }

    @SuppressLint("MissingPermission")
    private fun print(device: BluetoothDevice, lines: List<ReportLine>) {
        printing = true
        printStatus = "Imprimiendo en ${device.name ?: device.address}…"
        lifecycleScope.launch {
            printStatus = try {
                printer.print(device, lines)
                "✓ Reporte impreso"
            } catch (e: Exception) {
                "No se pudo imprimir. Verifique que la impresora esté encendida y cerca, y toque \"Imprimir reporte\"."
            }
            printing = false
        }
    }

    // ---------- Buscar y emparejar la impresora desde la app ----------

    private fun scanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun startScan() {
        val missing = scanPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            scanPermissionLauncher.launch(missing.toTypedArray())
            return
        }
        if (pickerDevices == null) pickerDevices = printer.pairedDevices()
        foundDevices.clear()
        scanning = true
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            printer.discover().collect { d ->
                if (foundDevices.none { it.address == d.address }) foundDevices += d
            }
            scanning = false
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanning = false
    }

    @SuppressLint("MissingPermission")
    private fun pairAndPrint(device: BluetoothDevice) {
        pickerDevices = null
        printing = true
        printStatus = "Emparejando con ${device.name ?: device.address}… Si pide PIN, pruebe 0000 o 1234."
        lifecycleScope.launch {
            val paired = printer.pair(device)
            printing = false
            if (!paired) {
                printStatus = "No se pudo emparejar. Apague y encienda la impresora e intente de nuevo."
                return@launch
            }
            (screen as? Screen.Report)?.let { print(device, it.lines) }
        }
    }

    @SuppressLint("MissingPermission")
    @androidx.compose.runtime.Composable
    private fun PrinterPicker(devices: List<BluetoothDevice>) {
        AlertDialog(
            onDismissRequest = { pickerDevices = null },
            title = { Text("Elija la impresora") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (devices.isNotEmpty()) {
                        Text("Emparejadas", fontWeight = FontWeight.Bold)
                        for (d in devices) {
                            DeviceRow(d) {
                                pickerDevices = null
                                (screen as? Screen.Report)?.let { print(d, it.lines) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        "Para imprimir en la computadora, elija su nombre en la lista de emparejadas " +
                            "(debe tener abierto el receptor). Para una impresora Bluetooth, enciéndala " +
                            "y búsquela aquí.",
                    )
                    Spacer(Modifier.height(8.dp))
                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Buscando…")
                        }
                    } else {
                        OutlinedButton(onClick = ::startScan, modifier = Modifier.fillMaxWidth()) {
                            Text("Buscar impresoras cercanas")
                        }
                    }
                    for (d in foundDevices) {
                        DeviceRow(d) { pairAndPrint(d) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickerDevices = null }) { Text("Cancelar") } },
        )
    }

    @SuppressLint("MissingPermission")
    @androidx.compose.runtime.Composable
    private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
        val marker = when {
            device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER -> "💻 "
            printer.looksLikePrinter(device) -> "🖨️ "
            else -> ""
        }
        Text(
            marker + (device.name ?: device.address),
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }

    @androidx.compose.runtime.Composable
    private fun ResetDialog() {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Empezar de nuevo") },
            text = { Text("¿Borrar todo lo contado?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    confirmReset = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }
}
