package com.lennychilito.runguide

import android.location.Location
import android.os.SystemClock
import org.osmdroid.util.GeoPoint

/**
 * Estado de la actividad en curso (carrera libre o ruta de un sendero).
 * Vive en memoria mientras la app esté abierta; el servicio en primer plano
 * (RecorridoService) lo alimenta con el GPS aunque la pantalla esté bloqueada,
 * y la pantalla de carrera solo lo muestra.
 */
object Recorrido {

    /** Lo que se guarda al terminar. */
    class Resultado(
        val nombre: String,
        val esLibre: Boolean,
        val senderoId: String,
        val segundos: Long,
        val km: Double,
        val fecha: Long,
        val puntos: List<GeoPoint>
    )

    var activo = false
        private set
    var pausado = false
        private set
    var conGps = false            // false si el usuario negó la ubicación: solo cuenta el tiempo
        private set

    var nombre = ""
        private set
    var esLibre = true
        private set
    var senderoId = ""
        private set
    var metaKm: Double? = null
        private set
    var estimadoMin: Int? = null
        private set
    var inicioSendero: GeoPoint? = null
        private set

    var km = 0.0
        private set
    val puntos = mutableListOf<GeoPoint>()

    private var fechaInicio = 0L
    private var acumuladoMs = 0L
    private var tramoDesde = 0L
    private var ultima: Location? = null

    private val oyentes = mutableSetOf<() -> Unit>()

    fun suscribir(f: () -> Unit) { oyentes.add(f) }
    fun desuscribir(f: () -> Unit) { oyentes.remove(f) }
    private fun avisar() = oyentes.toList().forEach { it() }

    /** Segundos en movimiento (no cuenta las pausas). */
    fun segundos(): Long {
        val enCurso = if (activo && !pausado) SystemClock.elapsedRealtime() - tramoDesde else 0L
        return (acumuladoMs + enCurso) / 1000
    }

    fun iniciar(
        nombre: String, esLibre: Boolean, senderoId: String,
        metaKm: Double?, estimadoMin: Int?, inicioSendero: GeoPoint?, conGps: Boolean
    ) {
        this.nombre = nombre
        this.esLibre = esLibre
        this.senderoId = senderoId
        this.metaKm = metaKm
        this.estimadoMin = estimadoMin
        this.inicioSendero = inicioSendero
        this.conGps = conGps
        km = 0.0
        puntos.clear()
        ultima = null
        acumuladoMs = 0L
        tramoDesde = SystemClock.elapsedRealtime()
        fechaInicio = System.currentTimeMillis()
        pausado = false
        activo = true
        avisar()
    }

    fun pausar() {
        if (!activo || pausado) return
        acumuladoMs += SystemClock.elapsedRealtime() - tramoDesde
        pausado = true
        ultima = null          // lo que te muevas en pausa no suma distancia
        avisar()
    }

    fun reanudar() {
        if (!activo || !pausado) return
        tramoDesde = SystemClock.elapsedRealtime()
        pausado = false
        avisar()
    }

    /** Nueva lectura del GPS: suma distancia y agrega el punto al recorrido. */
    fun nuevaUbicacion(l: Location) {
        if (!activo || pausado) return
        if (l.hasAccuracy() && l.accuracy > 30f) return     // lectura poco precisa: se ignora
        val punto = GeoPoint(l.latitude, l.longitude)
        val anterior = ultima
        if (anterior == null) {
            puntos.add(punto)
        } else {
            val metros = anterior.distanceTo(l)
            if (metros < 3f) return                           // ruido del GPS estando quieto
            km += metros / 1000.0
            puntos.add(punto)
        }
        ultima = l
        avisar()
    }

    /** Termina la actividad y devuelve sus datos. */
    fun terminar(): Resultado {
        if (activo && !pausado) acumuladoMs += SystemClock.elapsedRealtime() - tramoDesde
        val r = Resultado(nombre, esLibre, senderoId, acumuladoMs / 1000, km, fechaInicio, puntos.toList())
        activo = false
        pausado = false
        avisar()
        return r
    }
}
