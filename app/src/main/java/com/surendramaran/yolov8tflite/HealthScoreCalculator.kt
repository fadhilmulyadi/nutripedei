package com.surendramaran.yolov8tflite

import kotlin.math.abs

object HealthScoreCalculator {

    // Kualitas bahan (poin)
    private val qualityPoints = mapOf(
        "capcay" to 20, "tumis buncis" to 20, "tumis kangkung" to 20,
        "jeruk" to 20, "pisang" to 20, "semangka" to 20,
        "telur rebus" to 15, "tahu" to 15, "tempe goreng" to 15,
        "ayam goreng" to 5, "telur dadar" to 5,
        "nasi putih" to 0,
        "sambal" to -20
    )

    private val vegetableAndFruits = setOf(
        "capcay", "tumis buncis", "tumis kangkung", "jeruk", "pisang", "semangka"
    )

    data class HealthScoreResult(
        val totalScore: Int,
        val calorieScore: Float,
        val macroScore: Float,
        val qualityScore: Float,
        val microScore: Float,
        val classification: String,
        val suggestions: List<String>
    )

    fun calculate(foods: List<FoodNutrition>): HealthScoreResult {
        if (foods.isEmpty()) {
            return HealthScoreResult(0, 0f, 0f, 0f, 0f, "N/A", emptyList())
        }

        val totalCalories = foods.sumOf { it.calories.toDouble() }.toFloat()
        val totalCarbs = foods.sumOf { it.carbohydrates.toDouble() }.toFloat()
        val totalProtein = foods.sumOf { it.protein.toDouble() }.toFloat()
        val totalFat = foods.sumOf { it.fat.toDouble() }.toFloat()

        // 1. Skor Kalori (S_kalori)
        val calorieScore = 100 - (abs(totalCalories - 500) / 500) * 100
        val sKalori = calorieScore.coerceIn(0f, 100f)

        // 2. Skor Makronutrien (S_makro)
        val totalEnergyFromMacros = (totalCarbs * 4) + (totalProtein * 4) + (totalFat * 9)
        val percentageCarbs = if (totalEnergyFromMacros > 0) (totalCarbs * 4 / totalEnergyFromMacros) * 100 else 0f
        val percentageProtein = if (totalEnergyFromMacros > 0) (totalProtein * 4 / totalEnergyFromMacros) * 100 else 0f
        val percentageFat = if (totalEnergyFromMacros > 0) (totalFat * 9 / totalEnergyFromMacros) * 100 else 0f

        val macroScore = 100 - ((abs(percentageCarbs - 55) / 55) +
                (abs(percentageProtein - 15) / 15) +
                (abs(percentageFat - 30) / 30)) * 33
        val sMakro = macroScore.coerceIn(0f, 100f)

        // 3. Skor Kualitas (S_kualitas)
        val totalQualityPoints = foods.sumOf { qualityPoints[it.label.lowercase()] ?: 0 }
        val avgQualityPoints = totalQualityPoints.toFloat() / foods.size
        // Normalisasi skor kualitas dari rentang [-20, 20] ke [0, 100]
        val qualityScore = ((avgQualityPoints + 20) / 40) * 100
        val sKualitas = qualityScore.coerceIn(0f, 100f)

        // 4. Skor Mikronutrien (S_mikro)
        val hasVegetableOrFruit = foods.any { vegetableAndFruits.contains(it.label.lowercase()) }
        val sMikro = if (hasVegetableOrFruit) 100f else 50f

        // Final Score
        val healthScore = (0.3 * sKalori) + (0.3 * sMakro) + (0.3 * sKualitas) + (0.1 * sMikro)
        val finalScore = healthScore.toInt().coerceIn(0, 100)

        // Klasifikasi dan Saran
        val classification = getClassification(finalScore)
        val suggestions = generateSuggestions(sKalori, sMakro, sKualitas, sMikro)

        return HealthScoreResult(
            totalScore = finalScore,
            calorieScore = sKalori,
            macroScore = sMakro,
            qualityScore = sKualitas,
            microScore = sMikro,
            classification = classification,
            suggestions = suggestions
        )
    }

    private fun getClassification(score: Int): String {
        return when (score) {
            in 81..100 -> "Sangat Sehat"
            in 61..80 -> "Cukup Sehat"
            in 41..60 -> "Kurang Sehat"
            else -> "Tidak Sehat"
        }
    }

    private fun generateSuggestions(sKalori: Float, sMakro: Float, sKualitas: Float, sMikro: Float): List<String> {
        val suggestions = mutableListOf<String>()
        if (sKalori < 60) {
            suggestions.add("Jumlah kalori belum ideal. Sesuaikan porsi agar mendekati 500 kkal untuk sekali makan.")
        }
        if (sMakro < 60) {
            suggestions.add("Keseimbangan makronutrien (karbo, protein, lemak) perlu diperbaiki.")
        }
        if (sKualitas < 60) {
            suggestions.add("Pilihan bahan makanan kurang sehat. Pertimbangkan mengganti makanan olahan/gorengan dengan yang direbus atau dikukus.")
        }
        if (sMikro < 100) {
            suggestions.add("Tambahkan sayur atau buah untuk meningkatkan asupan vitamin, mineral, dan serat.")
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Komposisi makanan Anda sudah sangat baik. Pertahankan!")
        }
        return suggestions
    }
}