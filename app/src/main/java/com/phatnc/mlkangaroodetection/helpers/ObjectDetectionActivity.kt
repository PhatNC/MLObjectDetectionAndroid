package com.phatnc.mlkangaroodetection.helpers

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.phatnc.mlkangaroodetection.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.IOException

class ObjectDetectionActivity : AppCompatActivity() {
    private lateinit var resultTextView: TextView
    private lateinit var pickImage: FloatingActionButton
    private lateinit var selectedImage: AppCompatImageView
    private lateinit var bitmap: Bitmap
    private lateinit var pickCamera: FloatingActionButton

    private val changeImage =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                val data = it.data
                val imgUri = data?.data
                selectedImage.setImageURI(imgUri)

                // Ensure that the imgUri is not null before proceeding
                if (imgUri != null) {
                    try {
                        val contentResolver = applicationContext.contentResolver
                        val inputStream = contentResolver.openInputStream(imgUri)
                        if (inputStream != null) {
                            bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()

                            if (bitmap != null) {
                                // Use the loaded bitmap
//                                runClassification()
                                lifecycleScope.launch(Dispatchers.Default) { runObjectDetection(bitmap) }
                                resultTextView.text = "Detecting..."
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
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_detection)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 0)
            }

            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 0)
            }
        } else {
            // Android 7.0+ to before 13
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
                }
            }
        }

        pickImage = findViewById(R.id.pick_image)
        pickCamera = findViewById(R.id.pick_camera)
        selectedImage = findViewById(R.id.selected_image)
        resultTextView = findViewById(R.id.textView)

        pickImage.setOnClickListener {
            val pickImg = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            pickImg.type = "image/*" // Set the MIME type to image/*
            changeImage.launch(pickImg)
        }

        pickCamera.setOnClickListener {
            val intent = Intent(this, ObjectDetectionCameraActivity::class.java)
            startActivity(intent)
        }
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
    }

    private fun debugPrint(results: List<Detection>) {

        if (results.isEmpty()){
            resultTextView.text = "Cannot find any object!"
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

            // Draw the bounding box
            val paint = Paint()
            paint.color = Color.GREEN
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f

            canvas.drawRect(box.left, box.top, box.right, box.bottom, paint)

            // Add the label
            paint.color = Color.GREEN
            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${firstLabel.label} $confidence%", box.left, box.top - 10f, paint)
        }

        resultTextView.text = textResult

        // Set the modified bitmap with bounding box and label to the ImageView
        selectedImage.setImageBitmap(mutableBitmap)
    }
}