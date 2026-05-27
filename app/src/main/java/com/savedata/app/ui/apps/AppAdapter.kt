package com.savedata.app.ui.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.savedata.app.databinding.ItemAppBinding
import com.savedata.app.util.AppInfoLoader

class AppAdapter(
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit
) : ListAdapter<AppListItem, AppAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppListItem) {
            binding.appIcon.setImageDrawable(item.icon)
            binding.appName.text = item.appName
            binding.packageName.text = item.packageName

            val fmt = AppInfoLoader::formatBytes
            binding.trafficWifi.text   = "📶 ↓${fmt(item.rxWifi)}  ↑${fmt(item.txWifi)}"
            binding.trafficMobile.text = "📱 ↓${fmt(item.rxMobile)}  ↑${fmt(item.txMobile)}"
            binding.trafficTotal.text  = fmt(item.totalBytes)

            binding.blockSwitch.setOnCheckedChangeListener(null)
            binding.blockSwitch.isChecked = item.isBlocked
            binding.blockSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.packageName, isChecked)
            }

            val statusColor = if (item.isBlocked)
                itemView.context.getColor(android.R.color.holo_red_light)
            else
                itemView.context.getColor(android.R.color.holo_green_light)
            binding.statusDot.setColorFilter(statusColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppListItem>() {
            override fun areItemsTheSame(old: AppListItem, new: AppListItem) =
                old.packageName == new.packageName
            override fun areContentsTheSame(old: AppListItem, new: AppListItem) =
                old == new
        }
    }
}
