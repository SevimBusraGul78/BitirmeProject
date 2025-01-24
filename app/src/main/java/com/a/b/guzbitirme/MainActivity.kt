package com.a.b.guzbitirme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.IOException

class MainActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {
    private val RESULT_LOAD_IMAGE = 123
    val IMAGE_CAPTURE_CODE = 654
    private val PERMISSION_CODE = 321
    var innerImage: ImageView? = null
    private var image_uri: Uri? = null
    private lateinit var objectDetectorHelper: ObjectDetectorHelper

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        innerImage = findViewById(R.id.imageView2)

        // Galeriden resim seçme
        innerImage?.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE)
        }

        // Uzun basma ile kamera açma
        innerImage?.setOnLongClickListener(OnLongClickListener {
            innerImage?.setOnLongClickListener(OnLongClickListener {
                Log.d("LongClick", "Uzun basıldı")
                true
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    val permission = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    requestPermissions(permission, PERMISSION_CODE)
                } else {
                    openCamera()
                }
            } else {
                openCamera()
            }
            true // Long click olduğunda true döndür
        })


        // Uygulama ilk açıldığında izin kontrolü
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            val permission = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permission, PERMISSION_CODE)
        }

        objectDetectorHelper =
            ObjectDetectorHelper(threshold = 0.5f, context = applicationContext, maxResults = ObjectDetectorHelper.MAX_RESULTS_DEFAULT, currentDelegate = ObjectDetectorHelper.DELEGATE_CPU, modelName = "fruits.tflite", runningMode = RunningMode.IMAGE)
    }

    // Kamerayı açma işlemi
    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        image_uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri)
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE)
    }

    // Activity sonuçları alındığında işlem yapma
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            image_uri = data.data
            innerImage!!.setImageURI(image_uri)
            doInference()
        }
        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == Activity.RESULT_OK) {
            innerImage!!.setImageURI(image_uri)
            doInference()
        }
    }

    // Nesne tespiti ve kalori analizi
    private fun doInference() {
        val bitmap = uriToBitmap(image_uri!!)
        val rotatedBmp = rotateBitmap(bitmap!!)
        innerImage!!.setImageBitmap(rotatedBmp)

        if (rotatedBmp != null) {
            val mutableBmp = rotatedBmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBmp)

            val p = Paint()
            p.color = Color.RED
            p.style = Paint.Style.STROKE
            p.strokeWidth = (mutableBmp.width / 95).toFloat()

            val paintText = Paint()
            paintText.color = Color.BLUE
            paintText.textSize = (mutableBmp.width / 10).toFloat()
            paintText.isFakeBoldText = true

            val resultBundle = objectDetectorHelper.detectImage(rotatedBmp)
            if (resultBundle != null) {
                val resultsList = resultBundle.results
                var totalCalories = 0
                val calorieInfo = mapOf(
                    "apple-" to 52,
                    "banana" to 88.7f,
                    "beetroot" to 43,
                    "bell pepper" to 20.1f,
                    "cabbage" to 24.6f,
                    "capsicum" to 39.9f,
                    "carrot" to 41.3f,
                    "cauliflower" to 24.9f,
                    "chill pepper" to 39.7f,
                    "corn" to 77
                )
                for (singleResult in resultsList) {
                    val detections = singleResult.detections()
                    for (singleDetection in detections) {
                        val categoriesList = singleDetection.categories()
                        var objectName = ""
                        var objectScore = 0f
                        for (singleCategory in categoriesList) {
                            if (singleCategory.score() > objectScore) {
                                objectScore = singleCategory.score()
                                objectName = singleCategory.categoryName()
                            }
                        }
                        val calorie = calorieInfo[objectName] ?: 0f
                        totalCalories += calorie.toInt()

                        canvas.drawRect(singleDetection.boundingBox(), p)
                        canvas.drawText(
                            "$objectName (${calorie} kcal)",
                            singleDetection.boundingBox().left,
                            singleDetection.boundingBox().top,
                            paintText
                        )
                    }
                }
                Log.d("TotalCalories", "Toplam Kalori: $totalCalories kcal")
                innerImage!!.setImageBitmap(mutableBmp)
            }
        }
    }

    // URI'den Bitmap'e dönüşüm
    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        try {
            val parcelFileDescriptor =
                contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    @SuppressLint("Range")
    fun rotateBitmap(input: Bitmap): Bitmap? {
        val orientationColumn =
            arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cur =
            contentResolver.query(image_uri!!, orientationColumn, null, null, null)
        var orientation = -1
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]))
        }
        Log.d("tryOrientation", orientation.toString() + "")
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(orientation.toFloat())
        return Bitmap.createBitmap(
            input,
            0,
            0,
            input.width,
            input.height,
            rotationMatrix,
            true
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e("ObjectDetection", "Error: $error Code: $errorCode")
    }

    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        // Unused as we're using detectImage in this example
    }
}
