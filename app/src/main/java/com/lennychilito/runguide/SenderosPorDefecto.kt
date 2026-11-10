package com.lennychilito.runguide

/**
 * Senderos de Popayán que vienen con el sistema. Tienen id fijo, así que cargarlos
 * varias veces no los duplica: solo restaura sus datos originales.
 *
 * Las ubicaciones son las que dio el equipo (El Morro y La Torre en coordenadas;
 * San Bernardino viene del código Plus «GC42+79» tomando Popayán como referencia).
 * Distancia, tiempo y elevación son aproximados y se ajustan desde «Editar sendero».
 */
object SenderosPorDefecto {

    private class Def(
        val id: String,
        val nombre: String,
        val distancia: String,
        val dificultad: String,   // siempre en español: así se guarda en la base de datos
        val descripcion: String,
        val elevacion: String,
        val tipoTerreno: String,
        val tiempo: String,
        val lat: Double,
        val lng: Double,
        val imagenLocal: String
    )

    private val lista = listOf(
        Def(
            id = "default_morro", nombre = "El Morro de Tulcán", distancia = "1.4",
            dificultad = "Fácil",
            descripcion = "Caminata corta y apta para principiantes hasta el Morro de Tulcán, con el monumento ecuestre y vista de la ciudad.",
            elevacion = "80m", tipoTerreno = "Mixto", tiempo = "25",
            lat = 2.444770890709295, lng = -76.60008478449882, imagenLocal = "morro"
        ),
        Def(
            id = "default_torre", nombre = "La Torre", distancia = "3.0",
            dificultad = "Moderada",
            descripcion = "Ruta de dificultad media con subida sostenida y vista panorámica de Popayán.",
            elevacion = "150m", tipoTerreno = "Mixto", tiempo = "50",
            lat = 2.462950344065164, lng = -76.57565200265863, imagenLocal = "torre"
        ),
        Def(
            id = "default_san_bernardino", nombre = "Cascada San Bernardino", distancia = "3.5",
            dificultad = "Moderada",
            descripcion = "Camino de tierra entre vegetación hasta la cascada de San Bernardino. Lleva calzado con buen agarre.",
            elevacion = "120m", tipoTerreno = "Tierra", tiempo = "60",
            lat = 2.505688, lng = -76.599063, imagenLocal = "san_bernardino"
        )
    )

    /**
     * Escribe los senderos predeterminados en Firebase (solo el administrador puede).
     * Se escribe campo por campo para no borrar la calificación que ya tengan.
     */
    fun cargar(onListo: (Boolean) -> Unit = {}) {
        val cambios = HashMap<String, Any?>()
        for (d in lista) {
            val campos = mapOf(
                "nombre" to d.nombre,
                "ubicacion" to "Popayán",
                "distancia" to d.distancia,
                "dificultad" to d.dificultad,
                "descripcion" to d.descripcion,
                "elevacion" to d.elevacion,
                "tipoTerreno" to d.tipoTerreno,
                "tiempoEstimado" to d.tiempo,
                "lat" to d.lat,
                "lng" to d.lng,
                "imagenLocal" to d.imagenLocal,
                "imagenBase64" to "",
                "imagenUrl" to ""
            )
            for ((campo, valor) in campos) cambios["senderos/${d.id}/$campo"] = valor
        }
        Rtdb.ref.updateChildren(cambios).addOnCompleteListener { onListo(it.isSuccessful) }
    }
}
