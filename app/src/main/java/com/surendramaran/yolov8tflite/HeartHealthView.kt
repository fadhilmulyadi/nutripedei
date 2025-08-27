package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import android.graphics.Color

class HeartHealthView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var score: Int = 0

    // Paint untuk latar belakang hati (abu-abu)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider_gray)
        style = Paint.Style.FILL
    }

    // Paint untuk progress hati (warnanya akan dinamis)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }

    // Paint untuk teks skor di tengah hati
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val heartPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupHeartPath(w.toFloat(), h.toFloat())
    }

    private fun setupHeartPath(width: Float, height: Float) {
        heartPath.reset()
        // Titik awal di bawah (runcing hati)
        heartPath.moveTo(width / 2, height / 4)
        // Kurva sisi kiri
        heartPath.cubicTo(width / 4, 0f, 0f, height / 2, width / 2, height / 5 * 4)
        // Kurva sisi kanan
        heartPath.cubicTo(width, height / 2, width / 4 * 3, 0f, width / 2, height / 4)
        heartPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Gambar latar belakang hati (abu-abu)
        canvas.drawPath(heartPath, backgroundPaint)

        // 2. Hitung tinggi progress (dari bawah ke atas)
        val progressHeight = height * (score / 100f)
        val topOfProgress = height - progressHeight

        // 3. Gambar progress hati (warna sesuai skor)
        canvas.save()
        canvas.clipRect(0f, topOfProgress, width.toFloat(), height.toFloat())
        canvas.drawPath(heartPath, progressPaint)
        canvas.restore()

        // 4. Gambar teks skor di tengah hati
        val textX = width / 2f
        val textY = height / 2f - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(score.toString(), textX, textY, textPaint)
    }

    /**
     * Fungsi untuk mengatur skor kesehatan dan memicu penggambaran ulang view.
     */
    fun setScore(newScore: Int) {
        score = newScore.coerceIn(0, 100)

        // Atur warna progress berdasarkan skor
        progressPaint.color = when (score) {
            in 0..15 -> Color.parseColor("#8B0000")   // Critical
            in 16..35 -> Color.parseColor("#E53935")  // Poor
            in 36..55 -> Color.parseColor("#FB8C00")  // Fair
            in 56..75 -> Color.parseColor("#CDDC39")  // Good
            in 76..90 -> Color.parseColor("#4CAF50")  // Very Good
            in 91..100 -> Color.parseColor("#00E676") // Excellent
            else -> Color.GRAY
        }

        invalidate() // Meminta view untuk digambar ulang
    }
}
