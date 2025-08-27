package com.surendramaran.yolov8tflite

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HistoryManager {
    private const val PREFS_NAME = "NutriScanHistory"
    private const val HISTORY_KEY = "history_list"

    fun saveHistoryItem(context: Context, item: HistoryItem) {
        val history = getHistory(context).toMutableList()
        history.add(0, item) // Tambahkan item baru di paling atas
        saveHistoryList(context, history)
    }

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(HISTORY_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun saveHistoryList(context: Context, list: List<HistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val json = Gson().toJson(list)
        editor.putString(HISTORY_KEY, json)
        editor.apply()
    }
}