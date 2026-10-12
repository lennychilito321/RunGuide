package com.lennychilito.runguide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.FirebaseDatabase
import com.lennychilito.runguide.databinding.ItemSenderoBinding

class SenderoAdapter(
    private val lista: MutableList<Sendero>,
    private val rol: String,
    private val onVerRuta: (Sendero) -> Unit   // callback → abre DetalleSenderoActivity
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

        holder.binding.tvNombreSendero.text = s.nombre
        holder.binding.tvUbicacion.text     = "📍 ${s.tipoTerreno.ifEmpty { s.ubicacion }}"
        holder.binding.tvDistancia.text     = "${s.distancia} km"
        holder.binding.tvRating.text        = "⭐ ${s.rating} (0)"
        holder.binding.tvDificultad.text    = s.dificultad

        // Imagen con Base64 primero, luego URL, luego drawable local
        when {
            s.imagenBase64.isNotEmpty() -> {
                val bytes = android.util.Base64.decode(s.imagenBase64, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.binding.imgSendero.setImageBitmap(bmp)
            }
            s.imagenUrl.isNotEmpty() -> {
                Glide.with(holder.itemView.context)
                    .load(s.imagenUrl)
                    .placeholder(R.drawable.montana)
                    .into(holder.binding.imgSendero)
            }
            else -> holder.binding.imgSendero.setImageResource(R.drawable.montana)
        }

        // Botón Ver Ruta
        holder.binding.btnVerRuta.setOnClickListener { onVerRuta(s) }

        // Eliminar — solo admin
        if (rol == "admin") {
            holder.binding.btnEliminar.visibility = android.view.View.VISIBLE
            holder.binding.btnEliminar.setOnClickListener {
                if (s.id.isNotEmpty()) {
                    FirebaseDatabase
                        .getInstance("https://runner-app-f6214-default-rtdb.firebaseio.com/")
                        .getReference("senderos")
                        .child(s.id)
                        .removeValue()
                        .addOnSuccessListener {
                            val idx = lista.indexOf(s)
                            if (idx != -1) {
                                lista.removeAt(idx)
                                notifyItemRemoved(idx)
                            }
                        }
                }
            }
        } else {
            holder.binding.btnEliminar.visibility = android.view.View.GONE
        }
    }
}
