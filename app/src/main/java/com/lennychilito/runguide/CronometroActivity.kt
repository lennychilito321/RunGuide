package com.lennychilito.runguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.lennychilito.runguide.databinding.ActivityCronometroBinding
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

/**
 * Ruta en curso: mapa en vivo con el recorrido que vas dejando, y un panel con
 * el tiempo, la distancia, el ritmo y el avance respecto a la distancia del sendero.
 */
class CronometroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCronometroBinding

    // ── Cronómetro ──────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var segundos = 0
    private var corriendo = false
    private var pausado = false

    // ── GPS ─────────────────────────────────────────────────────────────────────
    private lateinit var fusedClient: FusedLocationProviderClient
    private var ultimaUbicacion: Location? = null
    private var kmRecorridos = 0.0

    private val PERMISO_GPS = 100

    // ── Mapa en vivo ────────────────────────────────────────────────────────────
    private val trazo = Polyline()               // línea del recorrido
    private var marcadorYo: Marker? = null       // «tu posición»
    private var ultimoPunto: GeoPoint? = null
    private var siguiendo = true                 // el mapa te sigue hasta que lo muevas

    // ── Datos del sendero ────────────────────────────────────────────────────────
    private val nombreSendero get() = intent.getStringExtra("NOMBRE") ?: getString(R.string.sendero_default)
    private var metaKm: Double? = null           // distancia del sendero
    private var estimadoMin: Int? = null         // tiempo estimado del sendero

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCronometroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvNombreSendero.text = nombreSendero
        metaKm = intent.getStringExtra("DISTANCIA")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
        estimadoMin = intent.getStringExtra("TIEMPO_ESTIMADO")?.toIntOrNull()?.takeIf { it > 0 }

        configurarMapa()
        actualizarProgreso()

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        pedirPermisosGPS()

        // ── Botón Pausar / Reanudar ──────────────────────────────────────────────
        binding.btnPausar.setOnClickListener {
            if (!corriendo) return@setOnClickListener
            if (!pausado) {
                // PAUSAR
                pausado = true
                handler.removeCallbacks(tickRunnable)
                detenerGPS()
                ultimaUbicacion = null   // lo recorrido durante la pausa no suma distancia
                binding.btnPausar.text = getString(R.string.btn_reanudar)
                binding.tvEstado.text  = getString(R.string.estado_pausado)
            } else {
                // REANUDAR
                pausado = false
                handler.post(tickRunnable)
                iniciarGPS()
                binding.btnPausar.text = getString(R.string.pausar)
                binding.tvEstado.text  = getString(R.string.corriendo)
            }
        }

        // ── Botón Finalizar ──────────────────────────────────────────────────────
        binding.btnFinalizar.setOnClickListener {
            detenerTodo()
            guardarActividad()
            Toast.makeText(this, getString(R.string.actividad_guardada), Toast.LENGTH_SHORT).show()
            finish()
        }

        // ── Centrar el mapa en mi posición ───────────────────────────────────────
        binding.btnCentrar.setOnClickListener {
            siguiendo = true
            ultimoPunto?.let { binding.mapaRuta.controller.animateTo(it) }
        }
    }

    // ─── Mapa ────────────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun configurarMapa() {
        val mapa = binding.mapaRuta
        mapa.configurar(this)

        trazo.outlinePaint.color = getColor(R.color.verde_primario)
        trazo.outlinePaint.strokeWidth = 14f
        trazo.outlinePaint.strokeCap = Paint.Cap.ROUND
        mapa.overlays.add(trazo)

        // Inicio del sendero (si el admin le puso ubicación)
        val lat = intent.getDoubleExtra("LAT", Double.NaN)
        val lng = intent.getDoubleExtra("LNG", Double.NaN)
        val inicio = if (!lat.isNaN() && !lng.isNaN()) GeoPoint(lat, lng) else null
        inicio?.let { mapa.overlays.add(mapa.marcador(it, getString(R.string.punto_inicio))) }

        mapa.controller.setZoom(17.0)
        mapa.controller.setCenter(inicio ?: CENTRO_POR_DEFECTO)

        // Si el usuario mueve el mapa con el dedo, deja de seguirlo hasta que pulse 📍
        mapa.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_MOVE) siguiendo = false
            false
        }
    }

    /** Pone (o mueve) el punto de «tu posición» y, si corresponde, centra el mapa. */
    private fun mostrarMiPosicion(punto: GeoPoint) {
        val mapa = binding.mapaRuta
        val marcador = marcadorYo ?: mapa.marcadorPosicion(this, punto).also {
            marcadorYo = it
            mapa.overlays.add(it)
        }
        marcador.position = punto
        ultimoPunto = punto
        if (siguiendo) mapa.controller.animateTo(punto)
        mapa.invalidate()
    }

    // ─── Permisos GPS ────────────────────────────────────────────────────────────
    private fun pedirPermisosGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCronometro()
            iniciarGPS()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISO_GPS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISO_GPS &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCronometro()
            iniciarGPS()
        } else {
            // Sin GPS sigue con cronómetro, pero km = 0
            Toast.makeText(this, getString(R.string.gps_denegado), Toast.LENGTH_LONG).show()
            iniciarCronometro()
        }
    }

    // ─── Cronómetro ──────────────────────────────────────────────────────────────
    private val tickRunnable = object : Runnable {
        override fun run() {
            segundos++
            actualizarCronometro()
            handler.postDelayed(this, 1000)
        }
    }

    private fun iniciarCronometro() {
        corriendo = true
        pausado   = false
        binding.tvEstado.text = getString(R.string.corriendo)
        handler.post(tickRunnable)
    }

    private fun actualizarCronometro() {
        val min = segundos / 60
        val seg = segundos % 60
        binding.tvCronometro.text = String.format(Locale.getDefault(), "%02d:%02d", min, seg)
    }

    // ─── Distancia, ritmo y avance ───────────────────────────────────────────────
    private fun actualizarMetricas() {
        binding.tvKm.text = String.format(Locale.getDefault(), "%.2f", kmRecorridos)

        // Ritmo: min/km
        if (kmRecorridos > 0) {
            val ritmo = (segundos / 60.0) / kmRecorridos
            val ritmoMin = ritmo.toInt()
            val ritmoSeg = ((ritmo - ritmoMin) * 60).toInt()
            binding.tvRitmo.text = String.format(Locale.getDefault(), "%d:%02d", ritmoMin, ritmoSeg)
        }
        actualizarProgreso()
    }

    /** Barra y texto de avance respecto a la distancia del sendero, y tiempo estimado. */
    private fun actualizarProgreso() {
        val meta = metaKm
        if (meta == null) {
            binding.progresoDistancia.visibility = View.GONE
            binding.tvProgreso.visibility = View.GONE
        } else {
            val pct = ((kmRecorridos / meta) * 100).toInt().coerceIn(0, 100)
            binding.progresoDistancia.setProgressCompat(pct, true)
            binding.tvProgreso.text = getString(
                R.string.progreso_ruta,
                String.format(Locale.getDefault(), "%.2f", kmRecorridos),
                String.format(Locale.getDefault(), "%.2f", meta)
            )
        }
        binding.tvTiempoEstimado.text =
            estimadoMin?.let { getString(R.string.tiempo_estimado_ruta, it) } ?: ""
    }

    // ─── GPS ─────────────────────────────────────────────────────────────────────
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 3000L   // cada 3 segundos
    ).setMinUpdateDistanceMeters(5f).build()      // mínimo 5 metros de diferencia

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val nueva = result.lastLocation ?: return
            val punto = GeoPoint(nueva.latitude, nueva.longitude)
            val anterior = ultimaUbicacion

            if (anterior == null) {
                // Primera lectura (o la primera después de una pausa): arranca el trazo
                trazo.addPoint(punto)
            } else {
                val metros = anterior.distanceTo(nueva)
                if (metros > 2) {                            // filtrar ruido < 2 m
                    kmRecorridos += metros / 1000.0
                    trazo.addPoint(punto)
                    actualizarMetricas()
                }
            }
            ultimaUbicacion = nueva
            mostrarMiPosicion(punto)
        }
    }

    private fun iniciarGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        // Muestra tu posición en el mapa sin esperar a la primera lectura nueva
        fusedClient.lastLocation.addOnSuccessListener { l ->
            if (l != null && ultimoPunto == null) mostrarMiPosicion(GeoPoint(l.latitude, l.longitude))
        }
    }

    private fun detenerGPS() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun detenerTodo() {
        corriendo = false
        handler.removeCallbacks(tickRunnable)
        detenerGPS()
    }

    // ─── Guardar actividad en Firebase ───────────────────────────────────────────
    private fun guardarActividad() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val actividad = mapOf(
            "sendero"        to nombreSendero,
            "tiempoSegundos" to segundos,
            "kmRecorridos"   to kmRecorridos,
            "fecha"          to System.currentTimeMillis()
        )

        FirebaseDatabase
            .getInstance("https://runner-app-f6214-default-rtdb.firebaseio.com/")
            .getReference("usuarios/$uid/actividades")
            .push()
            .setValue(actividad)
    }

    // ─── Ciclo de vida del mapa ──────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        binding.mapaRuta.onResume()
    }

    override fun onPause() {
        binding.mapaRuta.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerTodo()
        binding.mapaRuta.onDetach()
    }
}
