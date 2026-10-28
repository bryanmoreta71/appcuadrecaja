package com.cuadrecaja.app.stock

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel

class StockViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = AlmacenStock(app)
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
