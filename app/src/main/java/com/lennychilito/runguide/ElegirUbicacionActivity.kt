package com.lennychilito.runguide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.lennychilito.runguide.databinding.ActivityElegirUbicacionBinding
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * Solo admin: mapa (OpenStreetMap) para elegir la ubicación exacta de un sendero.
 * Recibe LAT/LNG opcionales (para editar) y devuelve LAT/LNG al confirmar.
 * Se toca el mapa para colocar el punto; con el dedo sobre el marcador se puede arrastrar.
 */
class ElegirUbicacionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityElegirUbicacionBinding
    private var marcador: Marker? = null
    private var seleccion: GeoPoint? = null

    private val pedirPermiso = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> if (concedido) irAMiUbicacion() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityElegirUbicacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Rtdb.esAdmin()) { finish(); return }

        val lat = intent.getDoubleExtra("LAT", Double.NaN)
        val lng = intent.getDoubleExtra("LNG", Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) seleccion = GeoPoint(lat, lng)

        val mapa = binding.mapaElegir
        mapa.configurar(this)

        // Si ya había ubicación, se centra ahí; si no, en Popayán
        mapa.controller.setZoom(if (seleccion != null) 17.0 else 13.0)
        mapa.controller.setCenter(seleccion ?: CENTRO_POR_DEFECTO)
        seleccion?.let { colocar(it) }

        // Un toque en el mapa coloca el punto
        mapa.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                colocar(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))

        binding.btnVolverMapa.setOnClickListener { finish() }
        binding.btnMiUbicacion.setOnClickListener { pedirMiUbicacion() }
        binding.btnConfirmarUbicacion.setOnClickListener {
            val p = seleccion ?: return@setOnClickListener
            setResult(
                RESULT_OK,
                Intent().putExtra("LAT", p.latitude).putExtra("LNG", p.longitude)
            )
            finish()
        }
    }

    /** Coloca el marcador (arrastrable) en [p] y actualiza las coordenadas. */
    private fun colocar(p: GeoPoint) {
        val mapa = binding.mapaElegir
        val m = marcador ?: mapa.marcador(p).also { nuevo ->
            nuevo.isDraggable = true
            nuevo.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {}
                override fun onMarkerDrag(marker: Marker) {}
                override fun onMarkerDragEnd(marker: Marker) {
                    seleccion = GeoPoint(marker.position.latitude, marker.position.longitude)
                    mostrarCoordenadas(marker.position)
                }
            })
            marcador = nuevo
            mapa.overlays.add(nuevo)
        }
        m.position = p
        seleccion = p
        mostrarCoordenadas(p)
        binding.btnConfirmarUbicacion.isEnabled = true
        mapa.invalidate()
    }

    private fun mostrarCoordenadas(p: GeoPoint) {
        binding.tvInstruccionMapa.text = getString(
            R.string.ubicacion_coords,
            String.format(Locale.US, "%.5f", p.latitude),
            String.format(Locale.US, "%.5f", p.longitude)
        )
    }

    // ── Mi ubicación (GPS del teléfono) ──────────────────────────────────────────
    private fun pedirMiUbicacion() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) irAMiUbicacion()
        else pedirPermiso.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Suppress("MissingPermission")
    private fun irAMiUbicacion() {
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { l ->
                if (l == null) {
                    Toast.makeText(this, getString(R.string.sin_ubicacion_actual), Toast.LENGTH_SHORT).show()
                } else {
                    binding.mapaElegir.controller.setZoom(17.0)
                    binding.mapaElegir.controller.animateTo(GeoPoint(l.latitude, l.longitude))
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.sin_ubicacion_actual), Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        binding.mapaElegir.onResume()
    }

    override fun onPause() {
        binding.mapaElegir.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapaElegir.onDetach()
    }
}
