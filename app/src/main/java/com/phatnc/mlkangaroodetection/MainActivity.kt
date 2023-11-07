package com.phatnc.mlkangaroodetection
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton
import com.phatnc.mlkangaroodetection.helpers.ClassificationActivity
import com.phatnc.mlkangaroodetection.helpers.HandwrittenActivity
import com.phatnc.mlkangaroodetection.helpers.ObjectDetectionActivity

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val objectDetectionButton = findViewById<ImageButton>(R.id.objectDetectionButton)
        val imageClassification = findViewById<ImageButton>(R.id.imageClassification)
        val handWrittenClassification = findViewById<ImageButton>(R.id.handWrittenClassification)

        objectDetectionButton.setOnClickListener {
            val intent = Intent(this, ObjectDetectionActivity::class.java)
            startActivity(intent)
        }

        imageClassification.setOnClickListener {
            val intent = Intent(this, ClassificationActivity::class.java)
            startActivity(intent)
        }

        handWrittenClassification.setOnClickListener {
            val intent = Intent(this,HandwrittenActivity::class.java)
            startActivity(intent)
        }
    }
}