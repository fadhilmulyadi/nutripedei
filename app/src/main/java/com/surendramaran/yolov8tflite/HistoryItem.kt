package com.surendramaran.yolov8tflite

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class HistoryItem(
    val imageUri: String,
    val foodName: String,
    val date: String,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val boundingBoxes: List<BoundingBox>,
    val qualityResults: List<String>? = null
) : Parcelable