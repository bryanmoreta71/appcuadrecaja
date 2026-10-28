package com.cuadrecaja.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cuadrecaja.app.impresion.EscPos
import com.cuadrecaja.app.impresion.PaqueteComputadora
import com.cuadrecaja.app.impresion.Renderizador
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.ui.AjustesScreen
import com.cuadrecaja.app.ui.CountScreen
import com.cuadrecaja.app.ui.DetalleHistorialScreen
import com.cuadrecaja.app.ui.EditorVisualScreen
import com.cuadrecaja.app.ui.HistorialScreen
import com.cuadrecaja.app.ui.HomeScreen
import com.cuadrecaja.app.ui.InicioImpresion
import com.cuadrecaja.app.ui.MessageScreen
import com.cuadrecaja.app.ui.NotaEntregaScreen
import com.cuadrecaja.app.ui.PlantillasScreen
import com.cuadrecaja.app.ui.ReportScreen
import com.cuadrecaja.app.ui.StockEditorScreen
import com.cuadrecaja.app.ui.StockListasScreen
import com.cuadrecaja.app.ui.StockSecuenciaScreen
import com.cuadrecaja.app.stock.ListaStock
import com.cuadrecaja.app.stock.StockViewModel
import com.cuadrecaja.app.stock.plantillaImpresion
import com.cuadrecaja.app.ui.theme.LocalPaleta
import com.cuadrecaja.app.ui.theme.TemaApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date

private sealed interface Screen {
    data object Home : Screen
    data class Count(val kind: Kind) : Screen
    data class Report(val lines: List<ReportLine>) : Screen
    data object Message : Screen
    data object Nota : Screen
    data object Plantillas : Screen
    data class Editor(val id: String) : Screen
    data object Historial : Screen
    data class DetalleHistorial(val id: String) : Screen
    data object Ajustes : Screen
    data object Stock : Screen
    data class StockEditar(val id: String) : Screen
    data class StockSecuencia(val id: String) : Screen
}

/** Lo que se va a mandar a la impresora, sea cuadre, mensaje, nota o reimpresión. */
private class Trabajo(
    val titulo: String,
    val tipo: TipoImpresion,
    val copias: Int,
    val datos: ByteArray,
    val texto: String?,
    val imagen: Bitmap?,
    val guardarEnHistorial: Boolean = true,
    /** Se llama cuando se imprimió bien (por ejemplo, para recordar lo escrito en la nota). */
    val alImprimir: (() -> Unit)? = null,
    /** Solo va a la computadora (por ejemplo, una plantilla para el programa de Windows). */
    val soloComputadora: Boolean = false,
    val mensajeOk: String? = null,
)

class MainActivity : ComponentActivity() {

    private val viewModel: CashViewModel by viewModels()
    private val impresion: ImpresionViewModel by viewModels()
    private val stock: StockViewModel by viewModels()
    /** Código que se muestra en "Sacar stock" (se mueve también con los botones de volumen). */
    private var indiceStock by mutableIntStateOf(0)
    private lateinit var printer: BluetoothPrinter

    private val pila = mutableStateListOf<Screen>(Screen.Home)
    private val screen: Screen get() = pila.last()

    private var printStatus by mutableStateOf("")
    private var printing by mutableStateOf(false)
    private var pendiente: Trabajo? = null
    private var pickerDevices by mutableStateOf<List<BluetoothDevice>?>(null)
    private var confirmReset by mutableStateOf(false)
    private var menuOpen by mutableStateOf(false)
    private var messageText by mutableStateOf("")
    private val foundDevices = mutableStateListOf<BluetoothDevice>()
    private var scanning by mutableStateOf(false)
    private var scanJob: Job? = null
    private var impresoraGuardada by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) continuarImpresion()
            else printStatus = "Se necesita permiso de Bluetooth para imprimir."
        }

    private val scanPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) startScan()
            else printStatus = "Se necesita permiso para buscar la impresora."
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (printer.isEnabled) continuarImpresion()
            else printStatus = "Encienda el Bluetooth para imprimir."
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        printer = BluetoothPrinter(this)
        impresoraGuardada = nombreImpresoraGuardada()

        setContent {
            TemaApp(impresion.modo) {
                val current = screen
                BackHandler(enabled = pila.size > 1) { atras() }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(titleFor(current), fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                            navigationIcon = {
                                if (pila.size > 1) {
                                    IconButton(onClick = ::atras) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = LocalPaleta.current.principal,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White,
                            ),
                            actions = { MenuOpciones() },
                        )
                    },
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
                        Contenido(current)
                    }
                }

                pickerDevices?.let { devices -> PrinterPicker(devices) }
                LaunchedEffect(pickerDevices == null) { if (pickerDevices == null) stopScan() }
                if (confirmReset) ResetDialog()
            }
        }
    }

    /** En "Sacar stock", los botones de volumen pasan al siguiente o al anterior código. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val s = screen
        if (s is Screen.StockSecuencia) {
            val total = stock.lista(s.id)?.codigos?.count { it.codigo.isNotBlank() } ?: 0
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { indiceStock = (indiceStock + 1).coerceAtMost(maxOf(0, total - 1)); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { indiceStock = (indiceStock - 1).coerceAtLeast(0); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Navegación ----------

    private fun ir(s: Screen) {
        printStatus = ""
        pila += s
    }

    private fun atras() {
        if (pila.size > 1) pila.removeAt(pila.lastIndex)
        printStatus = ""
    }

    private fun cambiarModo(m: Modo) {
        impresion.cambiarModo(m)
        pila.clear()
        pila += Screen.Home
        printStatus = ""
    }

    private fun titleFor(screen: Screen) = when (screen) {
        Screen.Home -> if (impresion.modo == Modo.CUADRE) "Cuadre de Caja" else "Impresión"
        is Screen.Count -> screen.kind.title
        is Screen.Report -> "Reporte"
        Screen.Message -> "Mensaje"
        Screen.Nota -> "Nota de entrega"
        Screen.Plantillas -> "Plantillas"
        is Screen.Editor -> "Editar plantilla"
        Screen.Historial -> "Historial"
        is Screen.DetalleHistorial -> "Ticket impreso"
        Screen.Ajustes -> "Ajustes"
        Screen.Stock -> "Sacar stock"
        is Screen.StockEditar -> "Editar lista"
        is Screen.StockSecuencia -> "Sacar stock"
    }

    @Composable
    private fun OpcionMenu(texto: String, accion: () -> Unit) {
        DropdownMenuItem(
            text = { Text(texto, fontSize = 17.sp) },
            onClick = {
                menuOpen = false
                accion()
            },
        )
    }

    @Composable
    private fun MenuOpciones() {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            OpcionMenu("📦   Nota de entrega") { ir(Screen.Nota) }
            OpcionMenu("✉️   Imprimir mensaje") { ir(Screen.Message) }
            OpcionMenu("🎨   Plantillas") { ir(Screen.Plantillas) }
            OpcionMenu("🕘   Historial") { ir(Screen.Historial) }
            OpcionMenu("🏷️   Sacar stock") { ir(Screen.Stock) }
            OpcionMenu("⚙️   Ajustes") { ir(Screen.Ajustes) }
            HorizontalDivider()
            if (impresion.modo == Modo.CUADRE) {
                OpcionMenu("🖨️   Cambiar a modo impresión") { cambiarModo(Modo.IMPRESION) }
            } else {
                OpcionMenu("💵   Cambiar a modo cuadre") { cambiarModo(Modo.CUADRE) }
            }
        }
    }

    @Composable
    private fun Contenido(current: Screen) {
        when (current) {
            Screen.Home -> if (impresion.modo == Modo.CUADRE) {
                HomeScreen(
                    counts = viewModel.counts,
                    onCambiarModo = ::cambiarModo,
                    onOpen = { ir(Screen.Count(it)) },
                    onCuadrar = ::openReport,
                    onReset = { confirmReset = true },
                )
            } else {
                InicioImpresion(
                    entradas = impresion.entradas,
                    onCambiarModo = ::cambiarModo,
                    onNota = { ir(Screen.Nota) },
                    onMensaje = { ir(Screen.Message) },
                    onPlantillas = { ir(Screen.Plantillas) },
                    onHistorial = { ir(Screen.Historial) },
                    onAbrirEntrada = { ir(Screen.DetalleHistorial(it.id)) },
                )
            }
            is Screen.Count -> CountScreen(current.kind, viewModel.counts, viewModel::setCount)
            is Screen.Report -> ReportScreen(
                reportText = reportToText(current.lines),
                status = printStatus,
                printing = printing,
                onPrint = { imprimir(trabajoCuadre(current.lines)) },
                onChangePrinter = {
                    printer.savedAddress = null
                    impresoraGuardada = null
                    pendiente = trabajoCuadre(current.lines)
                    if (hasBluetoothPermission()) pickerDevices = printer.pairedDevices() else continuarImpresion()
                },
            )
            Screen.Message -> MessageScreen(
                text = messageText,
                onTextChange = { messageText = it },
                status = printStatus,
                printing = printing,
                onPrint = {
                    val lines = buildMessage(messageText, Date())
                    imprimir(
                        Trabajo(
                            "Mensaje: " + messageText.trim().lineSequence().first().take(30),
                            TipoImpresion.MENSAJE, 1, BluetoothPrinter.buildEscPos(lines), reportToText(lines), null,
                        ),
                    )
                },
            )
            Screen.Nota -> {
                val plantilla = impresion.plantilla(impresion.plantillaNota) ?: impresion.plantillas.first()
                NotaEntregaScreen(
                    plantillas = impresion.plantillas,
                    plantilla = plantilla,
                    datos = impresion.datos(),
                    onElegirPlantilla = { impresion.plantillaNota = it },
                    onCampo = { id, v -> impresion.campos[id] = v },
                    onFilas = { id, filas -> impresion.filas[id] = filas },
                    renderizar = { p, d -> renderizar(p, d) },
                    status = printStatus,
                    printing = printing,
                    onImprimir = { copias -> imprimirNota(plantilla, copias) },
                    onLimpiar = impresion::limpiarNota,
                    onEditarPlantilla = { ir(Screen.Editor(plantilla.id)) },
                    sugerencias = impresion::sugerencias,
                    onOlvidar = impresion::olvidar,
                )
            }
            Screen.Plantillas -> PlantillasScreen(
                plantillas = impresion.plantillas,
                onEditar = { ir(Screen.Editor(it.id)) },
                onNueva = { ir(Screen.Editor(impresion.nuevaPlantilla().id)) },
                onDuplicar = impresion::duplicar,
                onEliminar = impresion::eliminar,
                onActualizarComputadora = ::actualizarEnComputadora,
                estado = printStatus,
                enviando = printing,
            )
            is Screen.Editor -> {
                val plantilla = impresion.plantilla(current.id)
                if (plantilla == null) {
                    LaunchedEffect(Unit) { atras() }
                } else {
                    EditorVisualScreen(
                        plantilla = plantilla,
                        papelMm = impresion.papelMm,
                        onCambio = impresion::guardarPlantilla,
                        renderizar = { p -> renderizar(p, Datos(), ejemplo = true) },
                        guardarImagen = impresion::guardarImagen,
                        onListo = ::atras,
                        onLlenar = {
                            impresion.plantillaNota = plantilla.id
                            atras()
                            if (screen != Screen.Nota) ir(Screen.Nota)
                        },
                    )
                }
            }
            Screen.Historial -> HistorialScreen(impresion.entradas) { ir(Screen.DetalleHistorial(it.id)) }
            is Screen.DetalleHistorial -> {
                val entrada = impresion.entradas.firstOrNull { it.id == current.id }
                if (entrada == null) {
                    LaunchedEffect(Unit) { atras() }
                } else {
                    val imagen = remember(entrada.id) { impresion.historial.imagen(entrada.id) }
                    DetalleHistorialScreen(
                        entrada = entrada,
                        imagen = imagen,
                        status = printStatus,
                        printing = printing,
                        onReimprimir = {
                            val datos = impresion.historial.datos(entrada.id)
                            if (datos == null) printStatus = "No se encontró el ticket guardado."
                            else imprimir(Trabajo(entrada.titulo, entrada.tipo, entrada.copias, datos, null, null, guardarEnHistorial = false))
                        },
                        onEliminar = {
                            impresion.historial.borrar(entrada.id)
                            impresion.refrescarHistorial()
                        },
                    )
                }
            }
            Screen.Stock -> StockListasScreen(
                listas = stock.listas,
                onAbrir = { l ->
                    if (l.codigos.none { it.codigo.isNotBlank() }) ir(Screen.StockEditar(l.id))
                    else { indiceStock = 0; ir(Screen.StockSecuencia(l.id)) }
                },
                onEditar = { ir(Screen.StockEditar(it.id)) },
                onEliminar = { stock.eliminar(it.id) },
                onNueva = { ir(Screen.StockEditar(stock.nueva().id)) },
                onImprimir = ::imprimirListaStock,
                estado = printStatus,
                imprimiendo = printing,
            )
            is Screen.StockEditar -> {
                val l = stock.lista(current.id)
                if (l == null) LaunchedEffect(Unit) { atras() }
                else StockEditorScreen(l, stock::guardar) {
                    indiceStock = 0
                    ir(Screen.StockSecuencia(l.id))
                }
            }
            is Screen.StockSecuencia -> {
                val l = stock.lista(current.id)
                if (l == null) LaunchedEffect(Unit) { atras() }
                else StockSecuenciaScreen(
                    lista = l,
                    indice = indiceStock,
                    onIndice = { indiceStock = it.coerceIn(0, maxOf(0, l.codigos.count { c -> c.codigo.isNotBlank() } - 1)) },
                    onTerminar = ::atras,
                    onEditar = { ir(Screen.StockEditar(l.id)) },
                    brilloManual = stock.brilloManual,
                    brillo = stock.brillo,
                    fondoNegro = stock.fondoNegro,
                    onPantalla = stock::cambiarPantalla,
                )
            }
            Screen.Ajustes -> AjustesScreen(
                modo = impresion.modo,
                papelMm = impresion.papelMm,
                impresora = impresoraGuardada,
                onModo = ::cambiarModo,
                onPapel = impresion::cambiarPapel,
                onOlvidarImpresora = {
                    printer.savedAddress = null
                    impresoraGuardada = null
                },
                recuerdos = impresion.versionMemoria.let { impresion.memoria.cantidad },
                onBorrarMemoria = impresion::borrarMemoria,
            )
        }
    }

    // ---------- Armar trabajos ----------

    private fun renderizar(p: Plantilla, d: Datos, ejemplo: Boolean = false): Renderizador.Resultado =
        Renderizador(this).dibujar(p, d, if (impresion.papelMm == 58) 384 else 576, Date(), ejemplo)

    private fun trabajoCuadre(lines: List<ReportLine>) =
        Trabajo("Cuadre de caja", TipoImpresion.CUADRE, 1, BluetoothPrinter.buildEscPos(lines), reportToText(lines), null)

    /** "Cuadrar caja": arma el reporte con fecha y hora actuales y lo manda a imprimir. */
    private fun openReport() {
        val lines = buildReport(viewModel.counts.toMap(), Date())
        ir(Screen.Report(lines))
        imprimir(trabajoCuadre(lines))
    }

    private fun imprimirNota(plantilla: Plantilla, copias: Int) {
        val datos = impresion.datos()
        val img = renderizar(plantilla, datos).imagen
        val raster = EscPos.imagen(img.width, img.height, Renderizador.puntosNegros(img))
        val principal = plantilla.campos.map { datos.campos[it.id].orEmpty() }.firstOrNull { it.isNotBlank() }
        imprimir(
            Trabajo(
                plantilla.nombre + (principal?.let { ": $it" } ?: ""),
                TipoImpresion.NOTA, copias, EscPos.trabajo(raster, copias), null, img,
                alImprimir = { impresion.recordarNota(plantilla, datos) },
            ),
        )
    }

    /** La lista de códigos impresa: nombre, código de barras y números de cada uno. */
    private fun imprimirListaStock(l: ListaStock) {
        val r = renderizar(plantillaImpresion(l), Datos())
        val img = r.imagen
        val raster = EscPos.imagen(img.width, img.height, Renderizador.puntosNegros(img))
        imprimir(
            Trabajo(
                "Lista: ${l.nombre}", TipoImpresion.STOCK, 1, EscPos.trabajo(raster, 1), null, img,
                mensajeOk = if (r.avisos.isEmpty()) null else "✓ Impreso\n" + r.avisos.joinToString("\n"),
            ),
        )
    }

    // ---------- Impresión ----------

    private fun hasBluetoothPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun imprimir(t: Trabajo) {
        if (printing) return
        pendiente = t
        continuarImpresion()
    }

    /** Revisa permiso, Bluetooth encendido e impresora; luego imprime lo pendiente. */
    private fun continuarImpresion() {
        if (pendiente == null || printing) return
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
        val soloComputadora = pendiente?.soloComputadora == true
        val device = if (soloComputadora) printer.findComputer() else printer.findPrinter()
        if (device == null) {
            if (soloComputadora) {
                val computadoras = printer.pairedDevices().filter(printer::esComputadora)
                if (computadoras.isEmpty()) {
                    printStatus = "No hay ninguna computadora emparejada con este celular. Emparéjela en los ajustes de Bluetooth."
                    pendiente = null
                } else {
                    pickerDevices = computadoras
                }
                return
            }
            pickerDevices = printer.pairedDevices()
            startScan()
            return
        }
        print(device)
    }

    /** Manda la plantilla al programa "Nota de entrega" de la computadora. */
    private fun actualizarEnComputadora(p: Plantilla) {
        val paquete = PaqueteComputadora.crear(this, p, if (impresion.papelMm == 58) 384 else 576, impresion.memoria)
        imprimir(
            Trabajo(
                "Plantilla: ${p.nombre}", TipoImpresion.NOTA, 1, paquete.datos, null, null,
                guardarEnHistorial = false,
                soloComputadora = true,
                mensajeOk = "✓ \"${p.nombre}\" actualizada en la computadora" +
                    paquete.avisos.joinToString("") { "\n$it" },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun nombreImpresoraGuardada(): String? {
        val address = printer.savedAddress ?: return null
        if (!hasBluetoothPermission()) return address
        return printer.pairedDevices().firstOrNull { it.address == address }?.name ?: address
    }

    @SuppressLint("MissingPermission")
    private fun print(device: BluetoothDevice) {
        val t = pendiente ?: return
        if (t.soloComputadora && !printer.esComputadora(device)) {
            printStatus = "Eso es una impresora. Para actualizar la plantilla elija la computadora."
            return
        }
        printing = true
        val nombre = device.name ?: device.address
        printStatus = if (t.soloComputadora) "Enviando a $nombre…" else if (t.copias > 1) "Imprimiendo ${t.copias} copias en $nombre…" else "Imprimiendo en $nombre…"
        lifecycleScope.launch {
            printStatus = try {
                val entrega = printer.print(device, t.datos)
                impresoraGuardada = nombre
                if (t.guardarEnHistorial) {
                    impresion.historial.agregar(t.titulo, t.tipo, t.copias, t.datos, t.texto, t.imagen)
                    impresion.refrescarHistorial()
                }
                pendiente = null
                t.alImprimir?.invoke()
                when {
                    t.soloComputadora && entrega == BluetoothPrinter.Entrega.SIN_CONFIRMAR ->
                        "La computadora no respondió. Actualice el receptor de Windows (zip nuevo) y vuelva a intentar."
                    t.mensajeOk != null -> t.mensajeOk
                    entrega == BluetoothPrinter.Entrega.SIN_CONFIRMAR ->
                        "✓ Enviado a la computadora. (Actualice el receptor de Windows para que confirme la impresión.)"
                    t.copias > 1 -> "✓ ${t.copias} copias impresas"
                    else -> "✓ Impreso"
                }
            } catch (e: BluetoothPrinter.ErrorReceptor) {
                "La computadora no imprimió: ${e.message}. Toque \"Imprimir\" de nuevo."
            } catch (e: Exception) {
                "No se pudo imprimir. Verifique que la impresora esté encendida y cerca, y toque \"Imprimir\" de nuevo."
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
            print(device)
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun PrinterPicker(devices: List<BluetoothDevice>) {
        AlertDialog(
            onDismissRequest = { pickerDevices = null },
            title = { Text(if (pendiente?.soloComputadora == true) "Elija la computadora" else "Elija la impresora") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (devices.isNotEmpty()) {
                        Text("Emparejadas", fontWeight = FontWeight.Bold)
                        for (d in devices) {
                            DeviceRow(d) {
                                pickerDevices = null
                                print(d)
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
    @Composable
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

    @Composable
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
