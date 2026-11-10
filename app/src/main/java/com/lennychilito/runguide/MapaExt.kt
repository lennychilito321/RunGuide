package com.lennychilito.runguide

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** Punto de partida cuando no hay ubicación: Popayán. */
val CENTRO_POR_DEFECTO = GeoPoint(2.4419, -76.6063)

/**
 * Configura osmdroid una sola vez: identifica la app ante los servidores de OpenStreetMap
 * (obligatorio) y guarda los mapas descargados en la caché privada de la app.
 */
fun Context.configurarOsmdroid() {
    val cfg = OsmConfig.getInstance()
    cfg.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    cfg.userAgentValue = packageName
    cfg.osmdroidBasePath = File(cacheDir, "osmdroid")
    cfg.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
}

/** true si el teléfono (o el tema elegido en Ajustes) está en modo oscuro. */
fun Context.esModoOscuro(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * Deja el mapa listo: mapas de OpenStreetMap, sin botones de zoom, con el crédito
 * «© OpenStreetMap contributors» y colores invertidos en modo oscuro.
 */
fun MapView.configurar(ctx: Context, interactivo: Boolean = true) {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(interactivo)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    isTilesScaledToDpi = true
    overlays.add(CopyrightOverlay(ctx))
    if (ctx.esModoOscuro()) {
        overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
    }
}

/** Abre el punto en la app de mapas del teléfono (cualquiera que entienda «geo:»). */
fun Context.verEnMapas(nombre: String, lat: Double, lng: Double) =
    abrirUrlOMapa(
        Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(nombre)})"),
        Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng")
    )

/** Abre las indicaciones para llegar hasta el punto. */
fun Context.comoLlegar(lat: Double, lng: Double) =
    abrirUrlOMapa(
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"),
        Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng")
    )

/** Intenta abrir [principal]; si no hay quién lo abra prueba [alternativa]; si tampoco, avisa. */
private fun Context.abrirUrlOMapa(principal: Uri, alternativa: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, principal).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, alternativa).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.sin_app_mapas), Toast.LENGTH_SHORT).show()
        }
    }
}

/** Marcador sin globo de información, con el icono por defecto (la «chincheta»). */
fun MapView.marcador(punto: GeoPoint, titulo: String? = null): Marker =
    Marker(this).apply {
        position = punto
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = titulo
        setInfoWindow(null)
    }

/** Marcador redondo para «tu posición» (punto verde con borde blanco). */
fun MapView.marcadorPosicion(ctx: Context, punto: GeoPoint): Marker =
    Marker(this).apply {
        position = punto
        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_posicion)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
    }
