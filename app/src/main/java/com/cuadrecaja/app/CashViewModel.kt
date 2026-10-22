package com.cuadrecaja.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel

/** Guarda lo contado; se conserva aunque se cierre la app hasta que se empiece de nuevo. */
class CashViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("conteo", Context.MODE_PRIVATE)

    val counts = mutableStateMapOf<String, Int>().apply {
        prefs.all.forEach { (key, value) -> if (value is Int && value > 0) put(key, value) }
    }

    fun setCount(denomination: Denomination, qty: Int) {
        val value = qty.coerceAtLeast(0)
        if (value == 0) counts.remove(denomination.id) else counts[denomination.id] = value
        prefs.edit().apply { if (value == 0) remove(denomination.id) else putInt(denomination.id, value) }.apply()
    }

    fun reset() {
        counts.clear()
        prefs.edit().clear().apply()
    }
}
