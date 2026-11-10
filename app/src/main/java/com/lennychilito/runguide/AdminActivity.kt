package com.lennychilito.runguide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.lennychilito.runguide.databinding.ActivityAdminBinding
import java.io.ByteArrayOutputStream

/**
 * Formulario del administrador para crear un sendero nuevo
 * o editar uno existente (si llega SENDERO_ID).
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private var imagenBase64: String = ""      // foto elegida guardada como Base64
    private var senderoId: String = ""         // vacío = crear, con valor = editar

    private val esEdicion get() = senderoId.isNotEmpty()

    private val dificultades = arrayOf("Fácil", "Moderada", "Difícil")
    private val terrenos = arrayOf("Tierra", "Asfalto", "Montaña", "Mixto")

    private val elegirFoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) procesarImagen(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Solo el administrador puede usar este formulario
        if (!Rtdb.esAdmin()) { finish(); return }

        senderoId = intent.getStringExtra("SENDERO_ID").orEmpty()

        binding.etDificultad.elegirDeLista("Dificultad", dificultades)
        binding.etTipoTerreno.elegirDeLista("Tipo de terreno", terrenos)

        binding.btnElegirFoto.setOnClickListener { elegirFoto.launch("image/*") }
        binding.btnCancelar.setOnClickListener { finish() }
        binding.btnCrear.setOnClickListener { guardar() }

        if (esEdicion) {
            binding.tvTituloForm.text = "Editar sendero"
            binding.btnCrear.text = "💾 Guardar cambios"
            cargarSendero()
        }
    }

    /** Campo de solo lectura que abre un diálogo con opciones al tocarlo. */
    private fun EditText.elegirDeLista(titulo: String, opciones: Array<String>) {
        setOnClickListener {
            AlertDialog.Builder(this@AdminActivity)
                .setTitle(titulo)
                .setItems(opciones) { _, i -> setText(opciones[i]) }
                .show()
        }
    }

    private fun cargarSendero() {
        Rtdb.ref.child("senderos/$senderoId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val s = snap.toSendero()
                    if (s == null) {
                        Toast.makeText(this@AdminActivity, "El sendero ya no existe", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                    binding.etNombre.setText(s.nombre)
                    binding.etUbicacion.setText(s.ubicacion)
                    binding.etDificultad.setText(s.dificultad)
                    binding.etDistancia.setText(s.distancia)
                    binding.etElevacion.setText(s.elevacion)
                    binding.etTipoTerreno.setText(s.tipoTerreno)
                    binding.etTiempoEstimado.setText(s.tiempoEstimado)
                    binding.etDescripcion.setText(s.descripcion)

                    imagenBase64 = s.imagenBase64
                    if (imagenBase64.isNotEmpty()) {
                        mostrarPreview(imagenBase64)
                        binding.tvFotoEstado.text = "Foto actual (elige otra para cambiarla)"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@AdminActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun guardar() {
        val nombre     = binding.etNombre.text.toString().trim()
        val ubicacion  = binding.etUbicacion.text.toString().trim()
        val dificultad = binding.etDificultad.text.toString().trim()

        if (nombre.isEmpty() || ubicacion.isEmpty() || dificultad.isEmpty()) {
            Toast.makeText(this, "Nombre, ubicación y dificultad son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        val datos = mutableMapOf<String, Any>(
            "nombre"         to nombre,
            "ubicacion"      to ubicacion,
            "distancia"      to binding.etDistancia.text.toString().trim(),
            "dificultad"     to dificultad,
            "descripcion"    to binding.etDescripcion.text.toString().trim(),
            "elevacion"      to binding.etElevacion.text.toString().trim(),
            "tipoTerreno"    to binding.etTipoTerreno.text.toString().trim(),
            "tiempoEstimado" to binding.etTiempoEstimado.text.toString().trim(),
            "imagenBase64"   to imagenBase64,
            "imagenUrl"      to ""
        )

        binding.btnCrear.isEnabled = false
        val tarea = if (esEdicion) {
            // updateChildren conserva rating y demás campos que no se tocan
            Rtdb.ref.child("senderos/$senderoId").updateChildren(datos)
        } else {
            datos["rating"] = "0.0"
            Rtdb.ref.child("senderos").push().setValue(datos)
        }

        tarea.addOnSuccessListener {
            Toast.makeText(
                this,
                if (esEdicion) "✅ Cambios guardados" else "✅ Sendero creado",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }.addOnFailureListener { e ->
            binding.btnCrear.isEnabled = true
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Convierte la URI elegida a Base64 comprimido y muestra preview
    private fun procesarImagen(uri: Uri) {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val escalado = escalarBitmap(bitmap, 600)   // máx 600px para no pasar de 1MB

        val stream = ByteArrayOutputStream()
        escalado.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        imagenBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)

        binding.imgPreview.setImageBitmap(escalado)
        binding.imgPreview.visibility = View.VISIBLE
        binding.tvFotoEstado.text = "✅ Foto lista para guardar"
    }

    private fun mostrarPreview(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            binding.imgPreview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            binding.imgPreview.visibility = View.VISIBLE
        } catch (_: Exception) {
            binding.imgPreview.visibility = View.GONE
        }
    }

    private fun escalarBitmap(bmp: Bitmap, maxPx: Int): Bitmap {
        val ratio = minOf(maxPx.toFloat() / bmp.width, maxPx.toFloat() / bmp.height)
        return if (ratio < 1f)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        else bmp
    }
}
