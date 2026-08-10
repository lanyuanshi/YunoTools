package com.yuno.tools.ui.profile

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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

class MemberMagnetPlayerActivity : Activity() {
    private var player: ExoPlayer? = null
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

        if (!isMagnet && isDirectMediaUrl(url)) {
            buildDirectPlayer(url, title)
        } else {
            buildMagnetLocalPage(url, title, hash)
        }
    }

    private fun buildDirectPlayer(url: String, title: String) {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val pv = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3000
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
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

    private fun buildMagnetLocalPage(raw: String, title: String, hash: String) {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(84), dp(18), dp(24))
            setBackgroundColor(Color.BLACK)
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        root.addView(topBar(title, "本地磁力页"), FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP))
        setContentView(root)

        page.addView(TextView(this).apply {
            text = "本地磁力解析"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        page.addView(TextView(this).apply {
            text = "当前版本不跳外部网站，磁力只在本地解析展示；如需播放，请交给系统下载器、支持磁力的云播 App，或粘贴可直接播放的 m3u8/mp4 直链。"
            textSize = 14f
            setLineSpacing(dp(5).toFloat(), 1f)
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(10), 0, dp(18))
        })
        page.addView(infoLine("类型", "磁力链接"))
        page.addView(infoLine("Hash", hash.ifBlank { "未识别" }))
        page.addView(infoLine("链接", raw.take(180) + if (raw.length > 180) "…" else ""))
        page.addView(Button(this).apply {
            text = "复制磁力链接"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#7C3AED"), dp(16), Color.TRANSPARENT, 0)
            setOnClickListener { copy(raw) }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })
        page.addView(Button(this).apply {
            text = "交给下载器"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#2563EB"), dp(16), Color.TRANSPARENT, 0)
            setOnClickListener { openExternal(raw) }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        page.addView(Button(this).apply {
            text = "关闭"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#1F2937"), dp(16), Color.parseColor("#334155"), 1)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        showChromeTemporarily()
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

    private fun infoLine(k: String, v: String) = TextView(this).apply {
        text = "$k：$v"
        textSize = 14f
        setTextColor(Color.parseColor("#E2E8F0"))
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun showChromeTemporarily() {
        setChromeVisible(true)
        chrome?.removeCallbacks(hideRunnable)
        chrome?.postDelayed(hideRunnable, 2500)
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

    private fun rounded(color: Int, radius: Int, stroke: Int, strokeWidth: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
