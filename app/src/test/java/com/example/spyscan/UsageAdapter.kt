package com.example.spyscan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UsageAdapter(
    private var appUsageList: List<AppUsageData>,
    private val formatDuration: (Long) -> String,
    private val onItemClick: ((AppUsageData) -> Unit)? = null
) : RecyclerView.Adapter<UsageAdapter.ViewHolder>() {

    private var fullList: List<AppUsageData> = appUsageList

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val time: TextView = view.findViewById(R.id.appTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = appUsageList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = appUsageList[position]
        holder.name.text = item.appLabel
        holder.time.text = "Süre: ${formatDuration(item.durationMillis)} • Son: ${formatLast(item.lastUsedMillis)}"
        holder.icon.setImageDrawable(item.icon)
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    fun updateData(newList: List<AppUsageData>) {
        fullList = newList
        appUsageList = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        appUsageList = if (q.isBlank()) fullList
        else fullList.filter { it.appLabel.lowercase().contains(q) }
        notifyDataSetChanged()
    }

    private fun formatLast(ts: Long): String {
        if (ts <= 0) return "-"
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }
}