package com.yuno.tools.ui.image

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class GridCropActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnPick: MaterialButton
    private lateinit var btnSaveAll: MaterialButton
    private val adapter = GridImageAdapter()
    private val gridBitmaps = mutableListOf<Bitmap>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processGridCrop(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid_crop)
        ThemeApplier.apply(this)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        btnPick = findViewById(R.id.btnPick)
        btnSaveAll = findViewById(R.id.btnSaveAll)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        btnPick.setOnClickListener { pickImage.launch("image/*") }
        btnSaveAll.setOnClickListener { saveAll() }
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun processGridCrop(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val src = BitmapFactory.decodeStream(stream) ?: return@use
                val w = src.width
                val h = src.height
                val size = minOf(w, h)
                val x = (w - size) / 2
                val y = (h - size) / 2
                val square = Bitmap.createBitmap(src, x, y, size, size)
                val cell = size / 3

                gridBitmaps.clear()
                for (row in 0 until 3) {
                    for (col in 0 until 3) {
                        val bmp = Bitmap.createBitmap(square, col * cell, row * cell, cell, cell)
                        gridBitmaps.add(bmp)
                    }
                }
                adapter.submit(gridBitmaps)
                recyclerView.isVisible = true
                btnSaveAll.isVisible = true
                src.recycle()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAll() {
        if (gridBitmaps.isEmpty()) return
        var success = 0
        val baseName = System.currentTimeMillis()
        for ((i, bmp) in gridBitmaps.withIndex()) {
            try {
                val fileName = "grid_${baseName}_${i + 1}.jpg"
                val bytes = bmp.toJpgBytes()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YunoTools/Grid")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunoTools/Grid")
                    dir.mkdirs()
                    FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
                }
                success++
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "已保存 $success/9 张到相册/YunoTools/Grid", Toast.LENGTH_SHORT).show()
    }

    private fun Bitmap.toJpgBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 95, baos)
        return baos.toByteArray()
    }

    inner class GridImageAdapter : RecyclerView.Adapter<GridImageAdapter.VH>() {
        private var items = listOf<Bitmap>()

        fun submit(list: List<Bitmap>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    parent.context.resources.displayMetrics.widthPixels / 3 - 12
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(4, 4, 4, 4)
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.iv.setImageBitmap(items[position])
        }

        override fun getItemCount() = items.size

        inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)
    }
}