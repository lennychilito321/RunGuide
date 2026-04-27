package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.lennychilito.runguide.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseDatabase.getInstance().getReference("senderos")


        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnCrear.setOnClickListener {
            val nombre = binding.etNombre.text.toString()
            val ubicacion = binding.etUbicacion.text.toString()
            val distancia = binding.etDistancia.text.toString()
            val dificultad = binding.etDificultad.text.toString()

            if (nombre.isEmpty() || ubicacion.isEmpty()) {
                Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sendero = mapOf(
                "nombre" to nombre,
                "ubicacion" to ubicacion,
                "distancia" to distancia,
                "dificultad" to dificultad
            )

            db.push().setValue(sendero)
            Toast.makeText(this, "Sendero creado", Toast.LENGTH_SHORT).show()

            binding.etNombre.text.clear()
            binding.etUbicacion.text.clear()
            binding.etDistancia.text.clear()
            binding.etDificultad.text.clear()
        }

        binding.btnEliminar.setOnClickListener {
            db.removeValue()
            Toast.makeText(this, "Todos los senderos eliminados", Toast.LENGTH_SHORT).show()
        }

        binding.btnCargarDatos.setOnClickListener {
            val test = mapOf(
                "nombre" to "PRUEBA",
                "ubicacion" to "Popayán",
                "distancia" to "1 km",
                "dificultad" to "Fácil"
            )

            db.push().setValue(test)
                .addOnSuccessListener {
                    Toast.makeText(this, "SE GUARDÓ 🔥", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "ERROR ❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}