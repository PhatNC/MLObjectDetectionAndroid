package com.phatnc.mlkangaroodetection.helpers

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.phatnc.mlkangaroodetection.R
import com.phatnc.mlkangaroodetection.ml.MobilenetV110224Quant
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.io.InputStream

class ClassificationActivity : AppCompatActivity() {
    private lateinit var resultTextView: TextView
    private lateinit var pickImage: FloatingActionButton
    private lateinit var selectedImage: AppCompatImageView
    private lateinit var imageLabeler: ImageLabeler
    private lateinit var bitmap: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classification)



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
        selectedImage = findViewById(R.id.selected_image)
        resultTextView = findViewById(R.id.textView)
        pickImage.setOnClickListener {
            val pickImg = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            changeImage.launch(pickImg)
        }

        imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build())
    }

    private val changeImage =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            println("HEHEHE")
            println(it)
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
                                runClassification()
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
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(ClassificationActivity::class.java.simpleName, "Grant result for ${permissions[0]} is ${grantResults[0]}")
    }

    private fun runClassification() {
        try {
            val model = MobilenetV110224Quant.newInstance(this@ClassificationActivity)

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

            resultTextView.text = labels[labelIndex]
            // Releases model resources if no longer used.
            model.close()
        } catch (e: IllegalArgumentException) {
            // Handle the specific exception
            e.printStackTrace() // Print the exception for debugging
            // Add your custom error handling logic here
        }
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
