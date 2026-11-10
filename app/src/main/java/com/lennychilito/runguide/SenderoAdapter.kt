package com.lennychilito.runguide

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lennychilito.runguide.databinding.ItemSenderoBinding

class SenderoAdapter(
    private val lista: MutableList<Sendero>,
    private val rol: String,
    private val onVerRuta: (Sendero) -> Unit,            // abre DetalleSenderoActivity
    private val onCorazon: ((Sendero) -> Unit)? = null   // si existe, muestra el corazón para retirar
) : RecyclerView.Adapter<SenderoAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSenderoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSenderoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = lista[position]
        val b = holder.binding

        b.tvNombreSendero.text = s.nombre
        b.tvUbicacion.text     = "📍 ${s.tipoTerreno.ifEmpty { s.ubicacion }}"
        b.tvDistancia.text     = "${s.distancia} km"
        b.tvRating.text        = "★ ${s.rating}"
        b.tvDificultad.text    = s.dificultad

        val (fondo, texto) = colorDificultad(s.dificultad)
        b.tvDificultad.backgroundTintList = ColorStateList.valueOf(fondo)
        b.tvDificultad.setTextColor(texto)

        // Imagen con Base64 primero, luego URL, luego drawable local
        when {
            s.imagenBase64.isNotEmpty() -> {
                val bytes = android.util.Base64.decode(s.imagenBase64, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.imgSendero.setImageBitmap(bmp)
            }
            s.imagenUrl.isNotEmpty() -> {
                Glide.with(holder.itemView.context)
                    .load(s.imagenUrl)
                    .placeholder(R.drawable.montana)
                    .into(b.imgSendero)
            }
            else -> b.imgSendero.setImageResource(R.drawable.montana)
        }

        // Toda la tarjeta abre el detalle y responde al toque
        b.root.setOnClickListener { onVerRuta(s) }
        b.root.efectoPresion()
        b.btnVerRuta.setOnClickListener { onVerRuta(s) }

        // Corazón: solo en la lista de favoritos
        if (onCorazon != null) {
            b.tvCorazon.visibility = View.VISIBLE
            b.tvCorazon.setOnClickListener { onCorazon.invoke(s) }
        } else {
            b.tvCorazon.visibility = View.GONE
        }

        // Menú ⋮ — solo admin (y no en la lista de favoritos)
        if (rol == "admin" && onCorazon == null) {
            b.btnMenuAdmin.visibility = View.VISIBLE
            b.btnMenuAdmin.setOnClickListener { mostrarMenuAdmin(it, s) }
        } else {
            b.btnMenuAdmin.visibility = View.GONE
        }
    }

    private fun mostrarMenuAdmin(anchor: View, s: Sendero) {
        val ctx = anchor.context
        PopupMenu(ctx, anchor).apply {
            menu.add(0, 1, 0, "✏️  Editar sendero")
            menu.add(0, 2, 1, "💬  Gestionar reseñas")
            menu.add(0, 3, 2, "🗑  Eliminar sendero")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> ctx.startActivity(
                        Intent(ctx, AdminActivity::class.java).putExtra("SENDERO_ID", s.id)
                    )
                    2 -> ctx.startActivity(
                        Intent(ctx, ResenasActivity::class.java)
                            .putExtra("SENDERO_ID", s.id)
                            .putExtra("NOMBRE", s.nombre)
                    )
                    3 -> confirmarEliminar(ctx, s)
                }
                true
            }
            show()
        }
    }

    private fun confirmarEliminar(ctx: Context, s: Sendero) {
        AlertDialog.Builder(ctx)
            .setTitle("Eliminar sendero")
            .setMessage("¿Eliminar \"${s.nombre}\" con todas sus reseñas? No se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                Rtdb.eliminarSendero(s.id) { ok ->
                    Toast.makeText(
                        ctx,
                        if (ok) "Sendero eliminado" else "No se pudo eliminar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
}
