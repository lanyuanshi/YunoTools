package com.yuno.tools.ui.image

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
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
    private lateinit var spMode: Spinner

    private val adapter = GridImageAdapter()
    private val gridBitmaps = mutableListOf<Bitmap>()
    private var selectedUri: Uri? = null
    private var rows = 3
    private var cols = 3

    private val modes = listOf(
        GridMode("1×3 横向三图", 1, 3),
        GridMode("2×2 四宫格", 2, 2),
        GridMode("3×3 九宫格", 3, 3)
    )

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedUri = it
            processGridCrop(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid_crop)
        ThemeApplier.apply(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        btnPick = findViewById(R.id.btnPick)
        btnSaveAll = findViewById(R.id.btnSaveAll)
        spMode = findViewById(R.id.spMode)

        recyclerView.layoutManager = GridLayoutManager(this, cols)
        recyclerView.adapter = adapter

        spMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes.map { it.label })
        spMode.setSelection(2)
        spMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val mode = modes[position]
                rows = mode.rows
                cols = mode.cols
                recyclerView.layoutManager = GridLayoutManager(this@GridCropActivity, cols)
                adapter.updateColumns(cols)
                selectedUri?.let { processGridCrop(it) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

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
                val cellW = size / cols
                val cellH = size / rows

                gridBitmaps.clear()
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val bmp = Bitmap.createBitmap(square, col * cellW, row * cellH, cellW, cellH)
                        gridBitmaps.add(bmp)
                    }
                }

                adapter.submit(gridBitmaps)
                recyclerView.isVisible = true
                btnSaveAll.isVisible = true
                src.recycle()
                if (!square.isRecycled) square.recycle()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAll() {
        if (gridBitmaps.isEmpty()) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }

        var success = 0
        val baseName = System.currentTimeMillis()
        val total = gridBitmaps.size

        for ((i, bmp) in gridBitmaps.withIndex()) {
            try {
                val fileName = "grid_${rows}x${cols}_${baseName}_${i + 1}.jpg"
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
            } catch (_: Exception) {
            }
        }

        Toast.makeText(this, "已保存 $success/$total 张到相册/YunoTools/Grid", Toast.LENGTH_SHORT).show()
    }

    private fun Bitmap.toJpgBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 95, baos)
        return baos.toByteArray()
    }

    private data class GridMode(val label: String, val rows: Int, val cols: Int)

    inner class GridImageAdapter : RecyclerView.Adapter<GridImageAdapter.VH>() {
        private var items = listOf<Bitmap>()
        private var itemColumns = 3

        fun updateColumns(columns: Int) {
            itemColumns = columns
            notifyDataSetChanged()
        }

        fun submit(list: List<Bitmap>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val size = parent.context.resources.displayMetrics.widthPixels / itemColumns - 28
            val iv = ImageView(parent.context).apply {
                id = R.id.ivGridItem
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, size)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(4, 4, 4, 4)
                clearColorFilter()
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.iv.layoutParams = holder.iv.layoutParams.apply {
                height = holder.iv.resources.displayMetrics.widthPixels / itemColumns - 28
            }
            holder.iv.clearColorFilter()
            holder.iv.setImageBitmap(items[position])
        }

        override fun getItemCount() = items.size

        inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)
    }
}
