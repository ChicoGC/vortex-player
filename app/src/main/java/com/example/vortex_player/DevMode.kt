package com.example.vortex_player

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Modo desenvolvedor escondido. Liga com um gesto secreto (tocar 7 vezes rápido no logo da
 * Biblioteca), sem nenhuma dica na tela até completar. Desliga em Ajustes.
 */
class DevMode(private val prefs: SharedPreferences) {

    var enabled by mutableStateOf(prefs.getBoolean(KEY, false))
        private set

    private var taps = 0
    private var firstTapAt = 0L

    /** Conta um toque no logo. Devolve true no toque que liga o modo. */
    fun onSecretTap(): Boolean {
        if (enabled) return false
        val now = SystemClock.uptimeMillis()
        if (now - firstTapAt > WINDOW_MS) {
            taps = 0
            firstTapAt = now
        }
        taps++
        if (taps < TAPS_NEEDED) return false
        taps = 0
        set(true)
        return true
    }

    fun set(value: Boolean) {
        prefs.edit { putBoolean(KEY, value) }
        enabled = value
    }

    private companion object {
        const val KEY = "dev_mode"
        const val TAPS_NEEDED = 7
        const val WINDOW_MS = 3000L
    }
}
