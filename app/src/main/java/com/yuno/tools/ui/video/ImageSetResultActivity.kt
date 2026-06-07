package com.yuno.tools.ui.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.yuno.tools.R
import com.yuno.tools.data.VideoParseResult
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class ImageSetResultActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var rvImages: RecyclerView
    private lateinit var imageAdapter: ImageGridAdapter
    private var result: VideoParseResult? = null
    private val selectedPositions = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_set_result)

        result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("result", VideoParseResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("result")
        }

        if (result == null) {
            Toast.makeText(this, "数据无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupUI()
    }

    private fun initViews() {
        tvContent = findViewById(R.id.tvContent)
        rvImages = findViewById(R.id.rvImages)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnCopyContent).setOnClickListener { copyContent() }
        findViewById<MaterialCardView>(R.id.btnCopyText).setOnClickListener { copyContent() }
        findViewById<MaterialCardView>(R.id.btnSaveContent).setOnClickListener { downloadSelected() }
    }

    private fun setupUI() {
        val data = result!!
        val text = when {
            data.content.isNotEmpty() -> data.content
            data.title.isNotEmpty() -> data.title
            else -> "暂无文案"
        }
        tvContent.text = text

        imageAdapter = ImageGridAdapter(data.images) { position, isSelected ->
            if (isSelected) selectedPositions.add(position)
            else selectedPositions.remove(position)
        }
        rvImages.layoutManager = GridLayoutManager(this, 3)
        rvImages.adapter = imageAdapter
        // 关闭默认动画，避免与自定义动画冲突导致卡顿
        rvImages.itemAnimator = null
    }

    private fun copyContent() {
        val text = tvContent.text.toString()
        if (text.isNotEmpty() && text != "暂无文案") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("文案", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "文案已复制", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadSelected() {
        val images = result?.images ?: return
        val toDownload = if (selectedPositions.isEmpty()) {
            images.indices.toList()
        } else {
            selectedPositions.sorted()
        }

        if (toDownload.isEmpty()) {
            Toast.makeText(this, "没有可下载的图片", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "开始下载 ${toDownload.size} 张图片...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            var success = 0
            var failed = 0
            var lastError = ""

            toDownload.forEach { index ->
                val url = images.getOrNull(index) ?: return@forEach
                try {
                    downloadImageToGallery(url, index)
                    success++
                } catch (e: Exception) {
                    failed++
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }

            withContext(Dispatchers.Main) {
                val msg = if (failed > 0 && lastError.isNotBlank()) {
                    "下载完成: ${success}成功 ${failed}失败，原因: $lastError"
                } else {
                    "下载完成: ${success}成功 ${failed}失败"
                }
                Toast.makeText(this@ImageSetResultActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadImageToGallery(imageUrl: String, index: Int) {
        val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 30000
            readTimeout = 60000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            setRequestProperty("Connection", "close")
            runCatching {
                val u = URL(imageUrl)
                setRequestProperty("Referer", "${u.protocol}://${u.host}/")
            }
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")

            val contentType = conn.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            val ext = guessImageExtension(imageUrl, contentType)
            val mimeType = contentType?.takeIf { it.startsWith("image/") } ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext) ?: "image/jpeg"
            val fileName = "yunotools_img_${System.currentTimeMillis()}_${index}.$ext"

            conn.inputStream.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YunoTools/ImageSets")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("无法创建图片文件")
                    try {
                        contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                            ?: throw IllegalStateException("无法写入图片文件")
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    } catch (e: Exception) {
                        contentResolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunoTools/ImageSets")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { output: OutputStream -> input.copyTo(output) }
                    android.media.MediaScannerConnection.scanFile(
                        this@ImageSetResultActivity,
                        arrayOf(file.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun guessImageExtension(imageUrl: String, contentType: String?): String {
        val fromMime = contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!fromMime.isNullOrBlank()) return if (fromMime == "jpeg") "jpg" else fromMime

        val cleanPath = runCatching { URL(imageUrl).path }.getOrDefault(imageUrl).substringBefore('?').substringBefore('#')
        val ext = cleanPath.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> if (ext == "jpeg") "jpg" else ext
            else -> "jpg"
        }
    }

    companion object {
        /**
         * 单次弹簧动画：缩到0.9然后弹回1.0，一气呵成
         */
        fun playBounce(view: View) {
            view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(80)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .interpolator = OvershootInterpolator(3f)
                }
        }
    }

    class ImageGridAdapter(
        private val images: List<String>,
        private val onSelectionChanged: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<ImageGridAdapter.ViewHolder>() {

        private val selected = mutableSetOf<Int>()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivImage: ShapeableImageView = itemView.findViewById(R.id.ivImage)
            val checkBg: View = itemView.findViewById(R.id.checkBg)
            val ivCheck: ImageView = itemView.findViewById(R.id.ivCheck)

            init {
                itemView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                    // 单次流畅弹动
                    playBounce(ivImage)

                    if (selected.contains(pos)) {
                        selected.remove(pos)
                        checkBg.setBackgroundResource(R.drawable.bg_circle_check)
                        ivCheck.visibility = View.GONE
                        onSelectionChanged(pos, false)
                    } else {
                        selected.add(pos)
                        checkBg.setBackgroundResource(R.drawable.bg_circle_blue)
                        ivCheck.visibility = View.VISIBLE
                        onSelectionChanged(pos, true)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_grid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = images[position]
            val itemWidth = holder.itemView.context.resources.displayMetrics.widthPixels / 3 - 24
            holder.ivImage.layoutParams.height = (itemWidth * 1.5).toInt()

            Glide.with(holder.itemView.context)
                .load(url)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivImage)

            if (selected.contains(position)) {
                holder.ivCheck.visibility = View.VISIBLE
                holder.checkBg.setBackgroundResource(R.drawable.bg_circle_blue)
            } else {
                holder.ivCheck.visibility = View.GONE
                holder.checkBg.setBackgroundResource(R.drawable.bg_circle_check)
            }
        }

        override fun getItemCount() = images.size
    }
}