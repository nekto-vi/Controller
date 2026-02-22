package com.example.ev

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScenarioAdapter(
    private var scenarios: List<Scenario>,
    private val onItemClick: (Scenario) -> Unit
) : RecyclerView.Adapter<ScenarioAdapter.ScenarioViewHolder>() {

    class ScenarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(android.R.id.text1)
        val detailsText: TextView = itemView.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScenarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ScenarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScenarioViewHolder, position: Int) {
        val scenario = scenarios[position]
        holder.nameText.text = scenario.name

        // Convert room keys to localized display names
        val context = holder.itemView.context
        val roomNames = scenario.rooms.map { roomKey ->
            RoomMapper.keyToDisplayName(context, roomKey)
        }

        holder.detailsText.text = "${roomNames.joinToString()} - ${scenario.temperature}°C"

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