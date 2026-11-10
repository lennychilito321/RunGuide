package com.lennychilito.runguide

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Preferencias del usuario: tema (claro/oscuro/automático) e idioma (es/en). */
object Ajustes {
    private const val PREFS = "ajustes"
    private const val K_TEMA = "tema"
    private const val K_IDIOMA = "idioma"

    const val TEMA_CLARO = "claro"
    const val TEMA_OSCURO = "oscuro"
    const val TEMA_AUTO = "auto"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tema(ctx: Context): String = prefs(ctx).getString(K_TEMA, TEMA_AUTO) ?: TEMA_AUTO

    /** "" = idioma del sistema; "es" o "en" si el usuario eligió uno. */
    fun idioma(ctx: Context): String = prefs(ctx).getString(K_IDIOMA, "") ?: ""

    fun guardarTema(ctx: Context, tema: String) {
        prefs(ctx).edit().putString(K_TEMA, tema).apply()
        aplicarTema(tema)
    }

    fun guardarIdioma(ctx: Context, codigo: String) {
        prefs(ctx).edit().putString(K_IDIOMA, codigo).apply()
        aplicarIdioma(codigo)
    }

    /** Se llama al abrir la app para restaurar lo elegido. */
    fun aplicarGuardado(ctx: Context) {
        aplicarTema(tema(ctx))
        val idioma = idioma(ctx)
        if (idioma.isNotEmpty()) aplicarIdioma(idioma)
    }

    private fun aplicarTema(tema: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (tema) {
                TEMA_CLARO -> AppCompatDelegate.MODE_NIGHT_NO
                TEMA_OSCURO -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun aplicarIdioma(codigo: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(codigo))
    }
}

class RunGuideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ajustes.aplicarGuardado(this)
        configurarOsmdroid()
    }
}
