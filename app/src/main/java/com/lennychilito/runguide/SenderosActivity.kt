package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.lennychilito.runguide.databinding.ActivitySenderosBinding

class SenderosActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderosBinding
    private lateinit var adapter: SenderoAdapter
    private val listaSenderos = mutableListOf<Sendero>()

    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySenderosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        val rol = intent.getStringExtra("ROL") ?: "invitado"

        // 🔹 RecyclerView
        binding.recyclerSenderos.layoutManager = LinearLayoutManager(this)
        adapter = SenderoAdapter(listaSenderos, rol)
        binding.recyclerSenderos.adapter = adapter

        db = FirebaseDatabase.getInstance().getReference("senderos")

        cargarSenderos()
    }

    private fun cargarSenderos() {

        db.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                listaSenderos.clear()

                if (!snapshot.exists()) {
                    Toast.makeText(this@SenderosActivity, "No hay senderos aún", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                    return
                }

                for (snap in snapshot.children) {

                    val nombre = snap.child("nombre").value?.toString() ?: ""
                    val ubicacion = snap.child("ubicacion").value?.toString() ?: ""
                    val distancia = snap.child("distancia").value?.toString() ?: ""
                    val dificultad = snap.child("dificultad").value?.toString() ?: ""

                    if (nombre.isNotEmpty()) {
                        listaSenderos.add(
                            Sendero(nombre, ubicacion, distancia, dificultad)
                        )
                    }
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@SenderosActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }
}