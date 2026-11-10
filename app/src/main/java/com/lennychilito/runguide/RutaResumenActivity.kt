package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.lennychilito.runguide.databinding.ActivityRutaResumenBinding
import org.osmdroid.util.GeoPoint

/**
 * Resumen de la ruta: mapa con el punto de inicio, los datos del sendero y el botón
 * para iniciar la ruta. Se abre al tocar el mapa del detalle del sendero.
 * Recibe los mismos extras que DetalleSenderoActivity.
 */
class RutaResumenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRutaResumenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRutaResumenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val nombre = intent.getStringExtra("NOMBRE").orEmpty()
        val lat = intent.getDoubleExtra("LAT", Double.NaN)
        val lng = intent.getDoubleExtra("LNG", Double.NaN)
        val tieneUbicacion = !lat.isNaN() && !lng.isNaN()

        // ── Datos del sendero ────────────────────────────────────────────────────
        binding.tvResNombre.text = nombre
        binding.tvResTerreno.text = "📍 ${terrenoLocal(intent.getStringExtra("TIPO_TERRENO").orEmpty())}"
        binding.tvResRating.text = "★ ${intent.getStringExtra("RATING") ?: "0.0"}"
        binding.tvResDistancia.text = intent.getStringExtra("DISTANCIA").orEmpty().ifEmpty { "—" }
        binding.tvResTiempo.text = intent.getStringExtra("TIEMPO_ESTIMADO").orEmpty().ifEmpty { "—" }
        binding.tvResElevacion.text = intent.getStringExtra("ELEVACION").orEmpty().ifEmpty { "—" }
        binding.tvResDificultad.text = dificultadLocal(intent.getStringExtra("DIFICULTAD").orEmpty())
        binding.tvResDescripcion.text =
            intent.getStringExtra("DESCRIPCION").orEmpty().ifEmpty { getString(R.string.sin_descripcion) }

        // ── Mapa ─────────────────────────────────────────────────────────────────
        val mapa = binding.mapaResumen
        mapa.configurar(this)
        if (tieneUbicacion) {
            val punto = GeoPoint(lat, lng)
            mapa.controller.setZoom(15.0)
            mapa.controller.setCenter(punto)
            mapa.overlays.add(mapa.marcador(punto, nombre))
        } else {
            mapa.controller.setZoom(13.0)
            mapa.controller.setCenter(CENTRO_POR_DEFECTO)
            binding.tvSinUbicacion.visibility = View.VISIBLE
        }

        // ── Botones ──────────────────────────────────────────────────────────────
        binding.btnVolverResumen.setOnClickListener { finish() }

        binding.btnIniciarResumen.setOnClickListener {
            // Pasa los mismos datos (nombre, distancia, tiempo estimado y ubicación)
            startActivity(Intent(this, CronometroActivity::class.java).putExtras(intent))
        }

        if (tieneUbicacion) {
            binding.btnComoLlegarResumen.setOnClickListener { comoLlegar(lat, lng) }
        } else {
            binding.btnComoLlegarResumen.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapaResumen.onResume()
    }

    override fun onPause() {
        binding.mapaResumen.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapaResumen.onDetach()
    }
}
