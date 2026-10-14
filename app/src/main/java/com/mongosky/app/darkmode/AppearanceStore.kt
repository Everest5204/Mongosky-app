package com.mongosky.app.darkmode

import android.content.Context

/** A single tiny built-in preference; no database, network or new dependency. */
internal class AppearanceStore(context: Context) : AppearancePreferences {
    private val preferences = context.applicationContext.getSharedPreferences("mongosky_appearance",Context.MODE_PRIVATE)
    override fun read(): AppearanceMode = try { AppearanceMode.parse(preferences.getString("mode",null)) }
        catch (_: ClassCastException) { AppearanceMode.LIGHT }
    override fun write(mode: AppearanceMode) { preferences.edit().putString("mode",mode.stored).apply() }
}
