package com.lennychilito.runguide

import com.google.firebase.database.DataSnapshot

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

    /** Los tres senderos de prueba que existían antes: se borran para que solo queden los predeterminados. */
    // Sin tildes ni mayúsculas, para que coincidan aunque se hayan escrito distinto
    private val NOMBRES_DE_PRUEBA = setOf("sendero el morro", "cerro nimbe", "ruta rio molino")

    private val ids get() = lista.map { it.id }.toSet()

    /**
     * Deja el catálogo como debe quedar (solo el administrador puede escribir):
     *  1) agrega los predeterminados que falten, sin tocar los que ya están;
     *  2) borra los tres senderos de prueba antiguos, con sus reseñas y calificaciones.
     * Se llama con la lista actual de senderos, así que es seguro repetirlo.
     */
    fun asegurar(senderos: DataSnapshot, onError: () -> Unit = {}) {
        val existentes = senderos.children.mapNotNull { it.key }.toSet()

        val faltan = lista.filter { it.id !in existentes }
        if (faltan.isNotEmpty()) escribir(faltan) { ok -> if (!ok) onError() }

        for (s in senderos.children) {
            val id = s.key ?: continue
            val nombre = s.child("nombre").value?.toString().orEmpty().sinTildes()
            if (id !in ids && nombre in NOMBRES_DE_PRUEBA) {
                Rtdb.eliminarSendero(id) { ok -> if (!ok) onError() }
            }
        }
    }

    /**
     * Escribe todos los senderos predeterminados en Firebase, restaurando sus datos originales.
     * Se escribe campo por campo para no borrar la calificación que ya tengan.
     */
    fun cargar(onListo: (Boolean) -> Unit = {}) = escribir(lista, onListo)

    private fun escribir(defs: List<Def>, onListo: (Boolean) -> Unit) {
        val cambios = HashMap<String, Any?>()
        for (d in defs) {
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
