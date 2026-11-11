package com.lennychilito.runguide

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

/**
 * Servicio en primer plano que sigue leyendo el GPS con la pantalla bloqueada
 * o con la app en segundo plano. Muestra una notificación fija con el tiempo
 * (que corre solo) y la distancia; al tocarla se vuelve a la pantalla de carrera.
 *
 * Solo usa el permiso de ubicación «mientras se usa la app»: el servicio se inicia
 * con la app abierta, así que Android le deja seguir leyendo la ubicación.
 */
class RecorridoService : Service() {

    companion object {
        private const val ACCION_INICIAR = "iniciar"
        private const val ACCION_PAUSAR = "pausar"
        private const val ACCION_REANUDAR = "reanudar"
        private const val ACCION_DETENER = "detener"
        private const val CANAL = "recorrido"
        private const val ID_NOTIFICACION = 71

        fun iniciar(ctx: Context) = ContextCompat.startForegroundService(ctx, intent(ctx, ACCION_INICIAR))
        fun pausar(ctx: Context) = ctx.startService(intent(ctx, ACCION_PAUSAR))
        fun reanudar(ctx: Context) = ctx.startService(intent(ctx, ACCION_REANUDAR))
        fun detener(ctx: Context) = ctx.startService(intent(ctx, ACCION_DETENER))

        private fun intent(ctx: Context, accion: String) =
            Intent(ctx, RecorridoService::class.java).setAction(accion)
    }

    private lateinit var fused: FusedLocationProviderClient
    private var leyendo = false
    private var kmNotificado = -1.0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { Recorrido.nuevaUbicacion(it) }
        }
    }

    /** Actualiza la notificación cuando cambian la distancia o la pausa. */
    private val oyente: () -> Unit = {
        if (Recorrido.activo && (Recorrido.km - kmNotificado >= 0.01 || Recorrido.pausado)) {
            kmNotificado = Recorrido.km
            notificar()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        crearCanal()
        Recorrido.suscribir(oyente)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_INICIAR -> {
                val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                ServiceCompat.startForeground(this, ID_NOTIFICACION, construirNotificacion(), tipo)
                empezarGps()
            }
            ACCION_PAUSAR -> {
                Recorrido.pausar()
                pararGps()
                notificar()
            }
            ACCION_REANUDAR -> {
                Recorrido.reanudar()
                empezarGps()
                notificar()
            }
            ACCION_DETENER -> {
                pararGps()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")   // la pantalla de carrera pide el permiso antes de iniciar el servicio
    private fun empezarGps() {
        if (leyendo) return
        val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        try {
            fused.requestLocationUpdates(pedido, callback, Looper.getMainLooper())
            leyendo = true
        } catch (e: SecurityException) {
            leyendo = false
        }
    }

    private fun pararGps() {
        if (!leyendo) return
        fused.removeLocationUpdates(callback)
        leyendo = false
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL, getString(R.string.notif_canal), NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun construirNotificacion(): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, CronometroActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val km = String.format(Locale.getDefault(), "%.2f", Recorrido.km)
        val texto = if (Recorrido.pausado) getString(R.string.notif_pausa, km)
        else getString(R.string.notif_texto, km)

        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(Recorrido.nombre)
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(abrir)
            // El tiempo corre solo en la notificación mientras no esté en pausa
            .setShowWhen(!Recorrido.pausado)
            .setUsesChronometer(!Recorrido.pausado)
            .setWhen(System.currentTimeMillis() - Recorrido.segundos() * 1000)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notificar() {
        if (!Recorrido.activo) return
        getSystemService(NotificationManager::class.java)
            .notify(ID_NOTIFICACION, construirNotificacion())
    }

    override fun onDestroy() {
        Recorrido.desuscribir(oyente)
        pararGps()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
