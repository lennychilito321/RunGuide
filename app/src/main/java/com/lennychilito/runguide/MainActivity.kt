package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.lennychilito.runguide.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Si ya hay sesión activa, saltar directo
        val currentUser = auth.currentUser
        if (currentUser != null) {
            irAPantallaPorRol()
            return
        }

        // Botón Registrarse → RegisterActivity
        binding.btnRegisterTab.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Botón Continuar con Google
        binding.btnGoogle.setOnClickListener {
            iniciarConGoogle { irAPantallaPorRol() }
        }

        // Botón Entrar como invitado
        binding.btnInvitado.setOnClickListener {
            val intent = Intent(this, SenderosActivity::class.java)
            intent.putExtra("ROL", "invitado")
            startActivity(intent)
        }



        // Botón Iniciar Sesión con Firebase
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    irAPantallaPorRol()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    /** El admin ve la misma lista de senderos que todos, con poderes extra. */
    private fun irAPantallaPorRol() {
        Rtdb.guardarPerfil()
        val intent = Intent(this, SenderosActivity::class.java)
        intent.putExtra("ROL", if (Rtdb.esAdmin()) "admin" else "usuario")
        startActivity(intent)
        finish() // Cierra el login para que no vuelvan con Back
    }
}