package com.lennychilito.runguide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.lennychilito.runguide.databinding.ItemResenaBinding

class ResenaAdapter(
    private val lista: List<Resena>,
    private val onEliminar: ((Resena) -> Unit)? = null   // si existe, muestra el menú ⋮ (admin)
) : RecyclerView.Adapter<ResenaAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemResenaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResenaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = lista[position]

        // Email abreviado: solo la parte antes del @
        val usuario = r.email.substringBefore("@")
        holder.binding.tvUsuarioResena.text = usuario
        holder.binding.tvAvatar.text = usuario.firstOrNull()?.uppercase() ?: "U"

        // Estrellas como texto
        val n = r.estrellas.coerceIn(0, 5)
        holder.binding.tvEstrellas.text = "★".repeat(n) + "☆".repeat(5 - n)

        holder.binding.tvTextoResena.text = r.texto
        holder.binding.tvFechaResena.text = r.fecha

        // Foto de la reseña (si tiene)
        if (r.foto.isNotEmpty()) {
            try {
                val bytes = android.util.Base64.decode(r.foto, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.binding.imgResena.setImageBitmap(bmp)
                holder.binding.imgResena.visibility = View.VISIBLE
            } catch (e: Exception) {
                holder.binding.imgResena.visibility = View.GONE
            }
        } else {
            holder.binding.imgResena.visibility = View.GONE
        }

        // Menú ⋮ — solo admin
        val menu = holder.binding.btnMenuResena
        if (onEliminar != null) {
            menu.visibility = View.VISIBLE
            menu.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    this.menu.add(0, 1, 0, anchor.context.getString(R.string.menu_eliminar_resena))
                    setOnMenuItemClickListener { onEliminar(r); true }
                    show()
                }
            }
        } else {
            menu.visibility = View.GONE
        }
    }
}
