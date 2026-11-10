package com.lennychilito.runguide

data class Sendero(
    val id: String = "",
    val nombre: String = "",
    val ubicacion: String = "",
    val distancia: String = "",
    val dificultad: String = "",
    val rating: Double = 0.0, // <-- Corregido: sin comillas para que sea un Double
    val imagenUrl: String = "",
    val imagenBase64: String = "",
    val imagenLocal: String = "",      // foto que viene dentro de la app (senderos predeterminados)
    val descripcion: String = "",
    val elevacion: String = "0m",
    val tipoTerreno: String = "",
    val tiempoEstimado: String = "0",  // en minutos
    val lat: Double? = null,           // ubicación en el mapa (opcional)
    val lng: Double? = null
) {
    val tieneUbicacion get() = lat != null && lng != null
}