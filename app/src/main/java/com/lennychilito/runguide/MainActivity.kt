package com.lennychilito.runguide

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lennychilito.runguide.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAdmin.setOnClickListener {
            val intent = Intent(this, AdminActivity::class.java)
            intent.putExtra("ROL", "admin")
            startActivity(intent)
        }

        binding.btnInvitado.setOnClickListener {
            val intent = Intent(this, SenderosActivity::class.java)
            intent.putExtra("ROL", "invitado")
            startActivity(intent)
        }

        binding.btnLogin.setOnClickListener {

            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email == "admin" && password == "1234") {
                val intent = Intent(this, AdminActivity::class.java)
                intent.putExtra("ROL", "admin")
                startActivity(intent)
            } else {
                val intent = Intent(this, SenderosActivity::class.java)
                intent.putExtra("ROL", "usuario")
                startActivity(intent)
            }
        }
    }
}