package com.lennychilito.runguide

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * Inicia sesión (o crea la cuenta si es la primera vez) con Google usando
 * Credential Manager y la entrega a Firebase Authentication.
 * [alTerminar] se ejecuta solo cuando Firebase confirma la sesión.
 */
fun AppCompatActivity.iniciarConGoogle(alTerminar: () -> Unit) {
    val activity = this
    val opcion = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(opcion).build()

    lifecycleScope.launch {
        try {
            val resultado = CredentialManager.create(activity).getCredential(activity, request)
            val credencial = resultado.credential

            if (credencial is CustomCredential &&
                credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credencial.data).idToken
                FirebaseAuth.getInstance()
                    .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .addOnSuccessListener {
                        Rtdb.guardarPerfil()
                        alTerminar()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(activity, "No se pudo entrar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(activity, "Tipo de credencial no compatible", Toast.LENGTH_LONG).show()
            }
        } catch (e: GetCredentialCancellationException) {
            // El usuario cerró el selector de cuentas: no es un error.
        } catch (e: GetCredentialException) {
            Toast.makeText(activity, "Google: ${e.message ?: "no se pudo iniciar sesión"}", Toast.LENGTH_LONG).show()
        } catch (e: GoogleIdTokenParsingException) {
            Toast.makeText(activity, "Respuesta de Google inválida", Toast.LENGTH_LONG).show()
        }
    }
}

/** Abre la lista de senderos con el rol correcto y limpia la pila de pantallas. */
fun Activity.irAlInicio() {
    val intent = Intent(this, SenderosActivity::class.java)
        .putExtra("ROL", if (Rtdb.esAdmin()) "admin" else "usuario")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    startActivity(intent)
    finish()
}
