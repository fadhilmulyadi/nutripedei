package com.surendramaran.yolov8tflite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.surendramaran.yolov8tflite.databinding.ItemFoodResultNewBinding

class FoodResultAdapter(
    private val foodList: MutableList<FoodNutrition>,
    private val onPortionChanged: () -> Unit,
    private val onItemRemoved: () -> Unit
) : RecyclerView.Adapter<FoodResultAdapter.FoodViewHolder>() {

    // Initial nutrition data per 100g
    private val baseNutritionMap = mutableMapOf<Int, FoodNutrition>()

    init {
        foodList.forEachIndexed { index, food ->
            // Store original values (assuming they are per 100g)
            baseNutritionMap[index] = food.copy()
        }
    }

    inner class FoodViewHolder(val binding: ItemFoodResultNewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val binding = ItemFoodResultNewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FoodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        val foodItem = foodList.getOrNull(position) ?: return

        with(holder.binding) {
            foodNameTextView.text = foodItem.label

            if ((foodItem.label == "Pisang" || foodItem.label == "Jeruk" || foodItem.label == "Semangka") && foodItem.quality != "N/A") {
                foodQualityTextView.visibility = View.VISIBLE
                foodQualityTextView.text = "Kondisi: ${foodItem.quality}"
            } else {
                foodQualityTextView.visibility = View.GONE
            }

            updateCollapsedView(this, foodItem)
            updateExpandedDetails(this, foodItem) // Update details saat bind

            val isExpanded = foodItem.isExpanded
            expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
            expandIcon.rotation = if (isExpanded) 180f else 0f

            collapsedView.setOnClickListener {
                foodItem.isExpanded = !foodItem.isExpanded
                notifyItemChanged(holder.adapterPosition)
            }

            // Expanded view logic
            if (isExpanded) {
                // Hapus listener lama untuk menghindari panggilan berulang
                portionSeekBar.setOnSeekBarChangeListener(null)
                val textWatcher = portionEditText.getTag() as? android.text.TextWatcher
                textWatcher?.let { portionEditText.removeTextChangedListener(it) }

                portionEditText.setText(foodItem.weight.toInt().toString())
                portionEditText.setSelection(portionEditText.text.length)
                portionSeekBar.progress = foodItem.weight.toInt()

                portionSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            portionEditText.setText(progress.toString())
                            updateNutrition(holder, progress.toFloat())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                val newTextWatcher = portionEditText.addTextChangedListener {
                    if (portionEditText.isFocused) {
                        val weight = it.toString().toFloatOrNull() ?: 0f
                        if (weight <= portionSeekBar.max) {
                            portionSeekBar.progress = weight.toInt()
                            updateNutrition(holder, weight)
                        }
                    }
                }
                portionEditText.setTag(newTextWatcher)
            }
        }
    }


    private fun updateNutrition(holder: FoodViewHolder, newWeight: Float) {
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val foodItem = foodList[position]
            val baseNutrition = baseNutritionMap[position] ?: return

            val ratio = newWeight / 100.0f

            foodItem.weight = newWeight
            foodItem.calories = baseNutrition.calories * ratio
            foodItem.carbohydrates = baseNutrition.carbohydrates * ratio
            foodItem.protein = baseNutrition.protein * ratio
            foodItem.fat = baseNutrition.fat * ratio

            updateCollapsedView(holder.binding, foodItem)
            updateExpandedDetails(holder.binding, foodItem)

            onPortionChanged()
        }
    }

    private fun updateCollapsedView(binding: ItemFoodResultNewBinding, foodItem: FoodNutrition) {
        binding.foodCalorieTextView.text = "${String.format("%.1f", foodItem.calories)} kcal"
        binding.foodWeightTextView.text = "${String.format("%.0f", foodItem.weight)} g"
    }

    private fun updateExpandedDetails(binding: ItemFoodResultNewBinding, foodItem: FoodNutrition) {
        binding.detailCarbsTextView.text = "Karbo: ${String.format("%.1f", foodItem.carbohydrates)}g"
        binding.detailProteinTextView.text = "Protein: ${String.format("%.1f", foodItem.protein)}g"
        binding.detailFatTextView.text = "Lemak: ${String.format("%.1f", foodItem.fat)}g"

        // --- LOGIKA BARU UNTUK BAR NUTRISI ---
        val calFromCarbs = foodItem.carbohydrates * 4
        val calFromProtein = foodItem.protein * 4
        val calFromFat = foodItem.fat * 9
        val totalMacroCalories = calFromCarbs + calFromProtein + calFromFat

        val carbPercent = if (totalMacroCalories > 0) (calFromCarbs / totalMacroCalories) * 100 else 0f
        val proteinPercent = if (totalMacroCalories > 0) (calFromProtein / totalMacroCalories) * 100 else 0f
        val fatPercent = if (totalMacroCalories > 0) (calFromFat / totalMacroCalories) * 100 else 0f

        (binding.carbBar.layoutParams as LinearLayout.LayoutParams).weight = carbPercent
        (binding.proteinBar.layoutParams as LinearLayout.LayoutParams).weight = proteinPercent
        (binding.fatBar.layoutParams as LinearLayout.LayoutParams).weight = fatPercent

        // Meminta container untuk menggambar ulang layout bar
        binding.macroBarContainer.requestLayout()
    }


    fun removeItem(position: Int) {
        if (position >= 0 && position < foodList.size) {
            foodList.removeAt(position)
            // Menggeser baseNutritionMap untuk menjaga konsistensi
            val keysToUpdate = baseNutritionMap.keys.filter { it > position }
            keysToUpdate.forEach { key ->
                baseNutritionMap[key - 1] = baseNutritionMap[key]!!
                baseNutritionMap.remove(key)
            }
            baseNutritionMap.remove(position) // Hapus yang asli
            notifyItemRemoved(position)
            onItemRemoved()
        }
    }


    override fun getItemCount(): Int = foodList.size
}