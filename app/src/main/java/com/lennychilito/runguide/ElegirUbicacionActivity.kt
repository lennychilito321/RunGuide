package com.lennychilito.runguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.lennychilito.runguide.databinding.ActivityElegirUbicacionBinding
import java.util.Locale

/**
 * Solo admin: mapa de Google para elegir la ubicación exacta de un sendero.
 * Recibe LAT/LNG opcionales (para editar) y devuelve LAT/LNG al confirmar.
 */
class ElegirUbicacionActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityElegirUbicacionBinding
    private var mapa: GoogleMap? = null
    private var marcador: Marker? = null
    private var seleccion: LatLng? = null

    private val pedirPermiso = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> if (concedido) activarMiUbicacion() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityElegirUbicacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Rtdb.esAdmin()) { finish(); return }

        val lat = intent.getDoubleExtra("LAT", Double.NaN)
        val lng = intent.getDoubleExtra("LNG", Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) seleccion = LatLng(lat, lng)

        binding.btnVolverMapa.setOnClickListener { finish() }
        binding.btnConfirmarUbicacion.setOnClickListener {
            val p = seleccion ?: return@setOnClickListener
            setResult(
                RESULT_OK,
                Intent().putExtra("LAT", p.latitude).putExtra("LNG", p.longitude)
            )
            finish()
        }

        (supportFragmentManager.findFragmentById(R.id.mapaElegir) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(m: GoogleMap) {
        mapa = m
        m.uiSettings.isZoomControlsEnabled = true
        // Deja libres los controles del mapa: la barra de arriba y el panel de abajo los taparían
        val d = resources.displayMetrics.density
        m.setPadding(0, (110 * d).toInt(), 0, (150 * d).toInt())

        // Si ya había ubicación, se centra ahí; si no, en Popayán
        val inicio = seleccion ?: LatLng(2.4419, -76.6063)
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(inicio, if (seleccion != null) 16f else 13f))
        seleccion?.let { colocar(it) }

        m.setOnMapClickListener { colocar(it) }
        m.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}
            override fun onMarkerDrag(marker: Marker) {}
            override fun onMarkerDragEnd(marker: Marker) {
                seleccion = marker.position
                mostrarCoordenadas(marker.position)
            }
        })

        // Botón "mi ubicación" si el usuario da permiso (opcional)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) activarMiUbicacion()
        else pedirPermiso.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun colocar(p: LatLng) {
        marcador?.remove()
        marcador = mapa?.addMarker(MarkerOptions().position(p).draggable(true))
        seleccion = p
        mostrarCoordenadas(p)
        binding.btnConfirmarUbicacion.isEnabled = true
    }

    private fun mostrarCoordenadas(p: LatLng) {
        binding.tvInstruccionMapa.text = getString(
            R.string.ubicacion_coords,
            String.format(Locale.US, "%.5f", p.latitude),
            String.format(Locale.US, "%.5f", p.longitude)
        )
    }

    @SuppressLint("MissingPermission")
    private fun activarMiUbicacion() {
        mapa?.isMyLocationEnabled = true
    }
}
