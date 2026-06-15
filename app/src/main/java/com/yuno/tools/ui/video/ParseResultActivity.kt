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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        Toast.makeText(this, "开始下载${type}...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            var pendingUri: Uri? = null
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode == 200) {
                    val isVideo = mimeType.startsWith("video/")
                    val collection = if (isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                    val relativeDir = if (isVideo) {
                        Environment.DIRECTORY_MOVIES + "/YunoTools"
                    } else {
                        Environment.DIRECTORY_PICTURES + "/YunoTools"
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = contentResolver
                    val uri = resolver.insert(collection, values) ?: error("无法创建媒体文件")
                    pendingUri = uri
                    resolver.openOutputStream(uri)?.use { output ->
                        connection.inputStream.use { input -> input.copyTo(output) }
                    } ?: error("无法写入媒体文件")

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ParseResultActivity, "${type}已保存到系统媒体库/YunoTools", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ParseResultActivity, "下载失败: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                pendingUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParseResultActivity, "下载错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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