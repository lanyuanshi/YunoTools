package com.yuno.tools.ui.tools

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class PokiGamesActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private val gamesUrl = "https://poki.com/zh"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            addView(header())
            progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                progressDrawable.setTint(Color.parseColor("#7C3AED"))
                layoutParams = LinearLayout.LayoutParams(-1, dp(3))
            }
            addView(progress)
            addView(toolbar())
            addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                webView = WebView(context).apply {
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
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            return if (url.startsWith("http://") || url.startsWith("https://")) {
                                view.loadUrl(url)
                                true
                            } else {
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                true
                            }
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            statusText.text = "已加载 Poki 小游戏，可选择网页内游戏游玩"
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) statusText.text = "加载失败：请检查网络后点刷新"
                        }
                    }
                    loadUrl(gamesUrl)
                }
                addView(webView, FrameLayout.LayoutParams(-1, -1))
            })
        })
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(10))
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#F5F3FF"), Color.parseColor("#F8FAFC")))
        addView(TextView(context).apply {
            text = "Poki 小游戏"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2E1065"))
        })
        statusText = TextView(context).apply {
            text = "正在加载 https://poki.com/zh"
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(4), 0, 0)
        }
        addView(statusText)
    }

    private fun toolbar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded("#EFFFFFFF", 0)
        addView(toolButton("刷新") { webView.reload() })
        addView(toolButton("后退") { if (webView.canGoBack()) webView.goBack() })
        addView(toolButton("前进") { if (webView.canGoForward()) webView.goForward() })
        addView(toolButton("浏览器") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gamesUrl))) })
    }

    private fun toolButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 13f
        setTextColor(Color.WHITE)
        background = rounded("#7C3AED", 16)
        stateListAnimator = null
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
    }

    private fun rounded(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
