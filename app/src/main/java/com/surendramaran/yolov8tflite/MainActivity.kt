package com.surendramaran.yolov8tflite

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var detector: Detector? = null
    private lateinit var cameraExecutor: ExecutorService
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Inisialisasi detector di sini
        detector = Detector(baseContext, "model.tflite", "labels.txt", this)

        bindListeners()
    }

    private fun bindListeners() {
        binding.btnTakePicture.setOnClickListener { takePhoto() }
        // PERBAIKAN: Mengembalikan nama tombol ke btnImportGallery
        binding.btnImportGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = "food_analysis_${System.currentTimeMillis()}.jpg"
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
                        showConfirmationDialog(bitmap, uri)
                    }
                }
            }
        )
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                showConfirmationDialog(bitmap, uri)
            }
        }
    }

    private fun showConfirmationDialog(bitmap: Bitmap, uri: Uri) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.confirmImageView)
        imageView.setImageBitmap(bitmap)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Konfirmasi") { _, _ ->
                showLoading()
                detector?.detect(bitmap, uri)
            }
            .setNegativeButton("Ambil Ulang") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setView(R.layout.loading_dialog)
        loadingDialog = builder.create()
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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
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
            // Logika di sini mungkin berbeda tergantung kebutuhan MainActivity Anda.
            // Jika tujuannya sama dengan CameraActivity (pindah ke ResultActivity),
            // maka kodenya bisa serupa.

            // Contoh: Pindah ke ResultActivity
            val intent = Intent(this@MainActivity, ResultActivity::class.java).apply {
                putParcelableArrayListExtra(ResultActivity.EXTRA_DETECTED_FOODS, ArrayList(boundingBoxes))
                putExtra(ResultActivity.EXTRA_IMAGE_URI, imageUri.toString())
                val qualityResults = boundingBoxes.map { it.quality ?: "N/A" }
                putStringArrayListExtra(ResultActivity.EXTRA_QUALITY_RESULTS, ArrayList(qualityResults))
            }
            startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
