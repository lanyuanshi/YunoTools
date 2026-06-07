package com.yuno.tools.ui.image

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class QRCodeActivity : AppCompatActivity() {

    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        ThemeApplier.apply(this)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val etText = findViewById<android.widget.EditText>(R.id.etText)
        val ivQRCode = findViewById<ImageView>(R.id.ivQRCode)
        val btnGenerate = findViewById<MaterialButton>(R.id.btnGenerate)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        btnGenerate.setOnClickListener {
            val text = etText.text.toString().trim()
            if (text.isNotEmpty()) {
                try {
                    currentBitmap = generateQRCode(text, 768)
                    ivQRCode.setImageBitmap(currentBitmap)
                    ivQRCode.isVisible = true
                    btnSave.isVisible = true
                } catch (e: Exception) {
                    Toast.makeText(this, "生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            saveQRCode()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun generateQRCode(text: String, size: Int): Bitmap {
        val writer = MultiFormatWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun saveQRCode() {
        val bitmap = currentBitmap ?: return
        val fileName = "qrcode_${System.currentTimeMillis()}.png"
        try {
            val bytes = bitmap.toPngBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YunoTools")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunoTools")
                dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
            }
            Toast.makeText(this, "已保存到相册/YunoTools", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }
}