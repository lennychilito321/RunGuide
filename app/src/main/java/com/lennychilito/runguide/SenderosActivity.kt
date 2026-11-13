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
            binding.btnLogout.setIconResource(R.drawable.ic_ingresar)
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

        // Carrera libre: el usuario corre por donde quiera y se guarda su tiempo y distancia
        binding.btnIniciarCarrera.setOnClickListener {
            if (esInvitado && !Recorrido.activo) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.login_carrera_t))
                    .setMessage(getString(R.string.login_carrera_m))
                    .setNegativeButton(getString(R.string.cancelar), null)
                    .setPositiveButton(getString(R.string.iniciar_sesion)) { _, _ ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                    }
                    .show()
            } else if (Recorrido.activo) {
                startActivity(Intent(this, CronometroActivity::class.java))
            } else {
                startActivity(Intent(this, CronometroActivity::class.java).putExtra("LIBRE", true))
            }
        }

        binding.recyclerSenderos.layoutManager = LinearLayoutManager(this)
        adapter = SenderoAdapter(
            listaSenderosMostrados,
            if (esAdmin) "admin" else rol,
            onVerRuta = { abrirDetalle(it) }
        )
        binding.recyclerSenderos.adapter = adapter

        // Al deslizar hacia abajo la cabecera se esconde (lo hace el AppBarLayout) y el botón
        // flotante se encoge a solo el icono para no tapar los senderos; al subir vuelve.
        binding.recyclerSenderos.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 4) binding.btnIniciarCarrera.shrink()
                else if (dy < -4) binding.btnIniciarCarrera.extend()
            }
        })

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

                // El administrador deja el catálogo con los senderos del sistema: agrega los
                // predeterminados que falten y borra los tres de prueba antiguos (una vez por sesión)
                if (esAdmin && !sembrando) {
                    sembrando = true
                    SenderosPorDefecto.asegurar(snapshot) {
                        Toast.makeText(
                            this@SenderosActivity,
                            getString(R.string.err_catalogo),
                            Toast.LENGTH_LONG
                        ).show()
                    }
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

    override fun onResume() {
        super.onResume()
        // Si hay una actividad en curso, el botón lleva de vuelta a ella
        binding.btnIniciarCarrera.text =
            getString(if (Recorrido.activo) R.string.volver_carrera else R.string.iniciar_carrera)
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerSenderos?.let { db.removeEventListener(it) }
    }
}
