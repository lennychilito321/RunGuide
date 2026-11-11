package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.lennychilito.runguide.databinding.ActivityDashboardBinding
import com.lennychilito.runguide.databinding.ItemActividadBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { finish(); return }

        // Email del usuario como saludo
        val email = user.email ?: getString(R.string.usuario_default)
        binding.tvSaludo.text = getString(R.string.hola_nombre, email.substringBefore("@"))

        binding.btnVolver.setOnClickListener { finish() }

        cargarEstadisticas(user.uid)
    }

    private fun cargarEstadisticas(uid: String) {
        FirebaseDatabase
            .getInstance("https://runner-app-f6214-default-rtdb.firebaseio.com/")
            .getReference("usuarios/$uid/actividades")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalKm      = 0.0
                    var totalActs    = 0
                    var totalSegundos = 0L
                    var mejorTiempo  = Long.MAX_VALUE

                    for (snap in snapshot.children) {
                        totalActs++
                        val km  = snap.child("kmRecorridos").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val seg = snap.child("tiempoSegundos").value?.toString()?.toLongOrNull() ?: 0L
                        totalKm       += km
                        totalSegundos += seg
                        if (seg > 0 && seg < mejorTiempo) mejorTiempo = seg
                    }

                    // Ritmo promedio (min/km)
                    val ritmoTexto = if (totalKm > 0) {
                        val minPorKm = (totalSegundos / 60.0) / totalKm
                        val m = minPorKm.toInt()
                        val s = ((minPorKm - m) * 60).toInt()
                        String.format(Locale.getDefault(), "%d:%02d", m, s)
                    } else "—"

                    // Mejor tiempo formateado
                    val mejorTexto = if (mejorTiempo == Long.MAX_VALUE) "—" else {
                        val m = (mejorTiempo / 60).toInt()
                        val s = (mejorTiempo % 60).toInt()
                        String.format(Locale.getDefault(), "%02d:%02d", m, s)
                    }

                    binding.tvKmTotal.text     = String.format(Locale.getDefault(), "%.2f", totalKm)
                    binding.tvActividades.text  = totalActs.toString()
                    binding.tvRitmo.text        = ritmoTexto
                    binding.tvMejorTiempo.text  = mejorTexto

                    mostrarHistorial(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DashboardActivity, getString(R.string.err_generico, error.message), Toast.LENGTH_SHORT).show()
                }
            })
    }

    /** «Mis actividades»: de la más reciente a la más antigua; al tocar una se abre su resumen. */
    private fun mostrarHistorial(actividades: DataSnapshot) {
        val lista = binding.listaActividades
        lista.removeAllViews()
        val formato = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

        val ordenadas = actividades.children.sortedByDescending {
            it.child("fecha").value?.toString()?.toLongOrNull() ?: 0L
        }
        binding.tvSinActividades.visibility = if (ordenadas.isEmpty()) View.VISIBLE else View.GONE

        for (a in ordenadas) {
            val tipo = a.child("tipo").value?.toString().orEmpty()
            val nombre = nombreActividad(a.child("sendero").value?.toString() ?: getString(R.string.sendero_default), tipo)
            val km = a.child("kmRecorridos").value?.toString()?.toDoubleOrNull() ?: 0.0
            val seg = a.child("tiempoSegundos").value?.toString()?.toLongOrNull() ?: 0L
            val fecha = a.child("fecha").value?.toString()?.toLongOrNull() ?: 0L
            val ruta = a.rutaGuardada()

            val fila = ItemActividadBinding.inflate(layoutInflater, lista, false)
            fila.tvActIcono.text = if (tipo == "libre") "🏃" else "🥾"
            fila.tvActItemNombre.text = nombre
            fila.tvActItemFecha.text = formato.format(Date(fecha))
            fila.tvActItemDatos.text = getString(
                R.string.actividad_datos, formatoDistancia(km), formatoDuracion(seg), formatoRitmo(seg, km)
            )
            fila.root.setOnClickListener {
                startActivity(
                    Intent(this, ResumenActividadActivity::class.java)
                        .putExtra("NOMBRE", nombre)
                        .putExtra("SEGUNDOS", seg)
                        .putExtra("KM", km)
                        .putExtra("FECHA", fecha)
                        .putExtra("RUTA", ruta.aArreglo())
                )
            }
            lista.addView(fila.root)
        }
    }
}
