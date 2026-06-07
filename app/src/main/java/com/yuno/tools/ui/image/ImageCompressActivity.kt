package com.yuno.tools.ui.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

class ImageCompressActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var ivCompressed: ImageView
    private lateinit var seekQuality: SeekBar
    private lateinit var btnPick: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var tvInfo: androidx.appcompat.widget.AppCompatTextView

    private var originalBitmap: Bitmap? = null
    private var compressedBytes: ByteArray? = null
    private var originalSizeStr = ""

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_compress)
        ThemeApplier.apply(this)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        ivPreview = findViewById(R.id.ivPreview)
        ivCompressed = findViewById(R.id.ivCompressed)
        seekQuality = findViewById(R.id.seekQuality)
        btnPick = findViewById(R.id.btnPick)
        btnSave = findViewById(R.id.btnSave)
        tvInfo = findViewById(R.id.tvInfo)

        btnPick.setOnClickListener { pickImage.launch("image/*") }
        btnSave.setOnClickListener { saveCompressed() }

        seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                compressAndShow(progress.coerceIn(10, 100))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun loadImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                originalBitmap = BitmapFactory.decodeStream(stream)
                ivPreview.setImageBitmap(originalBitmap)
                ivPreview.isVisible = true
                val fileSize = getFileSize(uri)
                originalSizeStr = formatSize(fileSize)
                seekQuality.progress = 80
                compressAndShow(80)
                ivCompressed.isVisible = true
                btnSave.isVisible = true
            }
        } catch (e: Exception) {
            Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressAndShow(quality: Int) {
        val bmp = originalBitmap ?: return
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        compressedBytes = baos.toByteArray()
        val compressedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes!!.size)
        ivCompressed.setImageBitmap(compressedBmp)
        val newSize = formatSize(compressedBytes!!.size.toLong())
        tvInfo.text = "原图: $originalSizeStr  →  压缩后: $newSize  (质量${quality}%)"
        tvInfo.isVisible = true
    }

    private fun saveCompressed() {
        val bytes = compressedBytes ?: return
        val fileName = "compressed_${System.currentTimeMillis()}.jpg"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
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

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun formatSize(bytes: Long): String {
        val df = DecimalFormat("0.00")
        return when {
            bytes >= 1024 * 1024 -> df.format(bytes / 1024.0 / 1024.0) + " MB"
            bytes >= 1024 -> df.format(bytes / 1024.0) + " KB"
            else -> "$bytes B"
        }
    }
}