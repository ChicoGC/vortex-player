package com.example.vortex_player

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.roundToInt

class Appearance(private val prefs: SharedPreferences) {

    var themeId: String
        get() = prefs.getString(KEY_THEME, null) ?: Themes.DEFAULT.id
        set(value) = prefs.edit { putString(KEY_THEME, value) }

    var coverBackground: Boolean
        get() = prefs.getBoolean(KEY_COVER, true)
        set(value) = prefs.edit { putBoolean(KEY_COVER, value) }

    var glass: Float
        get() = prefs.getInt(KEY_GLASS, 50) / 100f
        set(value) = prefs.edit { putInt(KEY_GLASS, (value * 100).roundToInt()) }

    var animated: Boolean
        get() = prefs.getBoolean(KEY_ANIMATED, true)
        set(value) = prefs.edit { putBoolean(KEY_ANIMATED, value) }

    val theme: AppTheme get() = Themes.byId(themeId)

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_COVER = "cover_background"
        const val KEY_GLASS = "glass"
        const val KEY_ANIMATED = "animated"
    }
}
