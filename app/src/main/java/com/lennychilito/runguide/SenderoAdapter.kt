package com.lennychilito.runguide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.lennychilito.runguide.databinding.ItemSenderoBinding

class SenderoAdapter(
    private val lista: MutableList<Sendero>,
    private val rol: String
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
        holder.binding.tvUbicacion.text = s.ubicacion
        holder.binding.tvDistancia.text = s.distancia
        holder.binding.tvRating.text = "⭐ ${s.rating}"

        // Cargar imagen desde URL con Glide
        if (s.imagenUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(s.imagenUrl)
                .placeholder(R.drawable.montana) // imagen local como fallback
                .into(holder.binding.imgSendero)
        } else {
            holder.binding.imgSendero.setImageResource(R.drawable.montana)
        }

        // Solo admin ve el botón eliminar
        if (rol == "admin") {
            holder.binding.btnEliminar.visibility = android.view.View.VISIBLE
            holder.binding.btnEliminar.setOnClickListener {
                // Eliminar por ID del documento en Firestore
                if (s.id.isNotEmpty()) {
                    FirebaseFirestore.getInstance()
                        .collection("senderos")
                        .document(s.id)
                        .delete()
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