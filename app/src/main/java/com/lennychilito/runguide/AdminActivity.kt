package com.lennychilito.runguide

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.lennychilito.runguide.databinding.ActivityAdminBinding
import java.io.ByteArrayOutputStream

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var db: DatabaseReference
    private var imagenBase64: String = ""   // foto elegida guardada como Base64

    // Lanzador de galería con el nuevo API (no startActivityForResult)
    private val elegirFoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        procesarImagen(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseDatabase
            .getInstance("https://runner-app-f6214-default-rtdb.firebaseio.com/")
            .getReference("senderos")

        // Logout
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Elegir foto de la galería
        binding.btnElegirFoto.setOnClickListener {
            elegirFoto.launch("image/*")
        }

        // Crear sendero
        binding.btnCrear.setOnClickListener {
            val nombre      = binding.etNombre.text.toString().trim()
            val ubicacion   = binding.etUbicacion.text.toString().trim()
            val dificultad  = binding.etDificultad.text.toString().trim()
            val distancia   = binding.etDistancia.text.toString().trim()
            val descripcion = binding.etDescripcion.text.toString().trim()
            val elevacion   = binding.etElevacion.text.toString().trim()
            val tipoTerreno = binding.etTipoTerreno.text.toString().trim()
            val tiempo      = binding.etTiempoEstimado.text.toString().trim()

            if (nombre.isEmpty() || ubicacion.isEmpty() || dificultad.isEmpty()) {
                Toast.makeText(this, "Nombre, ubicación y dificultad son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sendero = mapOf(
                "nombre"         to nombre,
                "ubicacion"      to ubicacion,
                "distancia"      to distancia,
                "dificultad"     to dificultad,
                "descripcion"    to descripcion,
                "elevacion"      to elevacion,
                "tipoTerreno"    to tipoTerreno,
                "tiempoEstimado" to tiempo,
                "imagenBase64"   to imagenBase64,   // foto real en Base64
                "imagenUrl"      to "",              // ya no se usa URL manual
                "rating"         to "0.0"
            )

            db.push().setValue(sendero)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Sendero creado", Toast.LENGTH_SHORT).show()
                    limpiarCampos()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        // Eliminar todos
        binding.btnEliminar.setOnClickListener {
            db.removeValue()
            Toast.makeText(this, "Todos los senderos eliminados", Toast.LENGTH_SHORT).show()
        }

        // Datos de prueba (sin foto)
        binding.btnCargarDatos.setOnClickListener {
            val pruebas = listOf(
                mapOf(
                    "nombre" to "Sendero El Morro", "ubicacion" to "Popayán",
                    "distancia" to "1.4", "dificultad" to "Moderada",
                    "descripcion" to "Ruta con cascada al final, ideal para principiantes.",
                    "elevacion" to "80m", "tipoTerreno" to "Tierra",
                    "tiempoEstimado" to "25", "imagenBase64" to "", "imagenUrl" to "", "rating" to "4.2"
                ),
                mapOf(
                    "nombre" to "Cerro Ñimbe", "ubicacion" to "Popayán",
                    "distancia" to "3.2", "dificultad" to "Difícil",
                    "descripcion" to "Ascenso técnico con vistas panorámicas.",
                    "elevacion" to "250m", "tipoTerreno" to "Montaña",
                    "tiempoEstimado" to "60", "imagenBase64" to "", "imagenUrl" to "", "rating" to "4.7"
                ),
                mapOf(
                    "nombre" to "Ruta Río Molino", "ubicacion" to "Popayán",
                    "distancia" to "2.0", "dificultad" to "Fácil",
                    "descripcion" to "Sendero plano junto al río, apto para toda la familia.",
                    "elevacion" to "10m", "tipoTerreno" to "Asfalto",
                    "tiempoEstimado" to "30", "imagenBase64" to "", "imagenUrl" to "", "rating" to "4.0"
                )
            )
            pruebas.forEach { db.push().setValue(it) }
            Toast.makeText(this, "✅ 3 senderos de prueba cargados", Toast.LENGTH_SHORT).show()
        }
    }

    // Convierte la URI elegida a Base64 comprimido y muestra preview
    private fun procesarImagen(uri: Uri) {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

        // Redimensionar a máx 600px para no superar 1MB en Base64
        val escalado = escalarBitmap(bitmap, 600)

        val stream = ByteArrayOutputStream()
        escalado.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        imagenBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)

        // Mostrar preview en el ImageView
        binding.imgPreview.setImageBitmap(escalado)
        binding.imgPreview.visibility = android.view.View.VISIBLE
        binding.tvFotoEstado.text = "✅ Foto lista para guardar"
    }

    private fun escalarBitmap(bmp: Bitmap, maxPx: Int): Bitmap {
        val ancho  = bmp.width
        val alto   = bmp.height
        val ratio  = minOf(maxPx.toFloat() / ancho, maxPx.toFloat() / alto)
        return if (ratio < 1f)
            Bitmap.createScaledBitmap(bmp, (ancho * ratio).toInt(), (alto * ratio).toInt(), true)
        else bmp
    }

    private fun limpiarCampos() {
        binding.etNombre.text.clear()
        binding.etUbicacion.text.clear()
        binding.etDistancia.text.clear()
        binding.etDificultad.text.clear()
        binding.etDescripcion.text.clear()
        binding.etElevacion.text.clear()
        binding.etTipoTerreno.text.clear()
        binding.etTiempoEstimado.text.clear()
        binding.imgPreview.visibility = android.view.View.GONE
        binding.tvFotoEstado.text = "Sin foto seleccionada"
        imagenBase64 = ""
    }
}
