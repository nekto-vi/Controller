package com.example.ev

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RoomAdapter(
    private val items: MutableList<String>,
    private val getDisplayName: (String) -> String,
    private val onRemoveClick: (String) -> Unit
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val roomName: TextView = itemView.findViewById(R.id.roomNameText)
            ?: throw IllegalStateException("roomNameText not found in layout")
        val removeButton: TextView = itemView.findViewById(R.id.removeButton)
            ?: throw IllegalStateException("removeButton not found in layout")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val key = items[position]
        val displayName = getDisplayName(key)

        holder.roomName.text = displayName
        holder.removeButton.setOnClickListener {
            onRemoveClick(key)
        }
    }

    override fun getItemCount() = items.size
}