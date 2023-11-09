package com.phatnc.mlkangaroodetection.helpers

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import android.widget.ProgressBar
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
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

class ObjectDetectionCameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    private lateinit var bitmap: Bitmap
    private lateinit var imageCaptureButton: Button
    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var photoImageView: ImageView
    private lateinit var startCameraButton: Button
    private lateinit var textLoading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_detection_camera)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        imageCaptureButton = findViewById(R.id.image_capture_button)
        viewFinder = findViewById(R.id.viewFinder)
        photoImageView = findViewById(R.id.photoImageView)
        startCameraButton = findViewById(R.id.restart_camera)
        textLoading = findViewById(R.id.textLoading)

        // Set up the listeners for take photo and video capture buttons
        imageCaptureButton.setOnClickListener { takePhoto() }
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
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(this@ObjectDetectionCameraActivity)
                    cameraProviderFuture.addListener({
                        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                    }, ContextCompat.getMainExecutor(this@ObjectDetectionCameraActivity))

//                    viewFinder.visibility = View.GONE

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
                                    runOnUiThread {
                                        photoImageView.setImageBitmap(bitmap)
//                                        photoImageView.visibility = View.VISIBLE
                                        textLoading.text = "Detecting..."
                                    }
                                    runObjectDetectionInBackground(bitmap)
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

    private fun runObjectDetectionInBackground(bitmap: Bitmap) {
        // Show a progress indicator if needed
        textLoading.text = "Detecting..."

        // Run object detection in a background thread or coroutine
        // ...


        // Simulating a delay with a Handler
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            // Update UI or perform any additional processing after object detection
            // ...
            runObjectDetection(bitmap)

            // Hide the progress indicator
            textLoading.text = ""
        }, 2000) // Simulated 2-second delay, replace with the actual duration
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
                    this, cameraSelector, preview, imageCapture)

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

    /**
     * TFLite Object Detection Function
     */
    private fun runObjectDetection(bitmap: Bitmap) {
        //TODO: Add object detection code here
        // Step 1: create TFLite's TensorImage object
        val image = TensorImage.fromBitmap(bitmap)

        //TODO:
        // Step 2: Initialize the detector object
        val options = ObjectDetector.ObjectDetectorOptions.builder()
//            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .build()
        val detector = ObjectDetector.createFromFileAndOptions(
            this, // the application context
            "lite-model_efficientdet_lite3_detection_metadata_1.tflite", // must be same as the filename in assets folder
            options
        )

        //TODO:
        // Step 3: feed given image to the model and print the detection result
        val results = detector.detect(image)

        //TODO:
        // Step 4: Parse the detection result and show it
        debugPrint(results)
        textLoading.text = ""
    }

    private fun debugPrint(results: List<Detection>) {
        Log.d(TAG,"results $results")

        if (results.isEmpty()) {
            return
        }

        var textResult: String = ""
        // Create a mutable copy of the bitmap
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Create a Canvas to draw on the mutable bitmap
        val canvas = Canvas(mutableBitmap)

        for ((i, obj) in results.withIndex()) {
            val box = obj.boundingBox

            Log.d(ContentValues.TAG, "Detected object: ${i} ")
            Log.d(ContentValues.TAG, "  boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")

            for ((j, category) in obj.categories.withIndex()) {
                Log.d(ContentValues.TAG, "    Label $j: ${category.label}")
                val confidence: Int = category.score.times(100).toInt()
                Log.d(ContentValues.TAG, "    Confidence: ${confidence}%")
            }

            val firstLabel = obj.categories.first()
            val confidence: Int = firstLabel.score.times(100).toInt()

            textResult += "${firstLabel.label}(score: $confidence%): location: (${box.left}, ${box.top}, ${box.right},${box.bottom}) \n"

            textLoading.text = textResult

            // Draw the bounding box
            val paint = Paint()
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f

            canvas.drawRect(box.left, box.top, box.right, box.bottom, paint)

            // Add the label
            paint.color = Color.RED
            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${firstLabel.label} $confidence%", box.left, box.top - 10f, paint)
        }
        // Set the modified bitmap with bounding box and label to the ImageView
        photoImageView.setImageBitmap(mutableBitmap)
    }

}
