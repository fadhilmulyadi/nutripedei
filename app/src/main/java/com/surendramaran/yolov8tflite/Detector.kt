package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    // Interpreter untuk model utama (deteksi objek)
    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()

    // Interpreter untuk model kualitas
    private var interpreterPisang: Interpreter? = null
    private var labelsPisang = listOf<String>()
    private var interpreterJeruk: Interpreter? = null
    private var labelsJeruk = listOf<String>()
    private var interpreterSemangka: Interpreter? = null
    private var labelsSemangka = listOf<String>()


    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    // ImageProcessor untuk model utama
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        setup()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        interpreterPisang?.close()
        interpreterPisang = null
        interpreterJeruk?.close()
        interpreterJeruk = null
        interpreterSemangka?.close()
        interpreterSemangka = null
    }

    private fun setup() {
        try {
            // 1. Setup Model Deteksi Utama (YOLO)
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options().apply { numThreads = 4 }
            interpreter = Interpreter(model, options)

            val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
            val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]
            numChannel = outputShape[1]
            numElements = outputShape[2]
            labels = FileUtil.loadLabels(context, labelPath)

            // 2. Setup Model Kualitas Pisang
            val modelPisang = FileUtil.loadMappedFile(context, "pisang_quality.tflite")
            interpreterPisang = Interpreter(modelPisang, Interpreter.Options().apply { numThreads = 4 })
            labelsPisang = FileUtil.loadLabels(context, "pisang_labels.txt")

            // 3. Setup Model Kualitas Jeruk (BARU)
            val modelJeruk = FileUtil.loadMappedFile(context, "jeruk_quality.tflite")
            interpreterJeruk = Interpreter(modelJeruk, Interpreter.Options().apply { numThreads = 4 })
            labelsJeruk = FileUtil.loadLabels(context, "jeruk_labels.txt")

            // 4. Setup Model Kualitas Semangka (BARU)
            val modelSemangka = FileUtil.loadMappedFile(context, "semangka_quality.tflite")
            interpreterSemangka = Interpreter(modelSemangka, Interpreter.Options().apply { numThreads = 4 })
            labelsSemangka = FileUtil.loadLabels(context, "semangka_labels.txt")

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun detect(image: Bitmap, imageUri: Uri? = null) {
        if (interpreter == null) return

        val inferenceTime = SystemClock.uptimeMillis()
        val resizedImage = Bitmap.createScaledBitmap(image, tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(DataType.FLOAT32).apply { load(resizedImage) }
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter?.run(imageBuffer, output.buffer)

        val bestBoxes = bestBox(output.floatArray)
        if (bestBoxes == null) {
            detectorListener.onEmptyDetect()
            return
        }

        // Jalankan klasifikasi kualitas untuk setiap bounding box
        val updatedBoxes = bestBoxes.map { box ->
            val croppedBitmap = cropBitmap(image, box)
            val quality = classifyQuality(croppedBitmap, box.clsName)
            box.copy(quality = quality) // Salin box dan tambahkan hasil kualitas
        }

        val finalInferenceTime = SystemClock.uptimeMillis() - inferenceTime
        detectorListener.onDetect(updatedBoxes, finalInferenceTime, imageUri)
    }

    /**
     * Fungsi klasifikasi kualitas yang digeneralisasi.
     * Memilih interpreter yang tepat berdasarkan nama kelas.
     */
    private fun classifyQuality(image: Bitmap, clsName: String): String {
        val (targetInterpreter, targetLabels) = when (clsName.toLowerCase()) {
            "pisang" -> interpreterPisang to labelsPisang
            "jeruk" -> interpreterJeruk to labelsJeruk
            "semangka" -> interpreterSemangka to labelsSemangka
            else -> return "N/A" // Jika bukan buah yang didukung
        }

        if (targetInterpreter == null) return "N/A"

        // Ukuran input model kualitas (misal: 224x224), sesuaikan jika berbeda
        val inputSize = targetInterpreter.getInputTensor(0).shape()[1]
        val resizedImage = Bitmap.createScaledBitmap(image, inputSize, inputSize, true)

        // Pre-processing untuk model kualitas
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedImage)
        val qualityImageProcessor = ImageProcessor.Builder()
            .add(NormalizeOp(0f, 255f)) // Normalisasi umum, sesuaikan jika perlu
            .build()
        val processedImage = qualityImageProcessor.process(tensorImage)

        // Siapkan output buffer dan jalankan inference
        val outputShape = targetInterpreter.getOutputTensor(0).shape()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        targetInterpreter.run(processedImage.buffer, outputBuffer.buffer)

        // Dapatkan hasil dengan skor tertinggi
        val scores = outputBuffer.floatArray
        val maxScoreIndex = scores.indices.maxByOrNull { scores[it] } ?: -1
        val result = if (maxScoreIndex != -1) targetLabels[maxScoreIndex] else "N/A"
//        Log.d("QualityCheck", "Buah: $clsName, Terdeteksi Kualitas: $result")
        return result
    }

    /**
     * Memotong bitmap berdasarkan koordinat BoundingBox.
     * Koordinat box dinormalisasi (0-1), jadi kita perlu mengalikannya dengan dimensi gambar.
     */
    private fun cropBitmap(source: Bitmap, box: BoundingBox): Bitmap {
        val left = (box.x1 * source.width).toInt()
        val top = (box.y1 * source.height).toInt()
        val right = (box.x2 * source.width).toInt()
        val bottom = (box.y2 * source.height).toInt()

        // Pastikan koordinat valid dan tidak di luar batas bitmap
        val x = maxOf(0, left)
        val y = maxOf(0, top)
        val width = maxOf(0, right - x)
        val height = maxOf(0, bottom - y)

        if (width == 0 || height == 0 || x + width > source.width || y + height > source.height) {
            // Kembalikan bitmap kosong atau source jika crop tidak valid
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        return Bitmap.createBitmap(source, x, y, width, height)
    }

    private fun bestBox(array: FloatArray?): List<BoundingBox>? {
        if (array == null) return null
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (i in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = i + j * numElements
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val cx = array[i]
                val cy = array[i + numElements]
                val w = array[i + 2 * numElements]
                val h = array[i + 3 * numElements]
                val x1 = cx - w / 2
                val y1 = cy - h / 2
                val x2 = cx + w / 2
                val y2 = cy + h / 2
                if (x1 < 0f || x1 > 1f || y1 < 0f || y1 > 1f || x2 < 0f || x2 > 1f || y2 < 0f || y2 > 1f) continue

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = maxConf,
                        cls = maxIdx,
                        clsName = labels[maxIdx]
                    )
                )
            }
        }

        if (boundingBoxes.isEmpty()) return null
        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }
        val selectedBoxes = mutableListOf<BoundingBox>()
        val active = BooleanArray(boxes.size) { true }
        var numActive = active.size

        var i = 0
        while (i < sortedBoxes.size) {
            if (active[i]) {
                val boxA = sortedBoxes[i]
                selectedBoxes.add(boxA)
                if (numActive == 1) break

                for (j in i + 1 until sortedBoxes.size) {
                    if (active[j]) {
                        val boxB = sortedBoxes[j]
                        if (iou(boxA, boxB) > IOU_THRESHOLD) {
                            active[j] = false
                            numActive--
                        }
                    }
                }
            }
            i++
        }
        return selectedBoxes
    }

    private fun iou(boxA: BoundingBox, boxB: BoundingBox): Float {
        val xA = maxOf(boxA.x1, boxB.x1)
        val yA = maxOf(boxA.y1, boxB.y1)
        val xB = minOf(boxA.x2, boxB.x2)
        val yB = minOf(boxA.y2, boxB.y2)

        val intersectionArea = maxOf(0f, xB - xA) * maxOf(0f, yB - yA)
        val boxAArea = (boxA.x2 - boxA.x1) * (boxA.y2 - boxA.y1)
        val boxBArea = (boxB.x2 - boxB.x1) * (boxB.y2 - boxB.y1)

        val unionArea = boxAArea + boxBArea - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    /**
     * Penting: Ubah interface listener untuk menerima List<BoundingBox> yang sudah
     * memiliki properti kualitas di dalamnya. Ini lebih bersih daripada mengirim list terpisah.
     */
    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long, imageUri: Uri? = null)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
    }
}