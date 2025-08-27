package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ScannerOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#4CAF50")
        strokeWidth = 12f
    }

    private val frameRect = RectF()
    private val cornerPath = Path()
    private val cornerRadius = 40f
    private val cornerLength = 120f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameRect.set(
            cornerPaint.strokeWidth / 2,
            cornerPaint.strokeWidth / 2,
            width - cornerPaint.strokeWidth / 2,
            height - cornerPaint.strokeWidth / 2
        )
        drawCorners(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        cornerPath.reset()
        // Top-left
        cornerPath.moveTo(frameRect.left, frameRect.top + cornerLength)
        cornerPath.lineTo(frameRect.left, frameRect.top + cornerRadius)
        cornerPath.quadTo(frameRect.left, frameRect.top, frameRect.left + cornerRadius, frameRect.top)
        cornerPath.lineTo(frameRect.left + cornerLength, frameRect.top)
        // Top-right
        cornerPath.moveTo(frameRect.right - cornerLength, frameRect.top)
        cornerPath.lineTo(frameRect.right - cornerRadius, frameRect.top)
        cornerPath.quadTo(frameRect.right, frameRect.top, frameRect.right, frameRect.top + cornerRadius)
        cornerPath.lineTo(frameRect.right, frameRect.top + cornerLength)
        // Bottom-left
        cornerPath.moveTo(frameRect.left, frameRect.bottom - cornerLength)
        cornerPath.lineTo(frameRect.left, frameRect.bottom - cornerRadius)
        cornerPath.quadTo(frameRect.left, frameRect.bottom, frameRect.left + cornerRadius, frameRect.bottom)
        cornerPath.lineTo(frameRect.left + cornerLength, frameRect.bottom)
        // Bottom-right
        cornerPath.moveTo(frameRect.right - cornerLength, frameRect.bottom)
        cornerPath.lineTo(frameRect.right - cornerRadius, frameRect.bottom)
        cornerPath.quadTo(frameRect.right, frameRect.bottom, frameRect.right, frameRect.bottom - cornerRadius)
        cornerPath.lineTo(frameRect.right, frameRect.bottom - cornerLength)

        canvas.drawPath(cornerPath, cornerPaint)
    }
}