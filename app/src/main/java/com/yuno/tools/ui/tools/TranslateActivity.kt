package com.yuno.tools.ui.tools

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class TranslateActivity : AppCompatActivity() {
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var inputText: EditText
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView
    private lateinit var translateButton: Button
    private lateinit var copyButton: TextView
    private lateinit var shareButton: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    private val languages = listOf(
        Lang("自动检测", "auto"),
        Lang("中文", "zh-CN"),
        Lang("英语", "en"),
        Lang("日语", "ja"),
        Lang("韩语", "ko"),
        Lang("法语", "fr"),
        Lang("德语", "de"),
        Lang("西班牙语", "es"),
        Lang("俄语", "ru"),
        Lang("葡萄牙语", "pt"),
        Lang("意大利语", "it"),
        Lang("阿拉伯语", "ar"),
        Lang("泰语", "th"),
        Lang("越南语", "vi"),
        Lang("印尼语", "id"),
        Lang("马来语", "ms")
    )

    private val targetLanguages = languages.filter { it.code != "auto" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F8FBFF"), Color.parseColor("#EEF2FF"), Color.parseColor("#FDF2F8"))
            )
        }

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#111827"))
            background = rounded(Color.WHITE, dp(18), Color.parseColor("#55FFFFFF"), 1)
            elevation = dp(2).toFloat()
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "智能翻译"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        })
        titleBox.addView(TextView(this).apply {
            text = "多语言文本互译 · 自动检测 · 复制分享"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        val langCard = glassCard()
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14))
        }
        sourceSpinner = makeSpinner(languages)
        targetSpinner = makeSpinner(targetLanguages)
        targetSpinner.setSelection(targetLanguages.indexOfFirst { it.code == "en" }.coerceAtLeast(0))
        val swap = TextView(this).apply {
            text = "⇄"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#6366F1"), dp(18), Color.TRANSPARENT, 0)
            elevation = dp(3).toFloat()
            setOnClickListener { swapLanguages() }
        }
        langRow.addView(sourceSpinner, LinearLayout.LayoutParams(0, dp(48), 1f))
        langRow.addView(swap, LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(10); rightMargin = dp(10) })
        langRow.addView(targetSpinner, LinearLayout.LayoutParams(0, dp(48), 1f))
        langCard.addView(langRow)
        content.addView(langCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val inputCard = glassCard()
        val inputBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        inputBox.addView(TextView(this).apply {
            text = "输入文本"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#334155"))
        })
        inputText = EditText(this).apply {
            hint = "请输入要翻译的文字……"
            textSize = 17f
            minLines = 5
            maxLines = 10
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.parseColor("#111827"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = rounded(Color.parseColor("#66FFFFFF"), dp(16), Color.parseColor("#DDE7F3"), 1)
            setPadding(dp(14))
        }
        inputBox.addView(inputText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val paste = miniAction("粘贴") { pasteText() }
        val clear = miniAction("清空") { inputText.setText(""); resultText.text = "翻译结果会显示在这里"; statusText.text = "准备就绪" }
        quickRow.addView(paste, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(8) })
        quickRow.addView(clear, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(8) })
        inputBox.addView(quickRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        inputCard.addView(inputBox)
        content.addView(inputCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        translateButton = Button(this).apply {
            text = "立即翻译"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#4F46E5"), dp(18), Color.TRANSPARENT, 0)
            setOnClickListener { translate() }
        }
        content.addView(translateButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })

        val resultCard = glassCard()
        val resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        val resultHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        resultHeader.addView(TextView(this).apply {
            text = "翻译结果"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#334155"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        copyButton = miniAction("复制") { copyResult() }
        shareButton = miniAction("分享") { shareResult() }
        resultHeader.addView(copyButton, LinearLayout.LayoutParams(dp(64), dp(36)).apply { rightMargin = dp(8) })
        resultHeader.addView(shareButton, LinearLayout.LayoutParams(dp(64), dp(36)))
        resultBox.addView(resultHeader)
        resultText = TextView(this).apply {
            text = "翻译结果会显示在这里"
            textSize = 18f
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setTextColor(Color.parseColor("#111827"))
            background = rounded(Color.parseColor("#77FFFFFF"), dp(16), Color.parseColor("#E2E8F0"), 1)
            setPadding(dp(14))
        }
        resultText.minHeight = dp(120)
        resultBox.addView(resultText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        statusText = TextView(this).apply {
            text = "准备就绪"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        }
        resultBox.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        resultCard.addView(resultBox)
        content.addView(resultCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val tips = TextView(this).apply {
            text = "提示：翻译功能需要联网。公共接口可能受网络环境影响，失败时可稍后重试。"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
        }
        content.addView(tips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun makeSpinner(items: List<Lang>): Spinner {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.name })
        return Spinner(this).apply {
            this.adapter = adapter
            background = rounded(Color.parseColor("#88FFFFFF"), dp(16), Color.parseColor("#D8E3F0"), 1)
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun translate() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) {
            toast("请输入要翻译的内容")
            return
        }
        hideKeyboard()
        val source = languages[sourceSpinner.selectedItemPosition].code
        val target = targetLanguages[targetSpinner.selectedItemPosition].code
        setLoading(true, "正在翻译……")
        thread {
            try {
                val translated = requestTranslate(text, source, target)
                mainHandler.post {
                    resultText.text = translated.ifBlank { "未获得翻译结果" }
                    statusText.text = "翻译完成：${langName(source)} → ${langName(target)}"
                    setLoading(false, null)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    resultText.text = "翻译失败，请检查网络后重试。"
                    statusText.text = "错误：${e.message ?: e.javaClass.simpleName}"
                    setLoading(false, null)
                }
            }
        }
    }

    private fun requestTranslate(text: String, source: String, target: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
            URLEncoder.encode(source, "UTF-8") +
            "&tl=" + URLEncoder.encode(target, "UTF-8") +
            "&dt=t&q=" + URLEncoder.encode(text, "UTF-8")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools Translate")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) error("HTTP $code")
            val arr = JSONArray(body)
            val parts = arr.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val seg = parts.getJSONArray(i).optString(0)
                sb.append(seg)
            }
            sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun swapLanguages() {
        val sPos = sourceSpinner.selectedItemPosition
        val tLang = targetLanguages[targetSpinner.selectedItemPosition]
        if (languages[sPos].code == "auto") {
            toast("自动检测无法直接交换，请先选择源语言")
            return
        }
        val newSource = languages.indexOfFirst { it.code == tLang.code }
        val newTarget = targetLanguages.indexOfFirst { it.code == languages[sPos].code }
        if (newSource >= 0 && newTarget >= 0) {
            sourceSpinner.setSelection(newSource)
            targetSpinner.setSelection(newTarget)
            val oldInput = inputText.text.toString()
            val oldResult = resultText.text.toString()
            if (oldInput.isNotBlank() && oldResult.isNotBlank() && oldResult != "翻译结果会显示在这里") {
                inputText.setText(oldResult)
                inputText.setSelection(inputText.text.length)
                resultText.text = oldInput
            }
        }
    }

    private fun pasteText() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) toast("剪贴板为空") else {
            inputText.setText(text)
            inputText.setSelection(inputText.text.length)
        }
    }

    private fun copyResult() {
        val text = resultText.text.toString()
        if (text.isBlank() || text == "翻译结果会显示在这里" || text.startsWith("翻译失败")) {
            toast("暂无可复制的翻译结果")
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("翻译结果", text))
        toast("已复制")
    }

    private fun shareResult() {
        val text = resultText.text.toString()
        if (text.isBlank() || text == "翻译结果会显示在这里") {
            toast("暂无可分享的翻译结果")
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "分享翻译结果"))
    }

    private fun setLoading(loading: Boolean, status: String?) {
        translateButton.isEnabled = !loading
        translateButton.text = if (loading) "翻译中……" else "立即翻译"
        translateButton.alpha = if (loading) 0.72f else 1f
        if (status != null) statusText.text = status
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputText.windowToken, 0)
    }

    private fun glassCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.parseColor("#BFFFFFFF"), dp(22), Color.parseColor("#AAFFFFFF"), 1)
        elevation = dp(4).toFloat()
    }

    private fun miniAction(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#4F46E5"))
        background = rounded(Color.parseColor("#EEF2FF"), dp(14), Color.parseColor("#C7D2FE"), 1)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
    }

    private fun langName(code: String): String = languages.firstOrNull { it.code == code }?.name ?: code
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    data class Lang(val name: String, val code: String)
}
