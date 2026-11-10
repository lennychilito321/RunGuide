package com.lennychilito.runguide

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.lennychilito.runguide.databinding.ActivityUsuariosBinding
import com.lennychilito.runguide.databinding.ItemUsuarioBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una carrera guardada por un usuario. */
data class Actividad(val sendero: String, val km: Double, val segundos: Long, val fecha: Long)

/** Resumen de un usuario con sus carreras. */
data class UsuarioStats(
    val uid: String,
    val email: String,
    val actividades: List<Actividad>
) {
    val rutas get() = actividades.size
    val km get() = actividades.sumOf { it.km }
    val segundos get() = actividades.sumOf { it.segundos }
}

/** Solo admin: lista de usuarios con sus rutas, kilómetros y tiempo acumulado. */
class UsuariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsuariosBinding
    private val usuarios = mutableListOf<UsuarioStats>()
    private lateinit var adapter: UsuarioAdapter
    private var listener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Rtdb.esAdmin()) { finish(); return }

        binding.btnVolverUsuarios.setOnClickListener { finish() }

        adapter = UsuarioAdapter(usuarios) { mostrarDetalle(it) }
        binding.recyclerUsuarios.layoutManager = LinearLayoutManager(this)
        binding.recyclerUsuarios.adapter = adapter

        listener = Rtdb.ref.child("usuarios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usuarios.clear()
                for (u in snapshot.children) {
                    val actividades = u.child("actividades").children.map { a ->
                        Actividad(
                            sendero = a.child("sendero").value?.toString() ?: "Sendero",
                            km = a.child("kmRecorridos").value?.toString()?.toDoubleOrNull() ?: 0.0,
                            segundos = a.child("tiempoSegundos").value?.toString()?.toLongOrNull() ?: 0L,
                            fecha = a.child("fecha").value?.toString()?.toLongOrNull() ?: 0L
                        )
                    }.sortedByDescending { it.fecha }
                    usuarios.add(
                        UsuarioStats(
                            uid = u.key.orEmpty(),
                            email = u.child("email").value?.toString().orEmpty(),
                            actividades = actividades
                        )
                    )
                }
                usuarios.sortByDescending { it.km }
                adapter.notifyDataSetChanged()
                actualizarResumen()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@UsuariosActivity,
                    getString(R.string.err_permisos, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun actualizarResumen() {
        val rutas = usuarios.sumOf { it.rutas }
        val km = usuarios.sumOf { it.km }
        val seg = usuarios.sumOf { it.segundos }
        binding.tvResumenUsuarios.text =
            getString(R.string.resumen_usuarios, usuarios.size, rutas, formatoKm(km), formatoTiempo(seg))
        val vacio = usuarios.isEmpty()
        binding.tvVacioUsuarios.visibility = if (vacio) View.VISIBLE else View.GONE
        binding.recyclerUsuarios.visibility = if (vacio) View.GONE else View.VISIBLE
    }

    private fun mostrarDetalle(u: UsuarioStats) {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val cuerpo = if (u.actividades.isEmpty()) getString(R.string.sin_rutas)
        else u.actividades.joinToString("\n\n") {
            "${it.sendero}\n${fmt.format(Date(it.fecha))} · ${formatoKm(it.km)} km · ${formatoTiempo(it.segundos)}"
        }
        AlertDialog.Builder(this)
            .setTitle(u.nombreVisible(this))
            .setMessage(
                getString(R.string.usuarios_detalle, u.rutas, formatoKm(u.km), formatoTiempo(u.segundos)) + "\n\n" + cuerpo
            )
            .setPositiveButton(getString(R.string.cerrar), null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.let { Rtdb.ref.child("usuarios").removeEventListener(it) }
    }
}

fun UsuarioStats.nombreVisible(ctx: Context) =
    if (email.isNotEmpty()) email.substringBefore("@") else ctx.getString(R.string.usuario_uid, uid.take(6))

fun formatoKm(km: Double): String = String.format(Locale.getDefault(), "%.2f", km)

fun formatoTiempo(segundos: Long): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    return if (h > 0) "${h} h ${m} min" else "$m min"
}

class UsuarioAdapter(
    private val lista: List<UsuarioStats>,
    private val onClick: (UsuarioStats) -> Unit
) : RecyclerView.Adapter<UsuarioAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemUsuarioBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemUsuarioBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val u = lista[position]
        val b = holder.b
        val ctx = b.root.context
        val nombre = u.nombreVisible(ctx)
        b.tvNombreUsuario.text = nombre
        b.tvCorreoUsuario.text = u.email.ifEmpty { ctx.getString(R.string.sin_correo) }
        b.tvAvatarUsuario.text = nombre.firstOrNull()?.uppercase() ?: "U"
        b.tvRutasUsuario.text =
            if (u.rutas == 1) ctx.getString(R.string.ruta_una) else ctx.getString(R.string.rutas_n, u.rutas)
        b.tvKmUsuario.text = "${formatoKm(u.km)} km"
        b.tvTiempoUsuario.text = formatoTiempo(u.segundos)
        b.root.setOnClickListener { onClick(u) }
    }
}
