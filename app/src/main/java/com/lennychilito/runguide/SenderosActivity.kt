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

    // 🔹 Dos listas: una para guardar todos los datos originales y otra para lo que se muestra
    private val listaCompletaSenderos = mutableListOf<Sendero>()
    private val listaSenderosMostrados = mutableListOf<Sendero>()

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

        // 🔹 RecyclerView configurado con la lista que se va a mostrar dinámicamente
        binding.recyclerSenderos.layoutManager = LinearLayoutManager(this)
        adapter = SenderoAdapter(listaSenderosMostrados, rol) { sendero ->
            val intent = Intent(this, DetalleSenderoActivity::class.java).apply {
                putExtra("nombre", sendero.nombre)
                putExtra("ubicacion", sendero.ubicacion)
                putExtra("distancia", sendero.distancia)
                putExtra("dificultad", sendero.dificultad)
            }
            startActivity(intent)
        }
        binding.recyclerSenderos.adapter = adapter

        db = FirebaseDatabase.getInstance().getReference("senderos")

        cargarSenderos()

        // 🔹 EJEMPLO DE CONFIGURACIÓN DE FILTROS POR DIFICULTAD
        // Asegúrate de cambiar los IDs (ej: btnFacil, btnDificil, btnTodos)
        // por los nombres reales que tengas en tu archivo XML de ActivitySenderosBinding.
        /*
        binding.btnTodos?.setOnClickListener { filtrarPorDificultad("Todos") }
        binding.btnFacil?.setOnClickListener { filtrarPorDificultad("Fácil") }
        binding.btnMedio?.setOnClickListener { filtrarPorDificultad("Intermedio") }
        binding.btnDificil?.setOnClickListener { filtrarPorDificultad("Difícil") }
        */
    }

    private fun cargarSenderos() {
        db.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                listaCompletaSenderos.clear()

                if (!snapshot.exists()) {
                    Toast.makeText(this@SenderosActivity, "No hay senderos aún", Toast.LENGTH_SHORT).show()
                    actualizarListaMostrada(emptyList())
                    return
                }

                for (snap in snapshot.children) {
                    val id = snap.key ?: ""
                    val nombre = snap.child("nombre").value?.toString() ?: ""
                    val ubicacion = snap.child("ubicacion").value?.toString() ?: ""
                    val distancia = snap.child("distancia").value?.toString() ?: ""
                    val dificultad = snap.child("dificultad").value?.toString() ?: ""
                    val tipoTerreno = snap.child("tipoTerreno").value?.toString() ?: ""
                    val ratingStr = snap.child("rating").value?.toString() ?: "0.0"
                    val rating = ratingStr.toDoubleOrNull() ?: 0.0
                    val imagenBase64 = snap.child("imagenBase64").value?.toString() ?: ""
                    val imagenUrl = snap.child("imagenUrl").value?.toString() ?: ""

                    if (nombre.isNotEmpty()) {
                        val sendero = Sendero(
                            id = id,
                            nombre = nombre,
                            ubicacion = ubicacion,
                            distancia = distancia,
                            dificultad = dificultad,
                            tipoTerreno = tipoTerreno,
                            rating = rating,
                            imagenBase64 = imagenBase64,
                            imagenUrl = imagenUrl
                        )
                        listaCompletaSenderos.add(sendero)
                    }
                }

                // Por defecto cargamos todos al iniciar
                actualizarListaMostrada(listaCompletaSenderos)
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

    // 🔹 Función para filtrar los senderos según el nivel de dificultad seleccionado
    private fun filtrarPorDificultad(dificultadSeleccionada: String) {
        if (dificultadSeleccionada.equals("Todos", ignoreCase = true)) {
            actualizarListaMostrada(listaCompletaSenderos)
        } else {
            val listaFiltrada = listaCompletaSenderos.filter {
                it.dificultad.equals(dificultadSeleccionada, ignoreCase = true)
            }
            actualizarListaMostrada(listaFiltrada)
        }
    }

    // 🔹 Método auxiliar para limpiar y actualizar los elementos visibles en el adaptador
    private fun actualizarListaMostrada(nuevaLista: List<Sendero>) {
        listaSenderosMostrados.clear()
        listaSenderosMostrados.addAll(nuevaLista)
        adapter.notifyDataSetChanged()
    }
}