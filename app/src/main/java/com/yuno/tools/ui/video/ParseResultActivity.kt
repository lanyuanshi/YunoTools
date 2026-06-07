package com.yuno.tools.ui.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import java.io.FileOutputStream
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
                downloadFile(coverUrl, "cover_${System.currentTimeMillis()}.jpg", "图片")
            } else {
                Toast.makeText(this, "封面链接无效", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveContent.setOnClickListener {
            val videoUrl = result?.videoUrl
            if (!videoUrl.isNullOrEmpty()) {
                downloadFile(videoUrl, "video_${System.currentTimeMillis()}.mp4", "视频")
            } else {
                Toast.makeText(this, "视频链接无效", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFile(fileUrl: String, fileName: String, type: String) {
        Toast.makeText(this, "开始下载${type}...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode == 200) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    FileOutputStream(file).use { output ->
                        connection.inputStream.use { input ->
                            input.copyTo(output)
                        }
                    }
                    // 通知系统扫描新文件
                    MediaScannerConnection.scanFile(
                        this@ParseResultActivity,
                        arrayOf(file.absolutePath),
                        null
                    ) { _, _ -> }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ParseResultActivity, "${type}已保存到下载目录", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ParseResultActivity, "下载失败: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
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