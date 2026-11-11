package com.lennychilito.runguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.lennychilito.runguide.databinding.ActivityCronometroBinding
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Pantalla de la actividad en curso (carrera libre o ruta de un sendero).
 * Solo muestra los datos: quien registra el GPS es RecorridoService, que sigue
 * funcionando con la pantalla bloqueada. Si sales de esta pantalla la actividad
 * continúa y se vuelve a ella desde la notificación.
 *
 * Extras para empezar: LIBRE=true (carrera libre) o los datos del sendero
 * (NOMBRE, DISTANCIA, TIEMPO_ESTIMADO, LAT, LNG, SENDERO_ID).
 */
class CronometroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCronometroBinding

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refrescar()
            handler.postDelayed(this, 1000)
        }
    }
    private val oyente: () -> Unit = { refrescar() }

    // ── Mapa en vivo ────────────────────────────────────────────────────────────
    private val trazo = Polyline()
    private var marcadorYo: Marker? = null
    private var marcadorInicio: Marker? = null
    private var puntosDibujados = 0
    private var siguiendo = true
    private var verDatos = false

    private val pedirPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val ubicacion = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        empezar(conGps = ubicacion)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCronometroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarMapa()

        if (Recorrido.activo) {
            // Ya hay una actividad: se vuelve a ella (p. ej. desde la notificación)
            if (pideOtraActividad()) {
                Toast.makeText(this, getString(R.string.ya_hay_carrera), Toast.LENGTH_LONG).show()
            }
        } else {
            pedirPermisosYEmpezar()
        }

        binding.btnPausar.setOnClickListener { pausarOReanudar() }
        binding.btnFinalizar.setOnClickListener { confirmarFinalizar() }
        binding.btnCentrar.setOnClickListener {
            siguiendo = true
            Recorrido.puntos.lastOrNull()?.let { binding.mapaRuta.controller.animateTo(it) }
        }
        binding.btnVistaDatos.setOnClickListener { alternarVista() }

        // Salir de la pantalla no detiene la actividad: sigue en segundo plano
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (Recorrido.activo) {
                    Toast.makeText(this@CronometroActivity, getString(R.string.carrera_sigue), Toast.LENGTH_LONG).show()
                }
                finish()
            }
        })
    }

    // ─── Inicio ──────────────────────────────────────────────────────────────────
    private val esLibre get() = intent.getBooleanExtra("LIBRE", false)

    private fun nombrePedido(): String =
        if (esLibre) getString(R.string.carrera_libre)
        else intent.getStringExtra("NOMBRE") ?: getString(R.string.sendero_default)

    /** true si se abrió para empezar algo distinto de lo que ya está en curso. */
    private fun pideOtraActividad(): Boolean =
        (esLibre || intent.hasExtra("NOMBRE")) && nombrePedido() != Recorrido.nombre

    private fun tienePermiso(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun pedirPermisosYEmpezar() {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 13+: permiso para mostrar la notificación de la actividad en curso
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permisos.all { tienePermiso(it) }) empezar(conGps = true)
        else pedirPermisos.launch(permisos.toTypedArray())
    }

    private fun empezar(conGps: Boolean) {
        val lat = intent.getDoubleExtra("LAT", Double.NaN)
        val lng = intent.getDoubleExtra("LNG", Double.NaN)
        val inicio = if (!esLibre && !lat.isNaN() && !lng.isNaN()) GeoPoint(lat, lng) else null

        Recorrido.iniciar(
            nombre = nombrePedido(),
            esLibre = esLibre,
            senderoId = if (esLibre) "" else intent.getStringExtra("SENDERO_ID").orEmpty(),
            metaKm = if (esLibre) null
            else intent.getStringExtra("DISTANCIA")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 },
            estimadoMin = if (esLibre) null
            else intent.getStringExtra("TIEMPO_ESTIMADO")?.toIntOrNull()?.takeIf { it > 0 },
            inicioSendero = inicio,
            conGps = conGps
        )

        if (conGps) {
            RecorridoService.iniciar(this)
            centrarEnMiUbicacion()
        } else {
            Toast.makeText(this, getString(R.string.gps_denegado), Toast.LENGTH_LONG).show()
        }
        mostrarInicioSendero()
        refrescar()
    }

    // ─── Botones ─────────────────────────────────────────────────────────────────
    private fun pausarOReanudar() {
        if (!Recorrido.activo) return
        if (Recorrido.pausado) {
            if (Recorrido.conGps) RecorridoService.reanudar(this) else Recorrido.reanudar()
        } else {
            if (Recorrido.conGps) RecorridoService.pausar(this) else Recorrido.pausar()
        }
    }

    private fun confirmarFinalizar() {
        if (!Recorrido.activo) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dlg_finalizar_t))
            .setMessage(getString(R.string.dlg_finalizar_m))
            .setPositiveButton(getString(R.string.guardar)) { _, _ -> terminar(guardar = true) }
            .setNegativeButton(getString(R.string.descartar)) { _, _ -> terminar(guardar = false) }
            .setNeutralButton(getString(R.string.seguir), null)
            .show()
    }

    private fun terminar(guardar: Boolean) {
        val conGps = Recorrido.conGps
        val r = Recorrido.terminar()
        if (conGps) RecorridoService.detener(this)

        if (guardar) {
            val ruta = reducirRuta(r.puntos)
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                guardarEnFirebase(uid, r, ruta)
                Toast.makeText(this, getString(R.string.actividad_guardada), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.no_guardada_invitado), Toast.LENGTH_LONG).show()
            }
            // Resumen final para compartir (como el de Strava, con el logo de RunGuide)
            startActivity(
                Intent(this, ResumenActividadActivity::class.java)
                    .putExtra("NOMBRE", r.nombre)
                    .putExtra("SEGUNDOS", r.segundos)
                    .putExtra("KM", r.km)
                    .putExtra("FECHA", r.fecha)
                    .putExtra("RUTA", ruta.aArreglo())
            )
        }
        finish()
    }

    private fun guardarEnFirebase(uid: String, r: Recorrido.Resultado, ruta: List<GeoPoint>) {
        Rtdb.ref.child("usuarios/$uid/actividades").push().setValue(
            mapOf(
                // La carrera libre se guarda en español y se traduce al mostrarla
                "sendero" to (if (r.esLibre) "Carrera libre" else r.nombre),
                "tipo" to (if (r.esLibre) "libre" else "sendero"),
                "senderoId" to r.senderoId,
                "tiempoSegundos" to r.segundos,
                "kmRecorridos" to r.km,
                "fecha" to r.fecha,
                "ruta" to ruta.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }
            )
        )
    }

    private fun alternarVista() {
        verDatos = !verDatos
        binding.vistaDatos.visibility = if (verDatos) View.VISIBLE else View.GONE
        binding.filaMetricas.visibility = if (verDatos) View.GONE else View.VISIBLE
        binding.btnCentrar.visibility = if (verDatos) View.INVISIBLE else View.VISIBLE
        binding.btnVistaDatos.text = getString(if (verDatos) R.string.ver_mapa else R.string.ver_datos)
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

        mapa.controller.setZoom(17.0)
        mapa.controller.setCenter(Recorrido.puntos.lastOrNull() ?: Recorrido.inicioSendero ?: CENTRO_POR_DEFECTO)
        if (Recorrido.activo) mostrarInicioSendero()

        // Si el usuario mueve el mapa con el dedo, deja de seguirlo hasta que pulse 📍
        mapa.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_MOVE) siguiendo = false
            false
        }
    }

    private fun mostrarInicioSendero() {
        val inicio = Recorrido.inicioSendero ?: return
        if (marcadorInicio != null) return
        val mapa = binding.mapaRuta
        marcadorInicio = mapa.marcador(inicio, getString(R.string.punto_inicio)).also { mapa.overlays.add(it) }
        if (Recorrido.puntos.isEmpty()) mapa.controller.setCenter(inicio)
        mapa.invalidate()
    }

    /** Muestra tu posición apenas abre, sin esperar a que empiece el recorrido. */
    @SuppressLint("MissingPermission")
    private fun centrarEnMiUbicacion() {
        if (!tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !tienePermiso(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { l ->
            if (l != null && Recorrido.puntos.isEmpty()) moverMiPosicion(GeoPoint(l.latitude, l.longitude))
        }
    }

    private fun moverMiPosicion(punto: GeoPoint) {
        val mapa = binding.mapaRuta
        val m = marcadorYo ?: mapa.marcadorPosicion(this, punto).also {
            marcadorYo = it
            mapa.overlays.add(it)
        }
        m.position = punto
        if (siguiendo) mapa.controller.animateTo(punto)
        mapa.invalidate()
    }

    // ─── Pantalla ────────────────────────────────────────────────────────────────
    private fun refrescar() {
        val seg = Recorrido.segundos()
        val km = Recorrido.km
        val reloj = formatoReloj(seg)
        val ritmo = formatoRitmo(seg, km)
        val distancia = formatoDistancia(km)

        binding.tvNombreSendero.text = Recorrido.nombre.ifEmpty { nombrePedido() }
        binding.tvCronometro.text = reloj
        binding.tvGrandeTiempo.text = reloj
        binding.tvRitmo.text = ritmo
        binding.tvGrandeRitmo.text = ritmo
        binding.tvKm.text = distancia
        binding.tvGrandeKm.text = distancia

        binding.tvEstado.text = getString(if (Recorrido.pausado) R.string.estado_pausado else R.string.corriendo)
        binding.btnPausar.text = getString(if (Recorrido.pausado) R.string.btn_reanudar else R.string.pausar)

        // Avance respecto al sendero
        val meta = Recorrido.metaKm
        if (meta == null) {
            binding.progresoDistancia.visibility = View.GONE
            binding.tvProgreso.visibility = View.GONE
        } else {
            binding.progresoDistancia.visibility = View.VISIBLE
            binding.tvProgreso.visibility = View.VISIBLE
            binding.progresoDistancia.setProgressCompat(((km / meta) * 100).toInt().coerceIn(0, 100), true)
            binding.tvProgreso.text = getString(R.string.progreso_ruta, distancia, formatoDistancia(meta))
        }
        val estimado = Recorrido.estimadoMin
        binding.tvTiempoEstimado.visibility = if (estimado == null) View.GONE else View.VISIBLE
        if (estimado != null) binding.tvTiempoEstimado.text = getString(R.string.tiempo_estimado_ruta, estimado)

        // Dibuja solo los puntos nuevos del recorrido
        val puntos = Recorrido.puntos
        if (puntos.size < puntosDibujados) {
            trazo.setPoints(emptyList())
            puntosDibujados = 0
        }
        if (puntos.size > puntosDibujados) {
            for (i in puntosDibujados until puntos.size) trazo.addPoint(puntos[i])
            puntosDibujados = puntos.size
            moverMiPosicion(puntos.last())
        }
    }

    // ─── Ciclo de vida ───────────────────────────────────────────────────────────
    override fun onStart() {
        super.onStart()
        Recorrido.suscribir(oyente)
        handler.post(tick)
    }

    override fun onStop() {
        Recorrido.desuscribir(oyente)
        handler.removeCallbacks(tick)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.mapaRuta.onResume()
    }

    override fun onPause() {
        binding.mapaRuta.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.mapaRuta.onDetach()
        super.onDestroy()
    }
}
