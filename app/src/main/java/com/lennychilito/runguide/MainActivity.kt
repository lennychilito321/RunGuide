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

    // UID del admin — cópialo desde Firebase Console > Authentication
    private val ADMIN_UID = "Lennychilito321@gmail.com"
    private val ADMIN_EMAIL = "Lennychilito321@gmail.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Si ya hay sesión activa, saltar directo
        val currentUser = auth.currentUser
        if (currentUser != null) {
            irAPantallaPorRol(currentUser.uid)
            return
        }

        // Botón Registrarse → RegisterActivity
        binding.btnRegisterTab.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
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
                .addOnSuccessListener { result ->
                    val userEmail = result.user?.email ?: ""
                    if (userEmail.equals(ADMIN_EMAIL, ignoreCase = true)) {
                        val intent = Intent(this, AdminActivity::class.java)
                        intent.putExtra("ROL", "admin")
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, SenderosActivity::class.java)
                        intent.putExtra("ROL", "usuario")
                        startActivity(intent)
                    }
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun irAPantallaPorRol(uid: String) {
        if (uid == ADMIN_UID) {
            val intent = Intent(this, AdminActivity::class.java)
            intent.putExtra("ROL", "admin")
            startActivity(intent)
        } else {
            val intent = Intent(this, SenderosActivity::class.java)
            intent.putExtra("ROL", "usuario")
            startActivity(intent)
        }
        finish() // Cierra el login para que no vuelvan con Back
    }
}