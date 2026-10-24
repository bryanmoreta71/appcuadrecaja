package com.cuadrecaja.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.cuadrecaja.app.impresion.Imagenes
import com.cuadrecaja.app.plantillas.Datos
import com.cuadrecaja.app.plantillas.Elemento
import com.cuadrecaja.app.plantillas.Plantilla
import com.cuadrecaja.app.plantillas.PlantillasPorDefecto
import com.cuadrecaja.app.plantillas.nuevoId

/** Estado del modo impresión: ajustes, plantillas, lo que se llena en la nota y el historial. */
class ImpresionViewModel(app: Application) : AndroidViewModel(app) {

    val ajustes = Ajustes(app)
    private val almacen = AlmacenPlantillas(app)
    val historial = Historial(app)

    var modo by mutableStateOf(ajustes.modo)
        private set
    var papelMm by mutableStateOf(ajustes.papelMm)
        private set

    val plantillas = mutableStateListOf<Plantilla>().apply { addAll(almacen.cargar()) }
    var plantillaNota by mutableStateOf(plantillas.first().id)

    /** Valores de los campos (id del campo -> texto) y filas de las tablas (id de la tabla -> filas). */
    val campos = mutableStateMapOf<String, String>()
    val filas = mutableStateMapOf<String, List<List<String>>>()

    val entradas = mutableStateListOf<EntradaHistorial>().apply { addAll(historial.entradas()) }

    val memoria = Memoria(java.io.File(app.filesDir, "memoria.json"))
    /** Cambia cada vez que la memoria cambia, para que las sugerencias en pantalla se actualicen. */
    var versionMemoria by mutableIntStateOf(0)
        private set

    fun sugerencias(clave: String, escrito: String): List<String> {
        versionMemoria // leerla hace que la pantalla se redibuje cuando cambia
        return memoria.sugerencias(clave, escrito)
    }

    fun olvidar(clave: String, texto: String) {
        memoria.olvidar(clave, texto)
        versionMemoria++
    }

    fun recordarNota(p: Plantilla, d: Datos) {
        memoria.recordarNota(p, d)
        versionMemoria++
    }

    fun borrarMemoria() {
        memoria.borrarTodo()
        versionMemoria++
    }

    fun cambiarModo(m: Modo) {
        modo = m
        ajustes.modo = m
    }

    fun cambiarPapel(mm: Int) {
        papelMm = mm
        ajustes.papelMm = mm
    }

    fun plantilla(id: String): Plantilla? = plantillas.firstOrNull { it.id == id }

    fun guardarPlantilla(p: Plantilla) {
        val i = plantillas.indexOfFirst { it.id == p.id }
        if (i >= 0) plantillas[i] = p else plantillas += p
        almacen.guardar(plantillas.toList())
    }

    fun nuevaPlantilla(): Plantilla {
        val p = PlantillasPorDefecto.notaDeEntrega().copy(nombre = "Nueva plantilla ${plantillas.size + 1}")
        guardarPlantilla(p)
        return p
    }

    fun duplicar(p: Plantilla) {
        // Nuevos ids para que lo llenado en una no aparezca en la otra.
        guardarPlantilla(p.copy(id = nuevoId(), nombre = "${p.nombre} (copia)", elementos = p.elementos.map(::conIdNuevo)))
    }

    fun eliminar(p: Plantilla) {
        if (plantillas.size <= 1) return
        plantillas.removeAll { it.id == p.id }
        // Borrar solo las imágenes que ninguna otra plantilla usa (las copias comparten archivo).
        val enUso = plantillas.flatMap { it.elementos.filterIsInstance<Elemento.Imagen>().map { i -> i.archivo } }.toSet()
        p.elementos.filterIsInstance<Elemento.Imagen>()
            .filter { it.archivo !in enUso }
            .forEach { Imagenes.borrar(getApplication(), it.archivo) }
        almacen.guardar(plantillas.toList())
        if (plantillaNota == p.id) plantillaNota = plantillas.first().id
    }

    fun guardarImagen(uri: Uri): String? = Imagenes.guardar(getApplication(), uri)

    fun datos() = Datos(campos.toMap(), filas.toMap())

    fun limpiarNota() {
        campos.clear()
        filas.clear()
    }

    fun refrescarHistorial() {
        entradas.clear()
        entradas.addAll(historial.entradas())
    }

    private fun conIdNuevo(e: Elemento): Elemento = when (e) {
        is Elemento.Texto -> e.copy(id = nuevoId())
        is Elemento.Campo -> e.copy(id = nuevoId())
        is Elemento.Tabla -> e.copy(id = nuevoId())
        is Elemento.Separador -> e.copy(id = nuevoId())
        is Elemento.Espacio -> e.copy(id = nuevoId())
        is Elemento.Imagen -> e.copy(id = nuevoId())
        is Elemento.Qr -> e.copy(id = nuevoId())
        is Elemento.Barras -> e.copy(id = nuevoId())
        is Elemento.Firma -> e.copy(id = nuevoId())
    }
}
