package com.yuno.tools.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier

class UpdateWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: UPDATE_URL
        setContentView(createContentView())
        ThemeApplier.apply(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = android.view.View.GONE
            }
        }
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out)
    }

    private fun createContentView(): ViewGroup {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        }
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt())
        })
        bar.addView(TextView(this).apply {
            text = "检查更新"
            textSize = 20f
            setTextColor(Color.parseColor("#111827"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(bar)
        root.addView(TextView(this).apply {
            text = "正在应用内加载更新目录，请自行查看 APK 文件名和版本号。"
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (8 * density).toInt())
        })
        val webBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt(), Gravity.CENTER)
        }
        webBox.addView(webView)
        webBox.addView(progress)
        root.addView(webBox)
        return root
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val UPDATE_URL = "https://github.com/lanyuanshi/YunoTools"

        fun createIntent(context: Context, url: String): Intent {
            return Intent(context, UpdateWebActivity::class.java).putExtra(EXTRA_URL, url)
        }
    }
}
