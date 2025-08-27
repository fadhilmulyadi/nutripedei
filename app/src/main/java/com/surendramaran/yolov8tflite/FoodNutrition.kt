package com.surendramaran.yolov8tflite

data class FoodNutrition(
    val label: String,
    var calories: Float,
    var carbohydrates: Float,
    var protein: Float,
    var fat: Float,
    var weight: Float = 100f,
    var isExpanded: Boolean = false,
    var quality: String = "N/A"
)