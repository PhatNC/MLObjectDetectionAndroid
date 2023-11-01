package com.phatnc.mlkangaroodetection
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton
import com.phatnc.mlkangaroodetection.helpers.ObjectDetectionActivity

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val objectDetectionButton = findViewById<ImageButton>(R.id.objectDetectionButton)
        val imageClassification = findViewById<ImageButton>(R.id.imageClassification)
        val flowerClassification = findViewById<ImageButton>(R.id.flowerDetection)

        objectDetectionButton.setOnClickListener {
            val intent = Intent(this, ObjectDetectionActivity::class.java)
            startActivity(intent)
        }

        imageClassification.setOnClickListener {

        }

        flowerClassification.setOnClickListener {

        }
    }
}