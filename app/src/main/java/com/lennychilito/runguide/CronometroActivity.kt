package com.lennychilito.runguide

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.lennychilito.runguide.databinding.ActivityCronometroBinding
import java.util.Locale

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

    // ── Datos del sendero ────────────────────────────────────────────────────────
    private val nombreSendero get() = intent.getStringExtra("NOMBRE") ?: getString(R.string.sendero_default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCronometroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvNombreSendero.text = nombreSendero

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

    // ─── GPS ─────────────────────────────────────────────────────────────────────
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 3000L   // cada 3 segundos
    ).setMinUpdateDistanceMeters(5f).build()      // mínimo 5 metros de diferencia

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val nueva = result.lastLocation ?: return
            ultimaUbicacion?.let { anterior ->
                val metros = anterior.distanceTo(nueva)
                if (metros > 2) {                            // filtrar ruido < 2 m
                    kmRecorridos += metros / 1000.0
                    binding.tvKm.text = String.format(Locale.getDefault(), "%.2f", kmRecorridos)

                    // Ritmo: min/km
                    if (kmRecorridos > 0) {
                        val minTotales = segundos / 60.0
                        val ritmo = minTotales / kmRecorridos
                        val ritmoMin = ritmo.toInt()
                        val ritmoSeg = ((ritmo - ritmoMin) * 60).toInt()
                        binding.tvRitmo.text =
                            String.format(Locale.getDefault(), "%d:%02d", ritmoMin, ritmoSeg)
                    }
                }
            }
            ultimaUbicacion = nueva
        }
    }

    private fun iniciarGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
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

    override fun onDestroy() {
        super.onDestroy()
        detenerTodo()
    }
}
