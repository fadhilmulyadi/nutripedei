package com.surendramaran.yolov8tflite

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.surendramaran.yolov8tflite.databinding.ItemHistoryBinding
import java.util.Locale

class HistoryAdapter(
    private val historyList: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            binding.foodImage.setImageURI(Uri.parse(item.imageUri))
            binding.foodName.text = item.foodName
            binding.historyDate.text = item.date
            binding.caloriesInfo.text = String.format(Locale.US, "%.0f kkal", item.calories)
            binding.proteinInfo.text = String.format(Locale.US, "%.0fg", item.protein)
            binding.carbsInfo.text = String.format(Locale.US, "%.0fg", item.carbs)

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size
}