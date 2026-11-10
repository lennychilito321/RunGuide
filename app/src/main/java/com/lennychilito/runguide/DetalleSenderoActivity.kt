package com.lennychilito.runguide

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lennychilito.runguide.databinding.ActivityDetalleSenderoBinding
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetalleSenderoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleSenderoBinding
    private val db = FirebaseDatabase
        .getInstance("https://runner-app-f6214-default-rtdb.firebaseio.com/")
        .reference
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private val emailUsuario get() = FirebaseAuth.getInstance().currentUser?.email ?: "Anónimo"
    private var senderoId = ""
    private var calificacionActual = 0
    private var esFavorito = false
    private var fotoResenaBase64 = ""

    // Selector de foto para la reseña
    private val elegirFotoResena = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        procesarFotoResena(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleSenderoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Datos del intent
        senderoId          = intent.getStringExtra("SENDERO_ID") ?: ""
        val nombre         = intent.getStringExtra("NOMBRE") ?: ""
        val distancia      = intent.getStringExtra("DISTANCIA") ?: "0"
        val dificultad     = intent.getStringExtra("DIFICULTAD") ?: ""
        val rating         = intent.getStringExtra("RATING") ?: "0.0"
        val imagenUrl      = intent.getStringExtra("IMAGEN_URL") ?: ""
        val imagenBase64   = intent.getStringExtra("IMAGEN_BASE64") ?: ""
        val descripcion    = intent.getStringExtra("DESCRIPCION") ?: ""
        val elevacion      = intent.getStringExtra("ELEVACION") ?: "0m"
        val tipoTerreno    = intent.getStringExtra("TIPO_TERRENO") ?: ""
        val tiempoEstimado = intent.getStringExtra("TIEMPO_ESTIMADO") ?: "0"

        // Imagen portada
        when {
            imagenBase64.isNotEmpty() -> {
                val bytes = Base64.decode(imagenBase64, Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.imgPortada.setImageBitmap(bmp)
            }
            imagenUrl.isNotEmpty() -> Glide.with(this).load(imagenUrl).into(binding.imgPortada)
            else -> binding.imgPortada.setImageResource(R.drawable.montana)
        }

        // Llenar vistas
        binding.tvNombre.text      = nombre
        binding.tvTipoTerreno.text = "📍 $tipoTerreno"
        binding.tvDistancia.text   = distancia
        binding.tvDificultad.text  = dificultad
        binding.tvRating.text      = rating
        binding.tvElevacion.text   = elevacion
        binding.tvTiempo.text      = tiempoEstimado
        binding.tvDescripcion.text = descripcion.ifEmpty { "Sin descripción." }
        binding.tvNivelBadge.text  = "📋 Nivel: $dificultad"

        // Volver
        binding.btnVolver.setOnClickListener { finish() }

        // Iniciar ruta
        binding.btnIniciarRuta.setOnClickListener {
            val i = Intent(this, CronometroActivity::class.java)
            i.putExtra("NOMBRE", nombre)
            i.putExtra("DISTANCIA", distancia)
            startActivity(i)
        }

        // Ver reseñas
        cargarContadorResenas()
        binding.btnVerResenas.setOnClickListener {
            val i = Intent(this, ResenasActivity::class.java)
            i.putExtra("SENDERO_ID", senderoId)
            i.putExtra("NOMBRE", nombre)
            startActivity(i)
        }

        // Foto para la reseña
        binding.btnFotoResena.setOnClickListener {
            elegirFotoResena.launch("image/*")
        }

        // Funciones solo para usuarios registrados
        if (uid != null) {
            configurarEstrellas()
            configurarFavorito()
            cargarCalificacionPrevia()
            cargarEstadoFavorito()
            binding.btnEnviarResena.setOnClickListener { enviarResena() }
        } else {
            binding.btnFavorito.setOnClickListener { pedirInicioSesion() }
            pintarFavorito()
            binding.btnEnviarResena.isEnabled = false
            binding.btnEnviarResena.text      = "🔒 Inicia sesión para reseñar"
            binding.btnFotoResena.isEnabled   = false
            deshabilitarEstrellas()
        }
    }

    // ── ESTRELLAS ─────────────────────────────────────────────────────────────
    private val listaEstrellas get() = listOf(
        binding.star1, binding.star2, binding.star3, binding.star4, binding.star5
    )

    private fun configurarEstrellas() {
        listaEstrellas.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                calificacionActual = index + 1
                pintarEstrellas(calificacionActual)
            }
        }
    }

    private fun deshabilitarEstrellas() {
        listaEstrellas.forEach { it.isEnabled = false }
    }

    private fun pintarEstrellas(cantidad: Int) {
        listaEstrellas.forEachIndexed { index, btn ->
            btn.text = if (index < cantidad) "★" else "☆"
            btn.setTextColor(
                if (index < cantidad)
                    getColor(android.R.color.holo_orange_light)
                else
                    getColor(android.R.color.darker_gray)
            )
        }
    }

    private fun cargarCalificacionPrevia() {
        if (uid == null || senderoId.isEmpty()) return
        db.child("usuarios/$uid/calificaciones/$senderoId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val v = snap.value?.toString()?.toIntOrNull() ?: 0
                    if (v > 0) { calificacionActual = v; pintarEstrellas(v) }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    // ── ENVIAR RESEÑA COMPLETA ────────────────────────────────────────────────
    private fun enviarResena() {
        if (uid == null || senderoId.isEmpty()) return

        val textoResena = binding.etResena.text.toString().trim()

        if (calificacionActual == 0) {
            Toast.makeText(this, "Selecciona al menos una estrella", Toast.LENGTH_SHORT).show()
            return
        }
        if (textoResena.isEmpty()) {
            Toast.makeText(this, "Escribe algo en tu reseña", Toast.LENGTH_SHORT).show()
            return
        }

        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val resena = mapOf(
            "uid"          to uid!!,
            "email"        to emailUsuario,
            "estrellas"    to calificacionActual,
            "texto"        to textoResena,
            "foto"         to fotoResenaBase64,
            "fecha"        to fecha,
            "timestamp"    to System.currentTimeMillis()
        )

        // Guardar reseña en Firebase
        db.child("resenas/$senderoId").push().setValue(resena)
            .addOnSuccessListener {
                // Guardar calificación del usuario y recalcular promedio
                db.child("calificaciones/$senderoId/$uid").setValue(calificacionActual)
                db.child("usuarios/$uid/calificaciones/$senderoId").setValue(calificacionActual)
                recalcularRating()

                Toast.makeText(this, "✅ Reseña publicada", Toast.LENGTH_SHORT).show()
                // Limpiar formulario
                binding.etResena.text.clear()
                fotoResenaBase64 = ""
                binding.imgFotoResena.visibility = android.view.View.GONE
                binding.tvFotoResenaEstado.text = "Sin foto (opcional)"
                cargarContadorResenas()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al publicar: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun recalcularRating() {
        db.child("calificaciones/$senderoId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    var suma = 0.0; var count = 0
                    for (child in snap.children) {
                        suma += child.value?.toString()?.toDoubleOrNull() ?: continue
                        count++
                    }
                    val promedio = if (count > 0) String.format("%.1f", suma / count) else "0.0"
                    db.child("senderos/$senderoId/rating").setValue(promedio)
                    binding.tvRating.text = promedio
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    // ── FOTO DE LA RESEÑA ─────────────────────────────────────────────────────
    private fun procesarFotoResena(uri: Uri) {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val escalado = escalarBitmap(bitmap, 600)
        val stream = ByteArrayOutputStream()
        escalado.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        fotoResenaBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        binding.imgFotoResena.setImageBitmap(escalado)
        binding.imgFotoResena.visibility = android.view.View.VISIBLE
        binding.tvFotoResenaEstado.text = "✅ Foto lista"
    }

    private fun escalarBitmap(bmp: Bitmap, maxPx: Int): Bitmap {
        val ratio = minOf(maxPx.toFloat() / bmp.width, maxPx.toFloat() / bmp.height)
        return if (ratio < 1f)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        else bmp
    }

    // ── CONTADOR DE RESEÑAS ───────────────────────────────────────────────────
    private fun cargarContadorResenas() {
        if (senderoId.isEmpty()) return
        db.child("resenas/$senderoId")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val total = snap.childrenCount
                    binding.btnVerResenas.text = "💬 Ver Reseñas ($total)"
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    // ── FAVORITOS ─────────────────────────────────────────────────────────────
    // La clave del sendero es única dentro de favoritos, así que no hay duplicados.
    private fun configurarFavorito() {
        binding.btnFavorito.setOnClickListener {
            if (uid == null || senderoId.isEmpty()) return@setOnClickListener
            esFavorito = !esFavorito
            if (esFavorito) {
                db.child("usuarios/$uid/favoritos/$senderoId").setValue(true)
                Toast.makeText(this, "Añadido a favoritos", Toast.LENGTH_SHORT).show()
            } else {
                db.child("usuarios/$uid/favoritos/$senderoId").removeValue()
                Toast.makeText(this, "Retirado de favoritos", Toast.LENGTH_SHORT).show()
            }
            pintarFavorito()
        }
    }

    private fun cargarEstadoFavorito() {
        if (uid == null || senderoId.isEmpty()) return
        db.child("usuarios/$uid/favoritos/$senderoId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    esFavorito = snap.exists()
                    pintarFavorito()
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    /** Corazón relleno y rojo si es favorito; vacío y verde si no. */
    private fun pintarFavorito() {
        val btn = binding.btnFavorito
        if (esFavorito) {
            btn.text = "♥  Guardado en favoritos"
            btn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.corazon)))
            btn.setTextColor(getColor(R.color.white))
        } else {
            btn.text = "♡  Guardar en favoritos"
            btn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.white)))
            btn.setTextColor(getColor(R.color.verde_primario))
        }
        // Pequeño "latido" al cambiar de estado
        btn.animate().scaleX(1.05f).scaleY(1.05f).setDuration(110).withEndAction {
            btn.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }.start()
    }

    private fun pedirInicioSesion() {
        AlertDialog.Builder(this)
            .setTitle("Inicia sesión para guardar")
            .setMessage("Necesitas una cuenta para guardar senderos en tus favoritos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Iniciar sesión") { _, _ ->
                val i = Intent(this, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(i)
            }
            .show()
    }
}
