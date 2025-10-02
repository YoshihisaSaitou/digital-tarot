package com.magicitengineer.digitaltarotandroidapp.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.camera2.interop.Camera2Interop
import android.graphics.Matrix
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.magicitengineer.digitaltarotandroidapp.CardStorage
import com.magicitengineer.digitaltarotandroidapp.R
import org.opencv.android.OpenCVLoader
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    companion object {
        const val RESULT_FILE_NAME = "result_file_name"
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var tvHint: TextView
    private lateinit var storage: CardStorage

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Initialize OpenCV in debug (bundled) mode
        OpenCVLoader.initDebug()

        storage = CardStorage(this)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        tvHint = findViewById(R.id.tvHint)

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<View>(R.id.btnShutter).setOnClickListener {
            pendingUserShot = true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(1280, 720))
                .build()

            val captureBuilder = ImageCapture.Builder()
            // Prefer short exposure by using higher FPS range if possible
            Camera2Interop.Extender(captureBuilder)
                .setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
                )
            val imageCapture = captureBuilder
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            this.imageCapture = imageCapture

            analysis.setAnalyzer(cameraExecutor, analyzer)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, analysis, imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // State
    private var detectionOkFrames = 0
    private var pendingUserShot = false

    private val analyzer = ImageAnalysis.Analyzer { image ->
        try {
            val frame = ImageUtils.toBitmap(image, applyRotation = true) ?: return@Analyzer image.close()
            val result = OpenCvProcessor.detectAndGuide(frame.width, frame.height, frame)

            runOnUiThread {
                overlay.mode = if (result.detectedCircle) DetectionOverlay.Mode.CIRCLE else DetectionOverlay.Mode.RECT
                overlay.message = result.message
                overlay.invalidate()
                tvHint.text = result.message ?: ""
            }

            if (result.isGood && pendingUserShot) {
                detectionOkFrames++
            } else {
                detectionOkFrames = 0
            }

            if (detectionOkFrames >= 3 && pendingUserShot) {
                pendingUserShot = false
                lockAndCapture(image)
                return@Analyzer // capture path will close image
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun lockAndCapture(refImage: ImageProxy) {
        val cam = camera ?: return
        try {
            // Torch off just before capture
            cam.cameraControl.enableTorch(false)

            // Focus and metering at center
            val factory = previewView.meteringPointFactory
            val center = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
            val action = FocusMeteringAction.Builder(center, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)

            imageCapture?.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processAndFinish(image)
                }
                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processAndFinish(image: ImageProxy) {
        try {
            val bitmap = ImageUtils.toBitmap(image, applyRotation = true) ?: return image.close()
            val processed = OpenCvProcessor.detectAndWarp(bitmap)
            if (processed == null) {
                runOnUiThread { tvHint.text = getString(R.string.err_blur_or_not_found) }
                image.close()
                return
            }

            val dir = storage.cardsDirectory().apply { mkdirs() }
            val file = File(dir, "card-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { os ->
                processed.compress(Bitmap.CompressFormat.JPEG, 95, os)
            }

            val data = Intent().putExtra(RESULT_FILE_NAME, file.name)
            setResult(RESULT_OK, data)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}

private object ImageUtils {
    fun toBitmap(image: ImageProxy, applyRotation: Boolean = false): Bitmap? {
        return when (image.format) {
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (applyRotation) bmp = rotate(bmp, image.imageInfo.rotationDegrees)
                bmp
            }
            ImageFormat.YUV_420_888 -> {
                var bmp = yuv420ToBitmap(image)
                if (applyRotation && bmp != null) bmp = rotate(bmp, image.imageInfo.rotationDegrees)
                bmp
            }
            else -> null
        }
    }

    private fun yuv420ToBitmap(image: ImageProxy): Bitmap? {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer
        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride
        val width = image.width
        val height = image.height

        val argb = IntArray(width * height)
        val y = ByteArray(yPlane.remaining())
        val u = ByteArray(uPlane.remaining())
        val v = ByteArray(vPlane.remaining())
        yPlane.get(y)
        uPlane.get(u)
        vPlane.get(v)

        var yp = 0
        for (j in 0 until height) {
            val pY = j * yRowStride
            val uvRow = (j shr 1) * uvRowStride
            for (i in 0 until width) {
                val uvCol = (i shr 1) * uvPixelStride
                val Y = (y[pY + i].toInt() and 0xFF)
                val U = (u[uvRow + uvCol].toInt() and 0xFF) - 128
                val V = (v[uvRow + uvCol].toInt() and 0xFF) - 128

                var r = (Y + 1.370705f * V).toInt()
                var g = (Y - 0.698001f * V - 0.337633f * U).toInt()
                var b = (Y + 1.732446f * U).toInt()
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255
                argb[yp++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(argb, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
