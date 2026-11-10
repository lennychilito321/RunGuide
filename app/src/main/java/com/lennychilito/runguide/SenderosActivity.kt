package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.lennychilito.runguide.databinding.ActivitySenderosBinding

class SenderosActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderosBinding
    private lateinit var adapter: SenderoAdapter

    // Una lista con todos los datos y otra con lo que se muestra
    private val listaCompletaSenderos = mutableListOf<Sendero>()
    private val listaSenderosMostrados = mutableListOf<Sendero>()

    private lateinit var db: DatabaseReference
    private var listenerSenderos: ValueEventListener? = null

    /** Clave de dificultad seleccionada ("" = todas). */
    private var filtroActual = ""

    private var esAdmin = false
    private var sembrando = false   // evita cargar los predeterminados dos veces seguidas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySenderosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rol = intent.getStringExtra("ROL") ?: "invitado"
        val esInvitado = rol == "invitado"
        esAdmin = rol == "admin" && Rtdb.esAdmin()

        binding.btnAjustes.setOnClickListener {
            startActivity(Intent(this, AjustesActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Perfil y favoritos solo para usuarios con sesión
        if (esInvitado) {
            binding.btnDashboard.visibility = View.GONE
            binding.btnFavoritos.visibility = View.GONE
            binding.btnLogout.text = getString(R.string.ingresar)
        } else {
            binding.btnDashboard.setOnClickListener {
                startActivity(Intent(this, DashboardActivity::class.java))
            }
            binding.btnFavoritos.setOnClickListener {
                startActivity(Intent(this, FavoritosActivity::class.java))
            }
        }

        if (esAdmin) {
            binding.rowAdmin.visibility = View.VISIBLE
            binding.btnNuevoSendero.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
            binding.btnUsuarios.setOnClickListener {
                startActivity(Intent(this, UsuariosActivity::class.java))
            }
            binding.btnPredeterminados.setOnClickListener { confirmarPredeterminados() }
        }

        binding.recyclerSenderos.layoutManager = LinearLayoutManager(this)
        adapter = SenderoAdapter(
            listaSenderosMostrados,
            if (esAdmin) "admin" else rol,
            onVerRuta = { abrirDetalle(it) }
        )
        binding.recyclerSenderos.adapter = adapter

        configurarFiltros()

        db = Rtdb.ref.child("senderos")
        cargarSenderos()
    }

    private fun configurarFiltros() {
        binding.chipsDificultad.setOnCheckedStateChangeListener { _, checkedIds ->
            filtroActual = when (checkedIds.firstOrNull()) {
                R.id.chipFacil    -> "faci"
                R.id.chipModerada -> "mode"
                R.id.chipDificil  -> "difi"
                else              -> ""
            }
            aplicarFiltro()
        }
        binding.btnLimpiarFiltro.setOnClickListener {
            binding.chipsDificultad.check(R.id.chipTodas)
        }
    }

    private fun cargarSenderos() {
        listenerSenderos = db.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                listaCompletaSenderos.clear()
                snapshot.children.mapNotNullTo(listaCompletaSenderos) { it.toSendero() }
                aplicarFiltro()

                // Catálogo vacío: el administrador carga los senderos que vienen con el sistema
                if (esAdmin && !sembrando && snapshot.childrenCount == 0L) {
                    sembrando = true
                    SenderosPorDefecto.cargar()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@SenderosActivity,
                    getString(R.string.err_generico, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    /** Restaura El Morro, La Torre y San Bernardino con sus datos originales. */
    private fun confirmarPredeterminados() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dlg_predeterminados_t))
            .setMessage(getString(R.string.dlg_predeterminados_m))
            .setNegativeButton(getString(R.string.cancelar), null)
            .setPositiveButton(getString(R.string.cargar)) { _, _ ->
                SenderosPorDefecto.cargar { ok ->
                    Toast.makeText(
                        this,
                        getString(if (ok) R.string.predeterminados_ok else R.string.err_predeterminados),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    /** Muestra los senderos de la dificultad elegida y actualiza el contador. */
    private fun aplicarFiltro() {
        val visibles = if (filtroActual.isEmpty()) {
            listaCompletaSenderos
        } else {
            listaCompletaSenderos.filter { it.dificultad.claveDificultad() == filtroActual }
        }

        listaSenderosMostrados.clear()
        listaSenderosMostrados.addAll(visibles)
        adapter.notifyDataSetChanged()
        binding.recyclerSenderos.scheduleLayoutAnimation()

        binding.tvContador.text = getString(R.string.rutas_disponibles, visibles.size)

        val vacio = visibles.isEmpty()
        binding.estadoVacio.visibility = if (vacio) View.VISIBLE else View.GONE
        binding.recyclerSenderos.visibility = if (vacio) View.GONE else View.VISIBLE
        if (vacio) {
            val hayFiltro = filtroActual.isNotEmpty()
            binding.tvVacio.text =
                if (hayFiltro) getString(R.string.sin_resultados) else getString(R.string.sin_senderos)
            binding.btnLimpiarFiltro.visibility = if (hayFiltro) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerSenderos?.let { db.removeEventListener(it) }
    }
}
