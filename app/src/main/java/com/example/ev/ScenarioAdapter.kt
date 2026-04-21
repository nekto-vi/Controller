package com.example.ev

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class ScenarioAdapter(
    private var scenarios: List<Scenario>,
    private val onItemClick: (Scenario) -> Unit
) : RecyclerView.Adapter<ScenarioAdapter.ScenarioViewHolder>() {

    private val imageOptions = RequestOptions()
        .centerCrop()
        .placeholder(android.R.drawable.ic_menu_gallery)
        .error(android.R.drawable.ic_menu_gallery)

    class ScenarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.scenarioNameText)
        val detailsText: TextView = itemView.findViewById(R.id.scenarioDetailsText)
        val imagePreview: ImageView = itemView.findViewById(R.id.scenarioImagePreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScenarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scenario, parent, false)
        return ScenarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScenarioViewHolder, position: Int) {
        val scenario = scenarios[position]
        holder.nameText.text = scenario.name

        val context = holder.itemView.context
        val roomNames = scenario.rooms.map { roomKey ->
            RoomMapper.keyToDisplayName(context, roomKey)
        }

        holder.detailsText.text = "${roomNames.joinToString()} - ${scenario.temperature}°C"
        val imageUrl = scenario.imageUrl
        if (imageUrl.isNullOrBlank()) {
            holder.imagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            Glide.with(holder.itemView)
                .load(imageUrl)
                .apply(imageOptions)
                .into(holder.imagePreview)
        }

        holder.itemView.setOnClickListener {
            onItemClick(scenario)
        }
    }

    override fun getItemCount() = scenarios.size

    fun updateScenarios(newScenarios: List<Scenario>) {
        scenarios = newScenarios
        notifyDataSetChanged()
    }
}
