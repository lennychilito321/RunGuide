package com.lennychilito.runguide

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.lennychilito.runguide.databinding.ActivityFavoritosBinding

/** Lista los senderos que el usuario guardó en usuarios/{uid}/favoritos. */
class FavoritosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var adapter: SenderoAdapter

    private val todosLosSenderos = mutableListOf<Sendero>()
    private val favoritosMostrados = mutableListOf<Sendero>()
    private var idsFavoritos: Set<String> = emptySet()

    private lateinit var refFavoritos: DatabaseReference
    private lateinit var refSenderos: DatabaseReference
    private lateinit var listenerFavoritos: ValueEventListener
    private lateinit var listenerSenderos: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { finish(); return }

        binding.btnVolverFavoritos.setOnClickListener { finish() }
        binding.btnExplorar.setOnClickListener { finish() }

        binding.recyclerFavoritos.layoutManager = LinearLayoutManager(this)
        adapter = SenderoAdapter(
            favoritosMostrados,
            rol = "usuario",
            onVerRuta = { abrirDetalle(it) },
            onCorazon = { retirarFavorito(it) }
        )
        binding.recyclerFavoritos.adapter = adapter

        refFavoritos = Rtdb.ref.child("usuarios/$uid/favoritos")
        refSenderos = Rtdb.ref.child("senderos")

        listenerFavoritos = refFavoritos.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                idsFavoritos = snapshot.children.mapNotNull { it.key }.toSet()
                actualizarLista()
            }
            override fun onCancelled(error: DatabaseError) = mostrarError(error)
        })

        listenerSenderos = refSenderos.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                todosLosSenderos.clear()
                snapshot.children.mapNotNullTo(todosLosSenderos) { it.toSendero() }
                actualizarLista()
            }
            override fun onCancelled(error: DatabaseError) = mostrarError(error)
        })
    }

    /** Cruza los ids favoritos con el catálogo y refresca la pantalla. */
    private fun actualizarLista() {
        favoritosMostrados.clear()
        favoritosMostrados.addAll(todosLosSenderos.filter { it.id in idsFavoritos })
        adapter.notifyDataSetChanged()
        binding.recyclerFavoritos.scheduleLayoutAnimation()

        val total = favoritosMostrados.size
        binding.tvContadorFavoritos.text =
            if (total == 1) getString(R.string.favoritos_uno) else getString(R.string.favoritos_n, total)

        val vacio = total == 0
        binding.estadoVacioFavoritos.visibility = if (vacio) View.VISIBLE else View.GONE
        binding.recyclerFavoritos.visibility = if (vacio) View.GONE else View.VISIBLE
    }

    private fun retirarFavorito(sendero: Sendero) {
        refFavoritos.child(sendero.id).removeValue()
        Snackbar.make(binding.root, getString(R.string.favorito_retirado), Snackbar.LENGTH_LONG)
            .setDuration(5000)
            .setAction(getString(R.string.deshacer)) { refFavoritos.child(sendero.id).setValue(true) }
            .show()
    }

    private fun mostrarError(error: DatabaseError) {
        Toast.makeText(this, getString(R.string.err_generico, error.message), Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::listenerFavoritos.isInitialized) refFavoritos.removeEventListener(listenerFavoritos)
        if (::listenerSenderos.isInitialized) refSenderos.removeEventListener(listenerSenderos)
    }
}
