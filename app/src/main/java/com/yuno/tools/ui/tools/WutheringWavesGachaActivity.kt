package com.yuno.tools.ui.tools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WutheringWavesGachaActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ww_gacha_analysis", Context.MODE_PRIVATE) }
    private val client by lazy { OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build() }
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resultBox: LinearLayout
    private var records = mutableListOf<GachaRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        prefs.getString("last_input", "")?.let { input.setText(it) }
        prefs.getString("last_raw", "")?.takeIf { it.isNotBlank() }?.let { parseAndRender(it, "已加载上次分析缓存") }
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#201033"), Color.parseColor("#F5F3FF"))) }
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)) }
        scroll.addView(box); root.addView(scroll)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "鸣潮抽卡分析"; setTextColor(Color.WHITE); textSize = 27f; typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(pill("返回", "#334155") { finish() }, LinearLayout.LayoutParams(dp(74), dp(42)))
        box.addView(top)
        box.addView(TextView(this).apply { text = "粘贴脚本导出的抽卡链接、JSON 或文本记录，自动统计出金与保底。"; setTextColor(Color.parseColor("#DDD6FE")); textSize = 13f; setPadding(0, dp(8), 0, dp(14)) })

        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#FFFFFF", 24); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        input = EditText(this).apply {
            hint = "粘贴脚本生成的 URL / JSON / 抽卡文本\n例如包含 name、rankType、resourceId、time 等字段的记录"
            minLines = 4; gravity = Gravity.TOP; imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Color.parseColor("#0F172A")); setHintTextColor(Color.parseColor("#94A3B8")); textSize = 14f; background = bg("#F8FAFC", 16); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        card.addView(input, LinearLayout.LayoutParams(-1, dp(122)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        row.addView(pill("开始分析", "#7C3AED") { analyze() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row.addView(pill("粘贴剪贴板", "#2563EB") { pasteClipboard() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row.addView(pill("清空", "#64748B") { clearAll() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        card.addView(row)
        status = TextView(this).apply { text = "等待导入抽卡记录"; setTextColor(Color.parseColor("#475569")); textSize = 13f; setPadding(0, dp(12), 0, 0) }
        card.addView(status)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(progress, LinearLayout.LayoutParams(dp(38), dp(38)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        box.addView(card)

        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, 0) }
        box.addView(resultBox)
        showPlaceholder()
        return root
    }

    private fun analyze() {
        val text = input.text?.toString().orEmpty().trim()
        if (text.isBlank()) { toast("先粘贴抽卡记录或链接"); return }
        prefs.edit().putString("last_input", text).apply()
        progress.visibility = View.VISIBLE; status.text = "正在读取并解析…"
        if (text.startsWith("http", true)) {
            Thread {
                val result = runCatching { fetchText(normalizeUrl(text)) }
                runOnUiThread { progress.visibility = View.GONE; result.onSuccess { parseAndRender(it, "远程记录读取完成") }.onFailure { status.text = "读取失败：${it.message}" } }
            }.start()
        } else {
            progress.visibility = View.GONE; parseAndRender(text, "本地文本解析完成")
        }
    }

    private fun normalizeUrl(url: String): String = URLDecoder.decode(url.trim(), "UTF-8")
    private fun fetchText(url: String): String {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) error("HTTP ${resp.code}")
            return body
        }
    }

    private fun parseAndRender(raw: String, msg: String) {
        records = parseRecords(raw).distinctBy { it.id.ifBlank { "${it.name}_${it.time}_${it.pool}" } }.sortedByDescending { it.time }.toMutableList()
        prefs.edit().putString("last_raw", raw).apply()
        status.text = "$msg · ${records.size} 条记录"
        renderStats()
    }

    private fun parseRecords(raw: String): MutableList<GachaRecord> {
        val out = mutableListOf<GachaRecord>()
        runCatching {
            val trimmed = raw.trim()
            val arr = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("data") ?: obj.optJSONArray("list") ?: obj.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
                }
                else -> JSONArray()
            }
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(recordFromJson(it)) }
        }
        if (out.isEmpty()) {
            raw.lines().map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { index, line ->
                val rank = Regex("(5|4|3)\\s*星|rank(?:Type)?[=:： ](5|4|3)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.firstOrNull { it in listOf("5","4","3") }?.toIntOrNull() ?: when {
                    line.contains("五星") || line.contains("5星") -> 5
                    line.contains("四星") || line.contains("4星") -> 4
                    else -> 3
                }
                val name = line.split(',', '，', '\t', ' ').firstOrNull { it.length in 2..12 && !it.contains("星") } ?: "记录${index + 1}"
                out.add(GachaRecord("line_$index", name, rank, guessType(line), guessPool(line), line.take(19), line))
            }
        }
        return out
    }

    private fun recordFromJson(o: JSONObject): GachaRecord {
        val name = first(o, "name", "resourceName", "itemName", "title", "resName")
        val rank = first(o, "rankType", "rank", "quality", "rarity", "star").filter { it.isDigit() }.toIntOrNull() ?: 3
        val type = first(o, "resourceType", "itemType", "type", "category")
        val pool = first(o, "cardPoolType", "pool", "poolName", "banner", "gachaType")
        val time = first(o, "time", "createTime", "drawTime", "createdAt", "ts")
        val id = first(o, "id", "recordId", "logId")
        return GachaRecord(id, name.ifBlank { "未知物品" }, rank, guessType(type.ifBlank { name }), pool.ifBlank { guessPool(type) }, time, o.toString())
    }

    private fun first(o: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { o.optString(it).takeIf { v -> v.isNotBlank() && v != "null" } }.orEmpty()
    private fun guessType(text: String) = when {
        text.contains("武器") || text.contains("weapon", true) -> "武器"
        text.contains("角色") || text.contains("共鸣者") || text.contains("role", true) -> "角色"
        else -> "其他"
    }
    private fun guessPool(text: String) = when {
        text.contains("武器") -> "武器池"
        text.contains("新手") -> "新手池"
        text.contains("常驻") -> "常驻池"
        text.contains("角色") || text.contains("限定") -> "角色池"
        else -> "未知卡池"
    }

    private fun renderStats() {
        resultBox.removeAllViews()
        if (records.isEmpty()) { showPlaceholder("没有解析到记录，请检查脚本导出格式"); return }
        val total = records.size
        val five = records.count { it.rank >= 5 }
        val four = records.count { it.rank == 4 }
        val pity = records.takeWhile { it.rank < 5 }.size
        val fiveIntervals = buildFiveIntervals(records.sortedBy { it.time })
        val avgFive = if (fiveIntervals.isNotEmpty()) fiveIntervals.average() else 0.0
        resultBox.addView(summaryCard(total, five, four, pity, avgFive))
        resultBox.addView(poolCard())
        resultBox.addView(recentCard())
    }

    private fun buildFiveIntervals(asc: List<GachaRecord>): List<Int> {
        val out = mutableListOf<Int>(); var count = 0
        asc.forEach { count++; if (it.rank >= 5) { out.add(count); count = 0 } }
        return out
    }

    private fun summaryCard(total: Int, five: Int, four: Int, pity: Int, avgFive: Double) = card("核心统计").apply {
        addView(statGrid(listOf("总抽数" to total.toString(), "五星" to five.toString(), "四星" to four.toString(), "当前保底" to "$pity 抽", "五星率" to String.format(Locale.ROOT, "%.2f%%", five * 100.0 / total), "平均出金" to if (avgFive > 0) String.format(Locale.ROOT, "%.1f 抽", avgFive) else "暂无")))
        addView(TextView(context).apply { text = if (pity >= 70) "距离大保底很近，建议谨慎规划资源。" else "当前保底进度正常，继续记录可获得更准统计。"; setTextColor(Color.parseColor("#7C3AED")); textSize = 13f; setPadding(0, dp(10), 0, 0) })
    }

    private fun poolCard() = card("卡池分布").apply {
        val grouped = records.groupBy { it.pool.ifBlank { "未知卡池" } }.toList().sortedByDescending { it.second.size }
        grouped.forEach { (pool, list) -> addView(line("$pool：${list.size} 抽 · 五星 ${list.count { it.rank >= 5 }} · 四星 ${list.count { it.rank == 4 }}")) }
    }

    private fun recentCard() = card("最近记录").apply {
        records.take(20).forEachIndexed { i, r -> addView(line("${i + 1}. ${stars(r.rank)} ${r.name} · ${r.type} · ${r.pool} · ${r.time.ifBlank { "未知时间" }}")) }
        addView(pill("复制分析摘要", "#7C3AED") { copy(summaryText()) }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
    }

    private fun statGrid(items: List<Pair<String, String>>): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { (k, v) -> row.addView(statChip(k, v), LinearLayout.LayoutParams(0, dp(72), 1f).apply { rightMargin = dp(8) }) }
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        return box
    }

    private fun statChip(k: String, v: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = bg("#F5F3FF", 18); addView(TextView(context).apply { text = v; setTextColor(Color.parseColor("#6D28D9")); textSize = 18f; typeface = Typeface.DEFAULT_BOLD }); addView(TextView(context).apply { text = k; setTextColor(Color.parseColor("#64748B")); textSize = 12f }) }
    private fun card(title: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#FFFFFF", 24); setPadding(dp(16), dp(16), dp(16), dp(16)); addView(TextView(context).apply { text = title; setTextColor(Color.parseColor("#111827")); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(12)) }); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) } }
    private fun line(text: String) = TextView(this).apply { this.text = text; setTextColor(Color.parseColor("#334155")); textSize = 13f; setPadding(0, dp(4), 0, dp(4)) }
    private fun stars(rank: Int) = when { rank >= 5 -> "★★★★★"; rank == 4 -> "★★★★"; else -> "★★★" }
    private fun summaryText() = "鸣潮抽卡分析：共 ${records.size} 抽，五星 ${records.count { it.rank >= 5 }}，四星 ${records.count { it.rank == 4 }}，当前保底 ${records.takeWhile { it.rank < 5 }.size} 抽。"
    private fun showPlaceholder(text: String = "导入后将在这里展示统计图卡、卡池分布和最近记录") { resultBox.removeAllViews(); resultBox.addView(TextView(this).apply { this.text = text; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#64748B")); textSize = 14f; background = bg("#FFFFFF", 22); setPadding(dp(18), dp(28), dp(18), dp(28)) }) }
    private fun pasteClipboard() { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; input.setText(cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()); toast("已粘贴") }
    private fun clearAll() { input.setText(""); records.clear(); prefs.edit().clear().apply(); status.text = "已清空"; showPlaceholder() }
    private fun copy(text: String) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("鸣潮抽卡分析", text)); toast("已复制") }
    private fun pill(text: String, color: String, action: () -> Unit) = Button(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; background = bg(color, 16); setOnClickListener { action() } }
    private fun bg(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private data class GachaRecord(val id: String, val name: String, val rank: Int, val type: String, val pool: String, val time: String, val raw: String)
}