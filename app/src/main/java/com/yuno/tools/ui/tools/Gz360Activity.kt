package com.yuno.tools.ui.tools

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier

class Gz360Activity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        ThemeApplier.apply(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                status.text = "正在加载网页..."
            }
            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                status.text = "已在应用内打开 · 需要科学上网"
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) {
                    progress.visibility = View.GONE
                    status.text = "网页加载失败，请检查科学上网后点击刷新"
                }
            }
        }
        webView.loadUrl(URL)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun createContentView(): ViewGroup {
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        }
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "返回"
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
        })
        bar.addView(TextView(this).apply {
            text = "更多资源"
            textSize = 20f
            setTextColor(Color.parseColor("#111827"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "刷新"
            setOnClickListener { webView.reload() }
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
        })
        root.addView(bar)
        status = TextView(this).apply {
            text = "正在加载网页..."
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (8 * d).toInt())
        }
        root.addView(status)
        val box = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        webView = WebView(this).apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }
        progress = ProgressBar(this).apply { layoutParams = FrameLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt(), Gravity.CENTER) }
        box.addView(webView)
        box.addView(progress)
        root.addView(box)
        return root
    }

    companion object {
        private const val URL = "https://gz360.tv/home"
    }
}