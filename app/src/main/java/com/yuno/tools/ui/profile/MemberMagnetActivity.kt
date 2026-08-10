package com.yuno.tools.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.yuno.tools.data.AccountStore
import com.yuno.tools.util.ThemeApplier
import java.net.URLDecoder
import kotlin.math.roundToInt

class MemberMagnetActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var resultBox: LinearLayout
    private var parsed: MagnetInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        if (!AccountStore.hasVipAccess(this)) {
            Toast.makeText(this, "请先开通会员", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        buildUi()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#111827"), Color.parseColor("#312E81"), Color.parseColor("#F8FAFC")))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(22), dp(18), dp(30)) }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#33FFFFFF"), dp(18), Color.parseColor("#55FFFFFF"), 1)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titles.addView(TextView(this).apply { text = "磁力链接解析"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titles.addView(TextView(this).apply { text = "解析 magnet 信息，支持在线播放入口和下载器调用"; textSize = 13f; setTextColor(Color.parseColor("#C7D2FE")) })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        val card = card(Color.WHITE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(TextView(this).apply { text = "粘贴磁力链接 / 视频直链"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        input = EditText(this).apply {
            hint = "magnet:?xt=urn:btih:... 或 m3u8/mp4 直链"
            minLines = 4
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.parseColor("#111827"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = rounded(Color.parseColor("#F8FAFC"), dp(18), Color.parseColor("#E2E8F0"), 1)
            setPadding(dp(12))
        }
        box.addView(input, LinearLayout.LayoutParams(-1, dp(132)).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("解析", "#7C3AED") { parseInput() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        row.addView(actionButton("粘贴", "#0F172A") { paste() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(resultBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })

        val note = card(Color.parseColor("#EEF2FF"))
        note.addView(TextView(this).apply {
            text = "说明：磁力链接本身不是可直接播放的视频地址。App 会先解析 Hash、名称和 Tracker；在线播放会进入黑底沉浸式播放页：m3u8/mp4 直链直接播放，magnet 进入网页/云播放入口；下载会调用系统/第三方下载器处理磁力任务。"
            textSize = 13f; setLineSpacing(dp(4).toFloat(), 1f); setTextColor(Color.parseColor("#475569")); setPadding(dp(16))
        })
        content.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun parseInput() {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) { toast("请先粘贴链接"); return }
        parsed = parseMagnet(raw)
        renderResult(raw, parsed!!)
    }

    private fun renderResult(raw: String, info: MagnetInfo) {
        resultBox.removeAllViews()
        resultBox.addView(TextView(this).apply { text = "解析结果"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        resultBox.addView(infoLine("类型", if (info.isMagnet) "磁力链接" else "视频直链/普通链接"))
        resultBox.addView(infoLine("名称", info.name.ifBlank { "未提供" }))
        resultBox.addView(infoLine("Hash", info.hash.ifBlank { "未识别" }))
        if (info.trackers.isNotEmpty()) resultBox.addView(infoLine("Tracker", "${info.trackers.size} 个"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("在线播放", "#10B981") { play(raw, info) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        row.addView(actionButton("下载", "#2563EB") { download(raw) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })
        resultBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        resultBox.addView(actionButton("复制解析信息", "#64748B") { copyInfo(info) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
    }

    private fun play(raw: String, info: MagnetInfo) {
        startActivity(Intent(this, MemberMagnetPlayerActivity::class.java).apply {
            putExtra("url", raw)
            putExtra("title", info.name.ifBlank { if (info.isMagnet) "磁力在线播放" else "在线播放" })
            putExtra("hash", info.hash)
            putExtra("isMagnet", info.isMagnet)
        })
    }

    private fun download(raw: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }.onFailure {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("magnet", raw))
            toast("未找到可处理的下载器，已复制链接")
        }
    }

    private fun paste() {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) toast("剪贴板为空") else input.setText(text)
    }

    private fun copyInfo(info: MagnetInfo) {
        val text = "名称：${info.name}\nHash：${info.hash}\nTracker：${info.trackers.joinToString()}"
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("magnet-info", text))
        toast("已复制解析信息")
    }

    private fun parseMagnet(raw: String): MagnetInfo {
        val isMagnet = raw.startsWith("magnet:?", ignoreCase = true)
        if (!isMagnet) return MagnetInfo(false, "", raw.substringAfterLast('/').ifBlank { raw }, emptyList())
        val query = raw.substringAfter("magnet:?")
        val params = query.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to decode(part.substring(i + 1))
        }
        val xt = params.firstOrNull { it.first == "xt" }?.second.orEmpty()
        val hash = xt.substringAfterLast(':').takeIf { xt.contains("btih", ignoreCase = true) && it.length >= 32 }.orEmpty()
        val name = params.firstOrNull { it.first == "dn" }?.second.orEmpty()
        val trackers = params.filter { it.first == "tr" }.map { it.second }.distinct()
        return MagnetInfo(true, hash, name, trackers)
    }

    private data class MagnetInfo(val isMagnet: Boolean, val hash: String, val name: String, val trackers: List<String>)
    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    private fun infoLine(k: String, v: String) = TextView(this).apply { text = "$k：$v"; textSize = 14f; setTextColor(Color.parseColor("#475569")); setPadding(0, dp(5), 0, dp(5)) }
    private fun actionButton(t: String, color: String, click: () -> Unit) = Button(this).apply { text = t; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = rounded(Color.parseColor(color), dp(16), Color.TRANSPARENT, 0); setOnClickListener { click() } }
    private fun card(color: Int) = LinearLayout(this).apply { background = rounded(color, dp(24), Color.parseColor("#33FFFFFF"), 1); elevation = dp(3).toFloat() }
    private fun rounded(color: Int, radius: Int, stroke: Int, strokeWidth: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke) }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
