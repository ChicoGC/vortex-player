package com.example.vortex_player

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.example.vortex_player.ui.theme.BackgroundStyle
import com.example.vortex_player.ui.theme.VortexLook
import com.example.vortex_player.ui.theme.themeById
import kotlin.math.roundToInt

/** Theme, background and glass intensity, saved in preferences and observable by the UI. */
class Appearance(private val prefs: SharedPreferences) {

    var look by mutableStateOf(read())
        private set

    fun setTheme(id: String) = save { putString(KEY_THEME, id) }

    fun setBackground(style: BackgroundStyle) = save { putString(KEY_BACKGROUND, style.name) }

    fun setGlass(value: Float) = save { putInt(KEY_GLASS, (value * 100).roundToInt()) }

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        look = read()
    }

    private fun read(): VortexLook {
        val background = prefs.getString(KEY_BACKGROUND, null)
            ?.let { name -> BackgroundStyle.entries.firstOrNull { it.name == name } }
            // Before this setting existed there was only "cover or gradient"
            ?: if (prefs.getBoolean(KEY_OLD_COVER, true)) BackgroundStyle.Cover else BackgroundStyle.Gradient
        return VortexLook(
            theme = themeById(prefs.getString(KEY_THEME, null)),
            background = background,
            glass = prefs.getInt(KEY_GLASS, 40) / 100f,
        )
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_BACKGROUND = "background"
        const val KEY_OLD_COVER = "cover_background"
        const val KEY_GLASS = "glass"
    }
}
