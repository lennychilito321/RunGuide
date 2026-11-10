package com.lennychilito.runguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lennychilito.runguide.databinding.ActivityAjustesBinding

/** Pantalla de ajustes: tema e idioma. Disponible para todos, incluso invitados. */
class AjustesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAjustesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVolverAjustes.setOnClickListener { finish() }

        // Estado inicial según lo guardado
        binding.chipsTema.check(
            when (Ajustes.tema(this)) {
                Ajustes.TEMA_CLARO -> R.id.chipTemaClaro
                Ajustes.TEMA_OSCURO -> R.id.chipTemaOscuro
                else -> R.id.chipTemaAuto
            }
        )
        binding.chipsIdioma.check(
            if (Ajustes.idioma(this) == "en") R.id.chipIdiomaEn
            else if (Ajustes.idioma(this) == "es") R.id.chipIdiomaEs
            else if (resources.configuration.locales[0].language == "en") R.id.chipIdiomaEn
            else R.id.chipIdiomaEs
        )

        binding.chipsTema.setOnCheckedStateChangeListener { _, ids ->
            val tema = when (ids.firstOrNull()) {
                R.id.chipTemaClaro -> Ajustes.TEMA_CLARO
                R.id.chipTemaOscuro -> Ajustes.TEMA_OSCURO
                else -> Ajustes.TEMA_AUTO
            }
            if (tema != Ajustes.tema(this)) Ajustes.guardarTema(this, tema)
        }

        binding.chipsIdioma.setOnCheckedStateChangeListener { _, ids ->
            val codigo = if (ids.firstOrNull() == R.id.chipIdiomaEn) "en" else "es"
            if (codigo != Ajustes.idioma(this)) Ajustes.guardarIdioma(this, codigo)
        }
    }
}
