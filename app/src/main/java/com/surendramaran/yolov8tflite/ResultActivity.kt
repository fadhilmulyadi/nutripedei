package com.surendramaran.yolov8tflite

import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.surendramaran.yolov8tflite.databinding.ActivityResultBinding
import com.surendramaran.yolov8tflite.databinding.ItemNutrientSummaryBinding
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var foodResultAdapter: FoodResultAdapter
    private var detectedFoodList = mutableListOf<FoodNutrition>()
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        val boundingBoxes = intent.getParcelableArrayListExtra<BoundingBox>(EXTRA_DETECTED_FOODS)
        val qualityResults = intent.getStringArrayListExtra(EXTRA_QUALITY_RESULTS)

        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString)
            binding.backgroundImageView.setImageURI(imageUri)
        }

        if (boundingBoxes != null) {
            var qualityIndex = 0
            detectedFoodList = boundingBoxes.mapNotNull { box ->
                val foodNutrition = NutritionData.data[box.clsName]?.copy()
//                if (box.clsName == "Jeruk" && qualityResults != null && qualityIndex < qualityResults.size) {
//                    foodNutrition?.quality = qualityResults[qualityIndex]
//                    Log.d("ResultActivityDebug", "Food: ${box.clsName}, Quality from BoundingBox: ${box.quality}")
//                    qualityIndex++
//                }
                if (qualityResults != null && qualityIndex < qualityResults.size) {
                    foodNutrition?.quality = qualityResults[qualityIndex]
                    Log.d("ResultActivityDebug", "Food: ${box.clsName}, Quality: ${foodNutrition?.quality}")
                    qualityIndex++
                }

                foodNutrition
            }.toMutableList()
        }

        if (detectedFoodList.isEmpty()) {
            Toast.makeText(this, "Tidak ada data nutrisi untuk makanan yang terdeteksi.", Toast.LENGTH_LONG).show()
        }

        setupRecyclerView(qualityResults)
        updateUI()

        binding.saveButton.setOnClickListener {
            saveToHistory()
        }
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun saveToHistory() {
        if (imageUri == null || detectedFoodList.isEmpty()) {
            Toast.makeText(this, "Tidak ada data untuk disimpan", Toast.LENGTH_SHORT).show()
            return
        }

        val totalCalories = detectedFoodList.sumOf { it.calories.toDouble() }.toFloat()
        val totalCarbs = detectedFoodList.sumOf { it.carbohydrates.toDouble() }.toFloat()
        val totalProtein = detectedFoodList.sumOf { it.protein.toDouble() }.toFloat()

        val foodName = detectedFoodList.joinToString(", ") { it.label }
        val currentDate = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

        val originalBoundingBoxes = intent.getParcelableArrayListExtra<BoundingBox>(EXTRA_DETECTED_FOODS) ?: emptyList()
        val qualityResults = intent.getStringArrayListExtra(EXTRA_QUALITY_RESULTS) ?: emptyList<String>()

        val historyItem = HistoryItem(
            imageUri = imageUri.toString(),
            foodName = foodName,
            date = currentDate,
            calories = totalCalories,
            protein = totalProtein,
            carbs = totalCarbs,
            boundingBoxes = originalBoundingBoxes,
            qualityResults = qualityResults
        )

        HistoryManager.saveHistoryItem(this, historyItem)
        Toast.makeText(this, "Berhasil disimpan ke riwayat", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupRecyclerView(qualityResults: ArrayList<String>?) {
        foodResultAdapter = FoodResultAdapter(
            foodList = detectedFoodList,
            onPortionChanged = {
                updateUI()
            },
            onItemRemoved = {
                updateUI()
            }
        )
        binding.foodRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ResultActivity)
            adapter = foodResultAdapter
        }
    }

    private fun updateUI() {
        val totalCalories = detectedFoodList.sumOf { it.calories.toDouble() }.toFloat()
        val totalCarbs = detectedFoodList.sumOf { it.carbohydrates.toDouble() }.toFloat()
        val totalProtein = detectedFoodList.sumOf { it.protein.toDouble() }.toFloat()
        val totalFat = detectedFoodList.sumOf { it.fat.toDouble() }.toFloat()

        binding.totalCaloriesTextView.text = formatNumber(totalCalories, "")
        binding.totalCarbsTextView.text = formatNumber(totalCarbs, "g")
        binding.totalProteinTextView.text = formatNumber(totalProtein, "g")
        binding.totalFatTextView.text = formatNumber(totalFat, "g")

        val healthScoreResult = HealthScoreCalculator.calculate(detectedFoodList)
        binding.healthScoreView.setScore(healthScoreResult.totalScore)
        binding.healthScoreClassification.text = healthScoreResult.classification
        binding.healthScoreSuggestion.text = healthScoreResult.suggestions.firstOrNull() ?: ""

        binding.foodTitleTextView.text = if (detectedFoodList.isEmpty()) {
            "Tidak Ada Makanan Terdeteksi"
        } else {
            detectedFoodList.joinToString(", ") { it.label }
        }
    }

    private fun updateNutrientView(
        nutrientBinding: ItemNutrientSummaryBinding,
        name: String,
        value: Float,
        unit: String
    ) {
        nutrientBinding.nutrientNameTextView.text = name
        nutrientBinding.nutrientUnitTextView.text = unit

        val animator = ValueAnimator.ofFloat(0f, value)
        animator.duration = 1000
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            nutrientBinding.nutrientValueTextView.text = String.format("%.1f", animatedValue)
        }
        animator.start()
    }

    private fun formatNumber(value: Float, unit: String): String {
        return if (value % 1 == 0f) {
            String.format("%.0f %s", value, unit)
        } else {
            String.format("%.1f %s", value, unit)
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_DETECTED_FOODS = "detected_foods"
        const val EXTRA_QUALITY_RESULTS = "quality_results"
    }
}
