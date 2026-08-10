package com.yuno.tools.ui.profile

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URLEncoder
import kotlin.math.roundToInt

class MemberMagnetPlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var chrome: LinearLayout? = null
    private val hideRunnable = Runnable { setChromeVisible(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enterImmersive()

        val url = intent.getStringExtra("url").orEmpty()
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "会员在线播放" }
        val hash = intent.getStringExtra("hash").orEmpty()
        val isMagnet = intent.getBooleanExtra("isMagnet", url.startsWith("magnet:", true))

        if (url.isBlank()) {
            Toast.makeText(this, "链接为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!isMagnet && isDirectMediaUrl(url)) buildDirectPlayer(url, title) else buildMagnetWebPlayer(url, title, hash)
    }

    private fun buildDirectPlayer(url: String, title: String) {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val pv = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3000
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
        playerView = pv
        root.addView(pv, FrameLayout.LayoutParams(-1, -1))
        root.addView(topBar(title, "直链播放"), FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP))
        root.setOnClickListener { showChromeTemporarily() }
        setContentView(root)
        player = ExoPlayer.Builder(this).build().also { exo ->
            pv.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
        showChromeTemporarily()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildMagnetWebPlayer(raw: String, title: String, hash: String) {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url.toString()
                    return if (uri.startsWith("magnet:", true)) {
                        openExternal(uri); true
                    } else false
                }
            }
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(topBar(title, "磁力网页播放入口"), FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP))
        root.addView(bottomHint(raw), FrameLayout.LayoutParams(-1, dp(82), Gravity.BOTTOM))
        setContentView(root)

        val target = if (hash.isNotBlank()) {
            "https://webtor.io/" + Uri.encode(hash)
        } else {
            "https://webtor.io/#/show?magnet=" + URLEncoder.encode(raw, "UTF-8")
        }
        web.loadUrl(target)
        Toast.makeText(this, "磁力需由网页/云播放服务解析；如无法播放请使用下载器", Toast.LENGTH_LONG).show()
    }

    private fun topBar(title: String, mode: String): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
        }
        chrome = bar
        bar.addView(TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), -1))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1 })
        texts.addView(TextView(this).apply { text = mode; textSize = 12f; setTextColor(Color.parseColor("#CBD5E1")); maxLines = 1 })
        bar.addView(texts, LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(TextView(this).apply {
            text = "退出"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(64), -1))
        return bar
    }

    private fun bottomHint(raw: String): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
        }
        bar.addView(TextView(this).apply {
            text = "若网页无法解析，可复制链接或交给第三方下载器。"
            textSize = 13f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(TextView(this).apply {
            text = "复制"
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setOnClickListener { copy(raw) }
        }, LinearLayout.LayoutParams(dp(70), -1))
        bar.addView(TextView(this).apply {
            text = "下载"
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setOnClickListener { openExternal(raw) }
        }, LinearLayout.LayoutParams(dp(70), -1))
        return bar
    }

    private fun showChromeTemporarily() {
        setChromeVisible(true)
        chrome?.removeCallbacks(hideRunnable)
        chrome?.postDelayed(hideRunnable, 3000)
    }

    private fun setChromeVisible(show: Boolean) { chrome?.visibility = if (show) View.VISIBLE else View.GONE }

    private fun openExternal(raw: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw))) }
            .onFailure { copy(raw); Toast.makeText(this, "未找到可处理应用，已复制链接", Toast.LENGTH_SHORT).show() }
    }

    private fun copy(raw: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("magnet", raw))
        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterImmersive() }
    override fun onPause() { super.onPause(); player?.pause() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }

    private fun enterImmersive() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".m4v") || lower.contains(".webm") || lower.contains(".mkv"))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
