package com.cuadrecaja.app.stock

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class StockViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = AlmacenStock(app)
    private val prefs = app.getSharedPreferences("stock", android.content.Context.MODE_PRIVATE)

    /** Cómo se ve la secuencia: brillo elegido a mano (o el del celular) y fondo negro. */
    var brilloManual by mutableStateOf(prefs.getBoolean("brilloManual", false))
        private set
    var brillo by mutableFloatStateOf(prefs.getFloat("brillo", 0.6f))
        private set
    var fondoNegro by mutableStateOf(prefs.getBoolean("fondoNegro", false))
        private set

    fun cambiarPantalla(manual: Boolean, nivel: Float, negro: Boolean) {
        brilloManual = manual
        brillo = nivel.coerceIn(0.02f, 1f)
        fondoNegro = negro
        prefs.edit().putBoolean("brilloManual", manual).putFloat("brillo", brillo).putBoolean("fondoNegro", negro).apply()
    }
    val listas = mutableStateListOf<ListaStock>().apply { addAll(almacen.cargar()) }

    fun lista(id: String): ListaStock? = listas.firstOrNull { it.id == id }

    fun nueva(): ListaStock {
        val l = ListaStock(nombre = "Lista ${listas.size + 1}")
        guardar(l)
        return l
    }

    fun guardar(l: ListaStock) {
        val i = listas.indexOfFirst { it.id == l.id }
        if (i >= 0) listas[i] = l else listas += l
        almacen.guardar(listas.toList())
    }

    fun eliminar(id: String) {
        listas.removeAll { it.id == id }
        almacen.guardar(listas.toList())
    }
}
