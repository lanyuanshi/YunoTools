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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.math.roundToInt

class MemberMagnetActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var resultBox: LinearLayout
    private var parsed: MagnetInfo? = null
    private var resourceId: String = ""
    private var resourceName: String = ""
    private val apiBase = "https://api.webtor.io/v1"

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
        titles.addView(TextView(this).apply { text = "Webtor API 解析文件列表，支持文件播放和下载"; textSize = 13f; setTextColor(Color.parseColor("#C7D2FE")) })
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
        row.addView(actionButton("解析文件", "#7C3AED") { parseInput() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        row.addView(actionButton("粘贴", "#0F172A") { paste() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(resultBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })

        val note = card(Color.parseColor("#EEF2FF"))
        note.addView(TextView(this).apply {
            text = "说明：磁力会通过 Webtor API 解析为真实文件列表。点“播放”会请求 stream 导出并交给本地播放器；点“下载”会请求 download 导出并交给系统下载器/浏览器。请只解析你有权访问的内容。"
            textSize = 13f; setLineSpacing(dp(4).toFloat(), 1f); setTextColor(Color.parseColor("#475569")); setPadding(dp(16))
        })
        content.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun parseInput() {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) { toast("请先粘贴链接"); return }
        parsed = parseMagnet(raw)
        if (!parsed!!.isMagnet) {
            renderDirectLink(raw, parsed!!)
            return
        }
        resultBox.removeAllViews()
        resultBox.addView(TextView(this).apply {
            text = "正在解析磁力资源…\n首次解析需要从 BT 网络获取种子信息，Webtor API 最长可能等待 3 分钟。"
            textSize = 14f; setLineSpacing(dp(4).toFloat(), 1f); setTextColor(Color.parseColor("#475569")); setPadding(0, dp(8), 0, dp(8))
        })
        Thread { resolveMagnet(raw, parsed!!) }.start()
    }

    private fun renderDirectLink(raw: String, info: MagnetInfo) {
        resultBox.removeAllViews()
        resultBox.addView(TextView(this).apply { text = "直链结果"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        resultBox.addView(infoLine("名称", info.name.ifBlank { "视频直链" }))
        resultBox.addView(infoLine("链接", raw.take(150) + if (raw.length > 150) "…" else ""))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("播放", "#10B981") { startPlayer(raw, info.name.ifBlank { "在线播放" }) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        row.addView(actionButton("下载", "#2563EB") { openExternal(raw) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })
        resultBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun resolveMagnet(raw: String, info: MagnetInfo) {
        try {
            val resource = postResource(raw)
            resourceId = resource.optString("id", info.hash).ifBlank { info.hash }
            resourceName = resource.optString("name", info.name).ifBlank { info.name.ifBlank { "磁力资源" } }
            val files = mutableListOf<TorrentFile>()
            resource.optJSONObject("file")?.let { files.add(parseFile(it)) }
            if (files.isEmpty()) {
                val list = getJson("$apiBase/resource/${Uri.encode(resourceId)}/list?output=tree&limit=2000&sort=size")
                collectFiles(list, files)
            }
            val sorted = files.filter { it.type == "file" }.sortedWith(compareByDescending<TorrentFile> { it.mediaFormat == "video" }.thenByDescending { it.size })
            runOnUiThread { renderFileList(resource, sorted) }
        } catch (e: Exception) {
            runOnUiThread {
                resultBox.removeAllViews()
                resultBox.addView(TextView(this).apply { text = "解析失败：${e.message ?: "未知错误"}"; textSize = 14f; setTextColor(Color.parseColor("#DC2626")); setPadding(0, dp(8), 0, dp(8)) })
                resultBox.addView(actionButton("复制磁力链接", "#64748B") { copy(raw) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
                resultBox.addView(actionButton("交给下载器", "#2563EB") { openExternal(raw) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
            }
        }
    }

    private fun renderFileList(resource: JSONObject, files: List<TorrentFile>) {
        resultBox.removeAllViews()
        resultBox.addView(TextView(this).apply { text = resourceName; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        resultBox.addView(infoLine("Resource ID", resourceId))
        resultBox.addView(infoLine("总大小", formatSize(resource.optLong("size", 0))))
        resultBox.addView(infoLine("文件数", "${files.size}"))
        if (files.isEmpty()) {
            resultBox.addView(TextView(this).apply { text = "没有解析到文件。可稍后重试，或确认磁力链接有可用 Tracker/做种。"; textSize = 14f; setTextColor(Color.parseColor("#DC2626")); setPadding(0, dp(10), 0, dp(10)) })
            return
        }
        files.take(60).forEach { file -> resultBox.addView(fileRow(file), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }) }
        if (files.size > 60) resultBox.addView(infoLine("提示", "仅显示前 60 个文件，已按视频优先和大小排序"))
    }

    private fun fileRow(file: TorrentFile): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12)); background = rounded(Color.parseColor("#F8FAFC"), dp(16), Color.parseColor("#E2E8F0"), 1) }
        box.addView(TextView(this).apply { text = file.name; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")); maxLines = 2 })
        box.addView(TextView(this).apply { text = "${file.mediaFormat.ifBlank { "unknown" }} · ${formatSize(file.size)} · index=${file.index}"; textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding(0, dp(4), 0, dp(8)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton(if (file.mediaFormat == "video" || isVideoName(file.name)) "播放" else "导出播放", "#10B981") { exportAndPlay(file) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(5) })
        row.addView(actionButton("下载", "#2563EB") { exportAndDownload(file) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(5) })
        box.addView(row)
        return box
    }

    private fun exportAndPlay(file: TorrentFile) {
        toast("正在获取播放地址…")
        Thread {
            try {
                val url = exportUrl(file, "stream")
                runOnUiThread { startPlayer(url, file.name) }
            } catch (e: Exception) {
                runOnUiThread { toast("播放导出失败：${e.message ?: "未知错误"}") }
            }
        }.start()
    }

    private fun exportAndDownload(file: TorrentFile) {
        toast("正在获取下载地址…")
        Thread {
            try {
                val url = exportUrl(file, "download")
                runOnUiThread { openExternal(url) }
            } catch (e: Exception) {
                runOnUiThread { toast("下载导出失败：${e.message ?: "未知错误"}") }
            }
        }.start()
    }

    private fun exportUrl(file: TorrentFile, output: String): String {
        val contentId = if (file.index >= 0) file.index.toString() else file.id
        val json = getJson("$apiBase/resource/${Uri.encode(resourceId)}/export/${Uri.encode(contentId)}?output=$output")
        val exports = json.optJSONObject("exports") ?: throw IllegalStateException("没有导出链接")
        val preferredKeys = if (output == "stream") listOf("stream", "download", "video", "default") else listOf("download", "stream", "default")
        for (key in preferredKeys) exports.optJSONObject(key)?.let { pickExportUrl(it)?.let { u -> return u } }
        val it = exports.keys()
        while (it.hasNext()) exports.optJSONObject(it.next())?.let { item -> pickExportUrl(item)?.let { u -> return u } }
        throw IllegalStateException("没有可用 URL")
    }

    private fun pickExportUrl(item: JSONObject): String? {
        item.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        val tag = item.optJSONObject("html_tag")
        tag?.optString("src")?.takeIf { it.startsWith("http") }?.let { return it }
        val sources = tag?.optJSONArray("sources")
        if (sources != null) for (i in 0 until sources.length()) sources.optJSONObject(i)?.optString("src")?.takeIf { it.startsWith("http") }?.let { return it }
        return null
    }

    private fun startPlayer(url: String, title: String) {
        startActivity(Intent(this, MemberMagnetPlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
            putExtra("isMagnet", false)
            putExtra("forceDirect", true)
        })
    }

    private fun postResource(raw: String): JSONObject {
        val conn = (URL("$apiBase/resource/").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 190000
            doOutput = true
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "YunoTools/1.2.44")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(raw) }
        return readJson(conn)
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "YunoTools/1.2.44")
        }
        return readJson(conn)
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty().ifBlank { body.take(120) }
            throw IllegalStateException("HTTP $code $msg")
        }
        return JSONObject(body)
    }

    private fun collectFiles(obj: JSONObject, out: MutableList<TorrentFile>) {
        if (obj.optString("type") == "file") out.add(parseFile(obj))
        val items = obj.optJSONArray("items") ?: return
        for (i in 0 until items.length()) items.optJSONObject(i)?.let { collectFiles(it, out) }
    }

    private fun parseFile(obj: JSONObject) = TorrentFile(
        id = obj.optString("id"),
        index = if (obj.has("index")) obj.optInt("index") else -1,
        name = obj.optString("name").ifBlank { obj.optString("path").substringAfterLast('/') },
        path = obj.optString("path"),
        size = obj.optLong("size", 0),
        type = obj.optString("type"),
        mediaFormat = obj.optString("media_format"),
        mimeType = obj.optString("mime_type"),
    )

    private fun paste() {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) toast("剪贴板为空") else input.setText(text)
    }

    private fun copy(raw: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("magnet", raw))
        toast("已复制")
    }

    private fun openExternal(raw: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            .onFailure { copy(raw); toast("未找到可处理应用，已复制链接") }
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
    private data class TorrentFile(val id: String, val index: Int, val name: String, val path: String, val size: Long, val type: String, val mediaFormat: String, val mimeType: String)
    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    private fun isVideoName(name: String) = name.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".m3u8") || it.endsWith(".webm") || it.endsWith(".m4v") || it.endsWith(".avi") || it.endsWith(".mov") }
    private fun formatSize(bytes: Long): String { if (bytes <= 0) return "未知大小"; val units = arrayOf("B", "KB", "MB", "GB", "TB"); var v = bytes.toDouble(); var i = 0; while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }; return if (i == 0) "${bytes}B" else "%.2f%s".format(v, units[i]) }
    private fun infoLine(k: String, v: String) = TextView(this).apply { text = "$k：$v"; textSize = 14f; setTextColor(Color.parseColor("#475569")); setPadding(0, dp(5), 0, dp(5)) }
    private fun actionButton(t: String, color: String, click: () -> Unit) = Button(this).apply { text = t; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = rounded(Color.parseColor(color), dp(16), Color.TRANSPARENT, 0); setOnClickListener { click() } }
    private fun card(color: Int) = LinearLayout(this).apply { background = rounded(color, dp(24), Color.parseColor("#33FFFFFF"), 1); elevation = dp(3).toFloat() }
    private fun rounded(color: Int, radius: Int, stroke: Int, strokeWidth: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke) }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
