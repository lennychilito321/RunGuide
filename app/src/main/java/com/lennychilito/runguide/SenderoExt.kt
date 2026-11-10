package com.lennychilito.runguide

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import com.google.firebase.database.DataSnapshot
import java.text.Normalizer

/** Convierte un nodo de /senderos en un [Sendero]; null si no tiene nombre. */
fun DataSnapshot.toSendero(): Sendero? {
    val nombre = child("nombre").value?.toString().orEmpty()
    if (nombre.isEmpty()) return null
    return Sendero(
        id = key.orEmpty(),
        nombre = nombre,
        ubicacion = child("ubicacion").value?.toString().orEmpty(),
        distancia = child("distancia").value?.toString().orEmpty(),
        dificultad = child("dificultad").value?.toString().orEmpty(),
        rating = child("rating").value?.toString()?.toDoubleOrNull() ?: 0.0,
        imagenUrl = child("imagenUrl").value?.toString().orEmpty(),
        imagenBase64 = child("imagenBase64").value?.toString().orEmpty(),
        descripcion = child("descripcion").value?.toString().orEmpty(),
        elevacion = child("elevacion").value?.toString() ?: "0m",
        tipoTerreno = child("tipoTerreno").value?.toString().orEmpty(),
        tiempoEstimado = child("tiempoEstimado").value?.toString() ?: "0"
    )
}

/** Abre el detalle con las mismas claves que lee DetalleSenderoActivity. */
fun Context.abrirDetalle(s: Sendero) {
    startActivity(Intent(this, DetalleSenderoActivity::class.java).apply {
        putExtra("SENDERO_ID", s.id)
        putExtra("NOMBRE", s.nombre)
        putExtra("DISTANCIA", s.distancia)
        putExtra("DIFICULTAD", s.dificultad)
        putExtra("RATING", s.rating.toString())
        putExtra("IMAGEN_URL", s.imagenUrl)
        putExtra("IMAGEN_BASE64", s.imagenBase64)
        putExtra("DESCRIPCION", s.descripcion)
        putExtra("ELEVACION", s.elevacion)
        putExtra("TIPO_TERRENO", s.tipoTerreno.ifEmpty { s.ubicacion })
        putExtra("TIEMPO_ESTIMADO", s.tiempoEstimado)
    })
}

/** "Moderada" y "Moderado" (o con tilde) dan la misma clave para poder filtrar. */
fun String.claveDificultad(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .trim()
        .take(4)

/** Colores (fondo, texto) del distintivo de dificultad. */
fun colorDificultad(dificultad: String): Pair<Int, Int> = when (dificultad.claveDificultad()) {
    "faci" -> Color.parseColor("#DDF3E4") to Color.parseColor("#0A6B37")
    "mode" -> Color.parseColor("#FFF3CD") to Color.parseColor("#7A5B00")
    "difi" -> Color.parseColor("#FFE0DE") to Color.parseColor("#A12622")
    else -> Color.parseColor("#ECEFF1") to Color.parseColor("#455A64")
}

/** Reduce un poco la vista mientras se pulsa; no consume el toque. */
@SuppressLint("ClickableViewAccessibility")
fun View.efectoPresion() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
        false
    }
}
