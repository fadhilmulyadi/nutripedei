package com.surendramaran.yolov8tflite

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.databinding.ActivityCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var isFlashOn = false
    private var detector: Detector? = null
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize the detector using constants
        detector = Detector(baseContext, "model.tflite", "labels.txt", this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnTakePicture.setOnClickListener { takePhoto() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnGallery.setOnClickListener { openGallery() }
        binding.btnInfo.setOnClickListener { showInfoDialog() }
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tips Pengambilan Gambar")
            .setMessage(
                "• Pastikan makanan terlihat jelas\n" +
                        "• Gunakan pencahayaan yang baik\n" +
                        "• Ambil foto dari atas untuk hasil terbaik\n" +
                        "• Hindari bayangan yang menutupi makanan"
            )
            .setPositiveButton("Mengerti", null)
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                showLoading()
                detector?.detect(bitmap, uri)
            }
        }
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
        binding.btnFlash.alpha = if (isFlashOn) 1.0f else 0.5f
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = "NutriScan_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Gagal mengambil foto: ${exc.message}", exc)
                    Toast.makeText(baseContext, "Gagal mengambil foto.", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        showLoading()
                        detector?.detect(bitmap, uri)
                    }
                }
            }
        )
    }

    private fun showLoading() {
        if (loadingDialog == null) {
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(false)
            builder.setView(R.layout.loading_dialog)
            loadingDialog = builder.create()
        }
        loadingDialog?.show()
    }


    private fun hideLoading() {
        loadingDialog?.dismiss()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                binding.btnFlash.alpha = 0.5f
            } catch (exc: Exception) {
                Log.e(TAG, "Gagal memulai kamera", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera tidak diberikan.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector?.close()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            hideLoading()
            Toast.makeText(this, "Tidak ada makanan yang terdeteksi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDetect(
        boundingBoxes: List<BoundingBox>,
        inferenceTime: Long,
        imageUri: Uri?
    ) {
        runOnUiThread {
            hideLoading()

            // Ambil data kualitas dari setiap objek boundingBox
            val qualityResults = boundingBoxes.map { it.quality ?: "N/A" }
            Log.d("QualityCheck", "Hasil Kualitas yang akan dikirim: $qualityResults")

            val intent = Intent(this@CameraActivity, ResultActivity::class.java).apply {
                putParcelableArrayListExtra(ResultActivity.EXTRA_DETECTED_FOODS, ArrayList(boundingBoxes))
                putExtra(ResultActivity.EXTRA_IMAGE_URI, imageUri.toString())
                // Kirim hasil kualitas yang sudah kita ambil
                putStringArrayListExtra(ResultActivity.EXTRA_QUALITY_RESULTS, ArrayList(qualityResults))
            }
            startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}