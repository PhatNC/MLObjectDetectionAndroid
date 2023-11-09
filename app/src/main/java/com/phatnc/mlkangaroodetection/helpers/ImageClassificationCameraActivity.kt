package com.phatnc.mlkangaroodetection.helpers

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.PermissionChecker
import com.phatnc.mlkangaroodetection.R
import com.phatnc.mlkangaroodetection.databinding.ActivityMainBinding
import com.phatnc.mlkangaroodetection.ml.MobilenetV110224Quant
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

typealias LumaListener = (luma: Double) -> Unit

class ImageClassificationCameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var snapshotHandler: Handler? = null

    private lateinit var bitmap: Bitmap
    private lateinit var imageCaptureButton: Button
    private lateinit var videoCaptureButton: Button
    private lateinit var viewFinder: PreviewView
    private lateinit var textResult: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var photoImageView: ImageView
    private lateinit var startCameraButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_classification_camera)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        imageCaptureButton = findViewById(R.id.image_capture_button)
        videoCaptureButton = findViewById(R.id.video_capture_button)
        viewFinder = findViewById(R.id.viewFinder)
        textResult = findViewById(R.id.textResult)
        photoImageView = findViewById(R.id.photoImageView)
        startCameraButton = findViewById(R.id.restart_camera)

        // Set up the listeners for take photo and video capture buttons
        imageCaptureButton.setOnClickListener { takePhoto() }
        videoCaptureButton.setOnClickListener { captureVideo() }
        startCameraButton.setOnClickListener { restartCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun restartCamera() {
        photoImageView.visibility = View.GONE
        viewFinder.visibility = View.VISIBLE
        startCamera()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){

                    // Stop the camera after taking a photo
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(this@ImageClassificationCameraActivity)
                    cameraProviderFuture.addListener({
                        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                    }, ContextCompat.getMainExecutor(this@ImageClassificationCameraActivity))

                    viewFinder.visibility = View.GONE

                    val imgUri = output.savedUri
                    val msg = "Photo capture succeeded: ${output.savedUri}"

                    if (imgUri != null) {
                        try {
                            val contentResolver = applicationContext.contentResolver
                            val inputStream = contentResolver.openInputStream(imgUri)
                            if (inputStream != null) {
                                bitmap = BitmapFactory.decodeStream(inputStream)
                                Log.d(TAG, "bitmap $bitmap")
                                inputStream?.close()

                                if (bitmap != null) {
                                    // Use the loaded bitmap
                                    photoImageView.setImageBitmap(bitmap)
                                    photoImageView.visibility = View.VISIBLE

                                    val label = runClassification()
                                    Log.d(TAG, "label $label")
                                    textResult.text = label
                                } else {
                                    // Handle the case where the bitmap couldn't be loaded
                                }
                            } else {
                                // Handle the case where 'inputStream' is null
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                            // Handle any exceptions that may occur during image decoding
                        }
                    }
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun takeSnapshot() {
        // Code to capture a snapshot here
        Log.d(TAG, "Taking snapshot")
        takePhoto()
    }

    private val snapshotRunnable = object : Runnable {
        override fun run() {
            takeSnapshot()
            snapshotHandler?.postDelayed(this, 2000) // Capture every 2 seconds (2000 milliseconds)
        }
    }

    private fun startSnapshotCapture() {
        snapshotHandler = Handler(Looper.getMainLooper())
        snapshotHandler?.post(snapshotRunnable)
    }

    private fun stopSnapshotCapture() {
        snapshotHandler?.removeCallbacks(snapshotRunnable)
        snapshotHandler = null
        textResult.text = ""
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@ImageClassificationCameraActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }

                        startSnapshotCapture()
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopSnapshotCapture()

                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
//                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }


                    }
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageCapture = ImageCapture.Builder().build()

            /*
            val imageAnalyzer = ImageAnalysis.Builder().build()
                .also {
                    setAnalyzer(
                        cameraExecutor,
                        LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Average luminosity: $luma")
                        }
                    )
                }
            */

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun runClassification(): String {
        var labelResult: String = ""
        try {
            val model = MobilenetV110224Quant.newInstance(this@ImageClassificationCameraActivity)

            // Load the input image from a file or another source
            val inputBitmap: Bitmap = bitmap // Replace this with your actual loading code

            // Resize the input image to match the model's expected size
            val resizedBitmap = Bitmap.createScaledBitmap(inputBitmap, 224, 224, true)

            // Create inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.UINT8)
            inputFeature0.loadBuffer(TensorImage.fromBitmap(resizedBitmap).buffer)

            // Runs model inference and gets the result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val labelIndex = getMax(outputFeature0.floatArray)

            var labels = arrayOf<String>()

            try {
                val inputStream: InputStream = assets.open("labels.txt")
                val lineList = mutableListOf<String>()

                inputStream.bufferedReader().forEachLine { lineList.add(it) }
                labels = lineList.toTypedArray()

            } catch (e: IOException) {
                e.printStackTrace()
                // Handle the exception
            }

            print("label: ${labels[labelIndex]}")
            // Releases model resources if no longer used.
            model.close()

            labelResult = labels[labelIndex]

        } catch (e: IllegalArgumentException) {
            // Handle the specific exception
            e.printStackTrace() // Print the exception for debugging
            // Add your custom error handling logic here
        }
        return labelResult
    }

    private fun getMax(arr: FloatArray): Int {
        var max = 0
        for (i in arr.indices) {
            // Your code here
            if (arr[i] > arr[max]) {
                max = i
            }
        }
        return max
    }
}

private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {

        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        listener(luma)

        image.close()
    }
}
