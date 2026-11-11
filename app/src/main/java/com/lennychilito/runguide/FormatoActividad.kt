package com.lennychilito.runguide

import android.content.Context
import com.google.firebase.database.DataSnapshot
import org.osmdroid.util.GeoPoint
import java.util.Locale

/** Reloj de la carrera: «04:12» o «1:03:15». */
fun formatoReloj(segundos: Long): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

/** Duración para el resumen: «26m 35s» o «1h 03m». */
fun formatoDuracion(segundos: Long): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return if (h > 0) String.format(Locale.getDefault(), "%dh %02dm", h, m)
    else String.format(Locale.getDefault(), "%dm %02ds", m, s)
}

/**
 * Ritmo promedio en min/km («5:18»). Muestra «—» con menos de 50 m recorridos o si sale
 * más lento que 60 min/km: son casos en que el número no significa nada (por ejemplo, quieto).
 */
fun formatoRitmo(segundos: Long, km: Double): String {
    if (km < 0.05 || segundos <= 0) return "—"
    val segPorKm = (segundos / km).toLong()
    if (segPorKm > 60 * 60) return "—"
    return formatoSegPorKm(segPorKm)
}

/** Segundos por km como «5:18»; «—» si no hay dato. */
fun formatoSegPorKm(segPorKm: Long?): String =
    if (segPorKm == null) "—"
    else String.format(Locale.getDefault(), "%d:%02d", segPorKm / 60, segPorKm % 60)

fun formatoDistancia(km: Double): String = String.format(Locale.getDefault(), "%.2f", km)

/** Nombre de la actividad en el idioma actual (la carrera libre se guarda en español). */
fun Context.nombreActividad(nombre: String, tipo: String): String =
    if (tipo == "libre") getString(R.string.carrera_libre) else nombre

/** Reduce el recorrido a como mucho [max] puntos para guardarlo sin ocupar demasiado. */
fun reducirRuta(puntos: List<GeoPoint>, max: Int = 400): List<GeoPoint> {
    if (puntos.size <= max) return puntos
    val paso = puntos.size.toDouble() / (max - 1)
    val reducidos = (0 until max - 1).map { puntos[(it * paso).toInt()] }
    return reducidos + puntos.last()
}

/** Recorrido como [lat0, lng0, lat1, lng1, …] para pasarlo entre pantallas. */
fun List<GeoPoint>.aArreglo(): DoubleArray =
    DoubleArray(size * 2) { i -> if (i % 2 == 0) this[i / 2].latitude else this[i / 2].longitude }

fun DoubleArray.aPuntos(): List<GeoPoint> =
    (0 until size / 2).map { GeoPoint(this[it * 2], this[it * 2 + 1]) }

/** Lee el recorrido guardado en Firebase (lista de {lat, lng}). */
fun DataSnapshot.rutaGuardada(): List<GeoPoint> =
    child("ruta").children.mapNotNull { p ->
        val lat = p.child("lat").value?.toString()?.toDoubleOrNull()
        val lng = p.child("lng").value?.toString()?.toDoubleOrNull()
        if (lat != null && lng != null) GeoPoint(lat, lng) else null
    }
