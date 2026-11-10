package com.lennychilito.runguide

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lennychilito.runguide.databinding.ActivityResenasBinding

class ResenasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResenasBinding
    private val listaResenas = mutableListOf<Resena>()
    private lateinit var adapter: ResenaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResenasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val senderoId = intent.getStringExtra("SENDERO_ID") ?: ""
        val nombre    = intent.getStringExtra("NOMBRE") ?: "Sendero"

        binding.tvTituloResenas.text = "Reseñas — $nombre"
        binding.btnVolverResenas.setOnClickListener { finish() }

        // RecyclerView
        binding.recyclerResenas.layoutManager = LinearLayoutManager(this)
        val alEliminar: ((Resena) -> Unit)? =
            if (Rtdb.esAdmin()) { r: Resena -> confirmarEliminar(senderoId, r) } else null
        adapter = ResenaAdapter(listaResenas, alEliminar)
        binding.recyclerResenas.adapter = adapter

        // Cargar reseñas desde Firebase
        FirebaseDatabase
            .getInstance("https://runner-app-f6214-default-rtdb.firebaseio.com/")
            .getReference("resenas/$senderoId")
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    listaResenas.clear()
                    for (snap in snapshot.children) {
                        val r = Resena(
                            id        = snap.key.orEmpty(),
                            uid       = snap.child("uid").value?.toString() ?: "",
                            email    = snap.child("email").value?.toString() ?: "Anónimo",
                            estrellas = snap.child("estrellas").value?.toString()?.toIntOrNull() ?: 0,
                            texto     = snap.child("texto").value?.toString() ?: "",
                            foto      = snap.child("foto").value?.toString() ?: "",
                            fecha     = snap.child("fecha").value?.toString() ?: ""
                        )
                        listaResenas.add(0, r) // más recientes primero
                    }
                    adapter.notifyDataSetChanged()
                    binding.tvContadorResenas.text = "${listaResenas.size} reseña(s)"
                }
                override fun onCancelled(e: DatabaseError) {
                    Toast.makeText(this@ResenasActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun confirmarEliminar(senderoId: String, r: Resena) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar reseña")
            .setMessage("¿Eliminar la reseña de ${r.email.substringBefore("@")}? Se recalculará la calificación del sendero.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                Rtdb.eliminarResena(senderoId, r.id) { ok ->
                    Toast.makeText(
                        this,
                        if (ok) "Reseña eliminada" else "No se pudo eliminar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
}
