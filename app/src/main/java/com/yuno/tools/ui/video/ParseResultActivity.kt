package com.yuno.tools.ui.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.VideoParseResult
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ParseResultActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var result: VideoParseResult? = null

    private lateinit var playerView: PlayerView
    private lateinit var tvContent: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnCopyContent: ImageButton
    private lateinit var btnSaveCover: MaterialCardView
    private lateinit var btnSaveContent: MaterialCardView
    private lateinit var downloadPanel: LinearLayout
    private lateinit var downloadProgress: ProgressBar
    private lateinit var tvDownloadStatus: TextView
    private lateinit var tvDownloadPercent: TextView
    private var downloadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parse_result)

        result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("result", VideoParseResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("result")
        }

        if (result == null) {
            Toast.makeText(this, "解析数据无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupUI()
        setupPlayer()
        setupListeners()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        tvContent = findViewById(R.id.tvContent)
        btnBack = findViewById(R.id.btnBack)
        btnCopyContent = findViewById(R.id.btnCopyContent)
        btnSaveCover = findViewById(R.id.btnSaveCover)
        btnSaveContent = findViewById(R.id.btnSaveContent)
        downloadPanel = findViewById(R.id.downloadProgressPanel)
        downloadProgress = findViewById(R.id.downloadProgress)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
        tvDownloadPercent = findViewById(R.id.tvDownloadPercent)
    }

    private fun setupUI() {
        val data = result!!
        val text = when {
            data.content.isNotEmpty() -> data.content
            data.title.isNotEmpty() -> data.title
            else -> "暂无文案"
        }
        tvContent.text = text
    }

    private fun setupPlayer() {
        val videoUrl = result?.videoUrl ?: return
        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "无视频链接", Toast.LENGTH_SHORT).show()
            return
        }

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
        playerView.player = player
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnCopyContent.setOnClickListener {
            val text = tvContent.text.toString()
            if (text.isNotEmpty() && text != "暂无文案") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("文案", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "文案已复制", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveCover.setOnClickListener {
            val coverUrl = result?.coverUrl
            if (!coverUrl.isNullOrEmpty()) {
                downloadFile(coverUrl, "cover_${System.currentTimeMillis()}.jpg", "图片", "image/jpeg")
            } else {
                Toast.makeText(this, "封面链接无效", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveContent.setOnClickListener {
            val videoUrl = result?.videoUrl
            if (!videoUrl.isNullOrEmpty()) {
                downloadFile(videoUrl, "video_${System.currentTimeMillis()}.mp4", "视频", "video/mp4")
            } else {
                Toast.makeText(this, "视频链接无效", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFile(fileUrl: String, fileName: String, type: String, mimeType: String) {
        if (downloadJob?.isActive == true) return
        setDownloadState(true, "正在准备${type}", 0)
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            var pendingUri: Uri? = null
            try {
                val connection = (URL(fileUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val isVideo = mimeType.startsWith("video/")
                val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relativeDir = if (isVideo) Environment.DIRECTORY_MOVIES + "/YunoTools" else Environment.DIRECTORY_PICTURES + "/YunoTools"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(collection, values) ?: error("无法创建媒体文件")
                pendingUri = uri
                val total = connection.contentLengthLong
                var copied = 0L
                contentResolver.openOutputStream(uri)?.use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            val percent = if (total > 0) ((copied * 100) / total).toInt().coerceIn(0, 99) else -1
                            withContext(Dispatchers.Main) { setDownloadState(true, "正在保存$type", percent) }
                        }
                    }
                } ?: error("无法写入媒体文件")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                withContext(Dispatchers.Main) {
                    setDownloadState(false, "${type}已保存到媒体库 / YunoTools", 100)
                    Toast.makeText(this@ParseResultActivity, "${type}已保存", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                pendingUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                withContext(Dispatchers.Main) {
                    setDownloadState(false, "保存失败，请稍后重试", 0)
                    Toast.makeText(this@ParseResultActivity, "下载错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setDownloadState(active: Boolean, status: String, percent: Int) {
        downloadPanel.visibility = View.VISIBLE
        tvDownloadStatus.text = status
        if (percent >= 0) {
            downloadProgress.isIndeterminate = false
            downloadProgress.progress = percent
            tvDownloadPercent.text = "$percent%"
        } else {
            downloadProgress.isIndeterminate = true
            tvDownloadPercent.text = ""
        }
        btnSaveCover.isEnabled = !active
        btnSaveContent.isEnabled = !active
        if (!active && percent == 100) downloadPanel.postDelayed({ downloadPanel.visibility = View.GONE }, 2600)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}