package com.yuno.tools.ui.tools

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class PokiGamesActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var menuPanel: LinearLayout
    private lateinit var menuButton: TextView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val gamesUrl = "https://poki.com/zh"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@PokiGamesActivity.progress.progress = newProgress
                    this@PokiGamesActivity.progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    showWebFullScreen(view, callback, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
                }

                @Suppress("DEPRECATION")
                override fun onShowCustomView(view: View?, requestedOrientation: Int, callback: CustomViewCallback?) {
                    if (view == null) return
                    showWebFullScreen(view, callback, requestedOrientation)
                }

                override fun onHideCustomView() { hideWebFullScreen() }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    return if (url.startsWith("http://") || url.startsWith("https://")) {
                        view.loadUrl(url); true
                    } else {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        true
                    }
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) Toast.makeText(this@PokiGamesActivity, "加载失败，请检查网络", Toast.LENGTH_SHORT).show()
                }
            }
            loadUrl(gamesUrl)
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setTint(Color.parseColor("#7C3AED"))
        }
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))
        addFloatingMenu()
    }

    private fun addFloatingMenu() {
        menuPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = rounded("#E61F2937", 22)
            elevation = dp(10).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(icon("↻", "刷新") { webView.reload(); toggleMenu(false) })
            addView(icon("‹", "后退") { if (webView.canGoBack()) webView.goBack(); toggleMenu(false) })
            addView(icon("›", "前进") { if (webView.canGoForward()) webView.goForward(); toggleMenu(false) })
            addView(icon("⛶", "浏览器") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gamesUrl))); toggleMenu(false) })
            addView(icon("✕", "关闭") { finish() })
        }
        root.addView(menuPanel, FrameLayout.LayoutParams(dp(56), -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(42); rightMargin = dp(10) })
        menuButton = TextView(this).apply {
            text = "⋮"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded("#CC111827", 22)
            elevation = dp(12).toFloat()
            setOnClickListener { toggleMenu(menuPanel.visibility != View.VISIBLE) }
        }
        root.addView(menuButton, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); rightMargin = dp(10) })
    }

    private fun icon(symbol: String, desc: String, action: () -> Unit) = TextView(this).apply {
        text = symbol
        contentDescription = desc
        textSize = 24f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { bottomMargin = dp(4) }
    }

    private fun toggleMenu(show: Boolean) {
        menuPanel.visibility = if (show) View.VISIBLE else View.GONE
        menuButton.text = if (show) "×" else "⋮"
    }

    private fun showWebFullScreen(view: View, callback: WebChromeClient.CustomViewCallback?, requested: Int) {
        if (customView != null) { callback?.onCustomViewHidden(); return }
        customView = view
        customViewCallback = callback
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        webView.visibility = View.GONE
        menuButton.visibility = View.GONE
        menuPanel.visibility = View.GONE
        requestedOrientation = when (requested) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> requested
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        enterImmersive()
    }

    private fun hideWebFullScreen() {
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        enterImmersive()
    }

    private fun enterImmersive() {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onBackPressed() {
        when {
            customView != null -> hideWebFullScreen()
            menuPanel.visibility == View.VISIBLE -> toggleMenu(false)
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun rounded(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}