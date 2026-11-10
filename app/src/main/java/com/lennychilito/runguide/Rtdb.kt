package com.lennychilito.runguide

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/** Acceso único a la Realtime Database del proyecto. */
object Rtdb {
    private const val URL = "https://runner-app-f6214-default-rtdb.firebaseio.com/"

    // Cuenta administradora (UID de Firebase Console > Authentication)
    const val ADMIN_UID = "dikzbNLdGGNXx8H6Rt6Ri7clfrd2"
    const val ADMIN_EMAIL = "Lennychilito321@gmail.com"

    val ref: DatabaseReference
        get() = FirebaseDatabase.getInstance(URL).reference

    /** true si la sesión actual es la del administrador. */
    fun esAdmin(): Boolean {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        return u.uid == ADMIN_UID || u.email.equals(ADMIN_EMAIL, ignoreCase = true)
    }

    /** Guarda el email del usuario para que el admin pueda identificarlo. */
    fun guardarPerfil() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val email = u.email ?: return
        ref.child("usuarios/${u.uid}/email").setValue(email)
    }

    /** Borra un sendero junto con sus reseñas y calificaciones. */
    fun eliminarSendero(id: String, onListo: (Boolean) -> Unit = {}) {
        if (id.isEmpty()) { onListo(false); return }
        val cambios = mapOf<String, Any?>(
            "senderos/$id" to null,
            "resenas/$id" to null,
            "calificaciones/$id" to null
        )
        ref.updateChildren(cambios).addOnCompleteListener { onListo(it.isSuccessful) }
    }

    /**
     * Borra una reseña y deja consistentes las calificaciones y el promedio:
     * cada usuario cuenta con la estrella de su reseña más reciente.
     */
    fun eliminarResena(senderoId: String, resenaId: String, onListo: (Boolean) -> Unit = {}) {
        if (senderoId.isEmpty() || resenaId.isEmpty()) { onListo(false); return }
        ref.child("resenas/$senderoId/$resenaId").removeValue().addOnCompleteListener { t ->
            if (!t.isSuccessful) { onListo(false); return@addOnCompleteListener }
            recalcularDesdeResenas(senderoId, onListo)
        }
    }

    private fun recalcularDesdeResenas(senderoId: String, onListo: (Boolean) -> Unit) {
        ref.child("resenas/$senderoId").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                // Última calificación por usuario
                val porUsuario = mutableMapOf<String, Pair<Long, Int>>()
                for (r in snap.children) {
                    val uid = r.child("uid").value?.toString() ?: continue
                    val estrellas = r.child("estrellas").value?.toString()?.toIntOrNull() ?: continue
                    val ts = r.child("timestamp").value?.toString()?.toLongOrNull() ?: 0L
                    val previo = porUsuario[uid]
                    if (previo == null || ts >= previo.first) porUsuario[uid] = ts to estrellas
                }
                val califs = porUsuario.mapValues { it.value.second }
                val promedio = if (califs.isEmpty()) "0.0"
                else String.format(Locale.US, "%.1f", califs.values.average())

                ref.updateChildren(
                    mapOf<String, Any?>(
                        "calificaciones/$senderoId" to califs.ifEmpty { null },
                        "senderos/$senderoId/rating" to promedio
                    )
                ).addOnCompleteListener { onListo(it.isSuccessful) }
            }

            override fun onCancelled(error: DatabaseError) = onListo(false)
        })
    }
}
