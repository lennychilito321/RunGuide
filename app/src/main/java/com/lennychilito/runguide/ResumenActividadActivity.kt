package com.lennychilito.runguide

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import com.lennychilito.runguide.databinding.ActivityResumenActividadBinding
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resumen de una actividad, al estilo de Strava: mapa del recorrido y una tarjeta con
 * distancia, ritmo y tiempo con el logo de RunGuide, que se puede compartir o guardar
 * como imagen (también con fondo transparente, para ponerla encima de una foto).
 *
 * Extras: NOMBRE, SEGUNDOS, KM, FECHA, RUTA ([lat0, lng0, lat1, lng1, …]).
 */
class ResumenActividadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResumenActividadBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResumenActividadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val segundos = intent.getLongExtra("SEGUNDOS", 0L)
        val km = intent.getDoubleExtra("KM", 0.0)
        val fecha = intent.getLongExtra("FECHA", System.currentTimeMillis())
        val ruta = intent.getDoubleArrayExtra("RUTA")?.aPuntos().orEmpty()

        binding.tvActNombre.text = intent.getStringExtra("NOMBRE").orEmpty()
        binding.tvActFecha.text = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(fecha))
        binding.tvTarjKm.text = getString(R.string.km_valor, formatoDistancia(km))
        binding.tvTarjRitmo.text = getString(R.string.ritmo_km, formatoRitmo(segundos, km))
        binding.tvTarjTiempo.text = formatoDuracion(segundos)

        configurarMapa(ruta)

        binding.btnCerrarResumen.setOnClickListener { finish() }

        // Fondo transparente: la tarjeta queda solo con el texto blanco y el logo
        binding.swTransparente.setOnCheckedChangeListener { _, transparente ->
            binding.tarjeta.background =
                if (transparente) null else ContextCompat.getDrawable(this, R.drawable.bg_tarjeta_compartir)
        }

        binding.btnCompartir.setOnClickListener { compartir() }

        // Guardar en la galería sin pedir permisos solo es posible en Android 10 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.btnGuardarImagen.setOnClickListener { guardarEnGaleria() }
        } else {
            binding.btnGuardarImagen.visibility = View.GONE
        }
    }

    private fun configurarMapa(ruta: List<org.osmdroid.util.GeoPoint>) {
        if (ruta.isEmpty()) {
            binding.contMapaActividad.visibility = View.GONE
            return
        }
        val mapa = binding.mapaActividad
        mapa.configurar(this, interactivo = false)

        val linea = Polyline().apply {
            outlinePaint.color = getColor(R.color.verde_primario)
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            setPoints(ruta)
        }
        mapa.overlays.add(linea)
        mapa.overlays.add(mapa.marcador(ruta.first()))
        if (ruta.size > 1) mapa.overlays.add(mapa.marcadorPosicion(this, ruta.last()))

        if (ruta.size == 1) {
            mapa.controller.setZoom(16.0)
            mapa.controller.setCenter(ruta.first())
        } else {
            // Ajusta el zoom para que se vea todo el recorrido, cuando el mapa ya tiene tamaño
            mapa.controller.setCenter(ruta.first())
            mapa.addOnFirstLayoutListener { _, _, _, _, _ ->
                mapa.zoomToBoundingBox(BoundingBox.fromGeoPoints(ruta), false, 60)
                if (mapa.zoomLevelDouble > 18.0) mapa.controller.setZoom(18.0)
            }
        }
    }

    /** La tarjeta convertida en imagen PNG (con transparencia si el interruptor está activo). */
    private fun crearImagen(): Bitmap? =
        try {
            binding.tarjeta.drawToBitmap(Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }

    private fun compartir() {
        val imagen = crearImagen() ?: return avisarError()
        try {
            val carpeta = File(cacheDir, "compartir").apply { mkdirs() }
            val archivo = File(carpeta, "runguide_actividad.png")
            FileOutputStream(archivo).use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)

            val enviar = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(enviar, getString(R.string.compartir)))
        } catch (e: Exception) {
            avisarError()
        }
    }

    private fun guardarEnGaleria() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val imagen = crearImagen() ?: return avisarError()
        try {
            val valores = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "RunGuide_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RunGuide")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
                ?: return avisarError()
            contentResolver.openOutputStream(uri)?.use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
            valores.clear()
            valores.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, valores, null, null)
            Toast.makeText(this, getString(R.string.imagen_guardada), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            avisarError()
        }
    }

    private fun avisarError() {
        Toast.makeText(this, getString(R.string.error_imagen), Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        binding.mapaActividad.onResume()
    }

    override fun onPause() {
        binding.mapaActividad.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.mapaActividad.onDetach()
        super.onDestroy()
    }
}
