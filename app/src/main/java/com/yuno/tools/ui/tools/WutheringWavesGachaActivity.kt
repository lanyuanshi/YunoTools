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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class WutheringWavesGachaActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ww_gacha_analysis", Context.MODE_PRIVATE) }
    private val client by lazy { OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build() }
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resultBox: LinearLayout
    private lateinit var tabCard: Button
    private lateinit var tabList: Button
    private var records = mutableListOf<GachaRecord>()
    private var cardMode = true
    private var uid = "未识别"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        prefs.getString("last_input", "")?.let { input.setText(it) }
        prefs.getString("last_raw", "")?.takeIf { it.isNotBlank() }?.let { parseAndRender(it, "已加载上次分析缓存") }
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#081225"), Color.parseColor("#1E1B4B"), Color.parseColor("#EEF2FF")))
        }
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)) }
        scroll.addView(box); root.addView(scroll)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "鸣潮唤取报告"; setTextColor(Color.WHITE); textSize = 27f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(pill("返回", "#334155") { finish() }, LinearLayout.LayoutParams(dp(74), dp(42)))
        box.addView(top)
        box.addView(TextView(this).apply { text = "在线导入 · 欧气评级 · 保底进度 · 五星时间线 · 历史快照"; setTextColor(Color.parseColor("#DDD6FE")); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(14)) })

        val inputCard = glassCard().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        input = EditText(this).apply {
            hint = "粘贴鸣潮唤取记录链接 / 官方接口 JSON / 抽卡文本\n支持公开记录接口字段：playerId、serverId、recordId、cardPoolType"
            minLines = 4; gravity = Gravity.TOP; imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#A5B4FC")); textSize = 14f; background = bg("#26304F", 16); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        inputCard.addView(input, LinearLayout.LayoutParams(-1, dp(122)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        row.addView(pill("开始分析", "#8B5CF6") { analyze() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row.addView(pill("粘贴", "#2563EB") { pasteClipboard() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row.addView(pill("清空", "#64748B") { clearAll() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        inputCard.addView(row)
        status = TextView(this).apply { text = "等待导入抽卡记录"; setTextColor(Color.parseColor("#CBD5E1")); textSize = 13f; setPadding(0, dp(12), 0, 0) }
        inputCard.addView(status)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        inputCard.addView(progress, LinearLayout.LayoutParams(dp(38), dp(38)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        box.addView(inputCard)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, dp(10)) }
        tabCard = pill("卡片总结", "#8B5CF6") { cardMode = true; renderStats() }
        tabList = pill("列表总结", "#1F2937") { cardMode = false; renderStats() }
        tabs.addView(tabCard, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        tabs.addView(tabList, LinearLayout.LayoutParams(0, dp(46), 1f))
        box.addView(tabs)

        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(resultBox)
        showPlaceholder()
        return root
    }

    private fun analyze() {
        val text = input.text?.toString().orEmpty().trim()
        if (text.isBlank()) { toast("先粘贴鸣潮抽卡链接或记录"); return }
        prefs.edit().putString("last_input", text).apply()
        progress.visibility = View.VISIBLE
        val officialUrl = extractOfficialRecordUrl(text)
        when {
            officialUrl.isNotBlank() -> {
                status.text = "已识别鸣潮唤取记录链接，正在通过公开记录接口同步…"
                Thread {
                    val result = runCatching { fetchOfficialGachaRecords(officialUrl) }
                    runOnUiThread {
                        progress.visibility = View.GONE
                        result.onSuccess { parseAndRender(it, "在线记录同步完成") }
                            .onFailure { status.text = "在线记录同步失败：${it.message}" }
                    }
                }.start()
            }
            text.startsWith("http", true) -> {
                status.text = "正在读取远程文本…"
                Thread {
                    val result = runCatching { fetchText(normalizeUrl(text)) }
                    runOnUiThread { progress.visibility = View.GONE; result.onSuccess { parseAndRender(it, "远程记录读取完成") }.onFailure { status.text = "读取失败：${it.message}" } }
                }.start()
            }
            else -> {
                progress.visibility = View.GONE
                parseAndRender(text, "本地文本解析完成")
            }
        }
    }

    private fun normalizeUrl(url: String): String = URLDecoder.decode(url.trim(), "UTF-8")

    private fun extractOfficialRecordUrl(text: String): String {
        val pattern = Regex("""https://aki-gm-resources(?:-oversea)?\.aki-game\.(?:net|com)/aki/gacha/index\.html#/record[^\s\"'<>]*""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.value?.trim()?.trimEnd('\r', '\n') ?: ""
    }

    private fun fetchText(url: String): String {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) error("HTTP ${resp.code}")
            return body
        }
    }

    private fun fetchOfficialGachaRecords(recordUrl: String): String {
        val params = parseRecordParams(recordUrl)
        uid = params["player_id"].orEmpty().ifBlank { params["playerId"].orEmpty() }.ifBlank { "未识别" }
        val apis = listOf("https://gmserver-api.aki-game2.net/gacha/record/query", "https://gmserver-api.aki-game2.com/gacha/record/query")
        val all = JSONArray()
        val poolNames = mapOf(1 to "角色活动唤取", 2 to "武器活动唤取", 3 to "角色常驻唤取", 4 to "武器常驻唤取", 5 to "新手唤取", 6 to "新手自选唤取", 7 to "角色新旅唤取", 8 to "武器新旅唤取")
        val media = "application/json; charset=utf-8".toMediaType()
        var okPool = 0
        for (poolType in 1..8) {
            val payload = JSONObject()
                .put("serverId", params["svr_id"] ?: params["serverId"] ?: "")
                .put("playerId", params["player_id"] ?: params["playerId"] ?: "")
                .put("languageCode", params["lang"] ?: params["languageCode"] ?: "zh-Hans")
                .put("recordId", params["record_id"] ?: params["recordId"] ?: "")
                .put("cardPoolId", params["resources_id"] ?: params["cardPoolId"] ?: "")
                .put("cardPoolType", poolType)
            var fetched = false
            for (api in apis) {
                if (fetched) break
                val req = Request.Builder()
                    .url(api)
                    .post(payload.toString().toRequestBody(media))
                    .header("Content-Type", "application/json")
                    .header("Origin", if (recordUrl.contains("oversea")) "https://aki-gm-resources-oversea.aki-game.net" else "https://aki-gm-resources.aki-game.com")
                    .header("Referer", recordUrl)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || body.isBlank()) return@use
                    val json = JSONObject(body)
                    if (json.optInt("code", 0) != 0) return@use
                    val data = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
                    if (data.length() > 0) okPool++
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        item.put("poolName", poolNames[poolType] ?: "卡池$poolType")
                        item.put("poolTypeId", poolType)
                        all.put(item)
                    }
                    fetched = true
                }
            }
        }
        if (all.length() == 0) error("未同步到抽卡记录：链接可能过期，或近六个月没有记录；请在游戏内打开一次唤取记录后重新复制链接")
        return JSONObject().put("uid", uid).put("source", "official_api").put("poolCount", okPool).put("data", all).toString()
    }

    private fun parseRecordParams(recordUrl: String): Map<String, String> {
        val query = recordUrl.substringAfter("?/", recordUrl).substringAfter("?", "")
        if (query.isBlank()) error("抽卡链接缺少参数")
        return query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else URLDecoder.decode(part.substring(0, idx), "UTF-8") to URLDecoder.decode(part.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    private fun parseAndRender(raw: String, msg: String) {
        uid = extractUid(raw)
        records = parseRecords(raw).distinctBy { it.id.ifBlank { "${it.name}_${it.time}_${it.pool}_${it.index}" } }.sortedByDescending { it.sortKey }.toMutableList()
        prefs.edit().putString("last_raw", raw).apply()
        status.text = "$msg · ${records.size} 条记录 · UID $uid"
        renderStats()
    }

    private fun extractUid(raw: String): String {
        val regex = Regex("(?:uid|userId|role_id|playerId)[\"'=:\\s]+(\\d{5,12})", RegexOption.IGNORE_CASE)
        return regex.find(raw)?.groupValues?.getOrNull(1) ?: Regex("\\b\\d{8,10}\\b").find(raw)?.value ?: "未识别"
    }

    private fun parseRecords(raw: String): MutableList<GachaRecord> {
        val out = mutableListOf<GachaRecord>()
        runCatching {
            collectJsonArrays(raw).forEach { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(recordFromJson(it, out.size + 1)) }
            }
        }
        if (out.isEmpty()) parsePlainLines(raw, out)
        return out
    }

    private fun collectJsonArrays(raw: String): List<JSONArray> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) return listOf(JSONArray(trimmed))
        if (!trimmed.startsWith("{")) return emptyList()
        val root = JSONObject(trimmed)
        val arrays = mutableListOf<JSONArray>()
        fun scan(obj: JSONObject) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)
                when (v) {
                    is JSONArray -> if (v.length() > 0 && v.optJSONObject(0) != null) arrays.add(v)
                    is JSONObject -> scan(v)
                }
            }
        }
        root.optJSONArray("data")?.let { arrays.add(it) }
        root.optJSONArray("list")?.let { arrays.add(it) }
        root.optJSONObject("data")?.optJSONArray("list")?.let { arrays.add(it) }
        scan(root)
        return arrays.distinctBy { it.toString().take(200) }
    }

    private fun parsePlainLines(raw: String, out: MutableList<GachaRecord>) {
        raw.lines().map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { index, line ->
            val rank = Regex("(5|4|3)\\s*星|五星|四星|三星|rank(?:Type)?[=:： ](5|4|3)", RegexOption.IGNORE_CASE).find(line)?.let {
                when {
                    it.value.contains("五") -> 5
                    it.value.contains("四") -> 4
                    it.value.contains("三") -> 3
                    else -> it.groupValues.firstOrNull { g -> g in listOf("5", "4", "3") }?.toIntOrNull() ?: 3
                }
            } ?: 3
            val name = line.split(',', '，', '\t', ' ').firstOrNull { it.length in 2..12 && !it.contains("星") && !it.contains("池") } ?: "记录${index + 1}"
            out.add(GachaRecord("line_$index", name, rank, guessType(line), guessPool(line), line.take(19), line.take(19), index, line))
        }
    }

    private fun recordFromJson(o: JSONObject, index: Int): GachaRecord {
        val name = first(o, "name", "resourceName", "itemName", "title", "resName", "item_name")
        val rank = first(o, "rankType", "rank", "quality", "rarity", "star", "qualityLevel").filter { it.isDigit() }.toIntOrNull() ?: 3
        val type = first(o, "resourceType", "itemType", "type", "category", "item_type")
        val pool = first(o, "cardPoolType", "pool", "poolName", "banner", "gachaType", "gacha_name", "pool_name")
        val time = first(o, "time", "createTime", "drawTime", "createdAt", "ts", "record_time")
        val id = first(o, "id", "recordId", "logId", "record_id")
        val order = first(o, "index", "order", "count", "drawIndex").toIntOrNull() ?: index
        val poolName = normalizePool(pool.ifBlank { guessPool(type + name) })
        return GachaRecord(id, name.ifBlank { "未知物品" }, rank, guessType(type.ifBlank { name }), poolName, time, time.ifBlank { "%08d".format(order) }, order, o.toString())
    }

    private fun first(o: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { o.optString(it).takeIf { v -> v.isNotBlank() && v != "null" } }.orEmpty()
    private fun guessType(text: String) = when {
        text.contains("武器") || text.contains("weapon", true) -> "武器"
        text.contains("角色") || text.contains("共鸣者") || text.contains("role", true) -> "角色"
        else -> "角色"
    }
    private fun guessPool(text: String) = when {
        text.contains("新旅") && text.contains("武器") -> "武器新旅"
            text.contains("新旅") -> "角色新旅"
            text.contains("新手自选") || text.contains("自选") -> "新手自选"
        text.contains("新手") -> "新手池"
        text.contains("武器") || text.contains("weapon", true) -> "武器池"
        text.contains("常驻") -> "常驻角色池"
        else -> "角色池"
    }
    private fun normalizePool(text: String) = when (text.trim()) {
        "1" -> "角色池"
        "2" -> "武器池"
        "3" -> "常驻角色池"
        "4" -> "常驻武器池"
        "5" -> "新手池"
        "6" -> "新手自选"
        "7" -> "角色新旅"
        "8" -> "武器新旅"
        else -> when {
            text.contains("新旅") && text.contains("武器") -> "武器新旅"
            text.contains("新旅") -> "角色新旅"
            text.contains("新手自选") || text.contains("自选") -> "新手自选"
            text.contains("新手") -> "新手池"
            text.contains("常驻") && text.contains("武器") -> "常驻武器池"
            text.contains("武器") || text.contains("weapon", true) -> "武器池"
            text.contains("常驻") && text.contains("角色") -> "常驻角色池"
            text.contains("常驻") -> "常驻角色池"
            text.contains("角色") || text.contains("限定") || text.contains("event", true) -> "角色池"
            else -> text.ifBlank { "未知卡池" }
        }
    }

    private fun renderStats() {
        resultBox.removeAllViews()
        tabCard.background = bg(if (cardMode) "#8B5CF6" else "#1F2937", 16)
        tabList.background = bg(if (!cardMode) "#8B5CF6" else "#1F2937", 16)
        if (records.isEmpty()) { showPlaceholder("没有解析到记录，请粘贴鸣潮唤取记录链接、官方接口 JSON 或抽卡文本"); return }
        if (cardMode) renderCardSummary() else renderListSummary()
    }

    private fun renderCardSummary() {
        val five = records.count { it.rank >= 5 }
        val total = records.size
        val avg = avgFive(records)
        resultBox.addView(topSummary(total, five, avg))
        resultBox.addView(reportActions())
        resultBox.addView(insightPanel())
        poolOrder().forEach { pool ->
            val list = records.filter { it.pool == pool }
            resultBox.addView(poolPanel(pool, list))
        }
        resultBox.addView(timelinePanel())
    }

    private fun renderListSummary() {
        resultBox.addView(topSummary(records.size, records.count { it.rank >= 5 }, avgFive(records)))
        val card = panel("列表总结")
        records.forEachIndexed { i, r ->
            card.addView(line("${i + 1}. ${stars(r.rank)} ${r.name} · ${r.pool} · ${r.type} · ${r.time.ifBlank { "未知时间" }}"))
        }
        card.addView(pill("复制分析摘要", "#8B5CF6") { copy(summaryText()) }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        resultBox.addView(card)
    }

    private fun topSummary(total: Int, five: Int, avg: Double): View {
        val card = panel(overallRating(avg, five))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(labelValue("UID", uid), LinearLayout.LayoutParams(0, dp(72), 1f).apply { rightMargin = dp(10) })
        row1.addView(labelValue("总抽数", "${total}抽", "#8B5CF6"), LinearLayout.LayoutParams(0, dp(72), 1f))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row2.addView(labelValue("总金数", "${five}金", "#FACC15"), LinearLayout.LayoutParams(0, dp(72), 1f).apply { rightMargin = dp(10) })
        row2.addView(labelValue("平均出金", if (avg > 0) String.format(Locale.ROOT, "%.1f抽", avg) else "暂无", "#FFFFFF"), LinearLayout.LayoutParams(0, dp(72), 1f))
        card.addView(row1); card.addView(row2)
        card.addView(line("综合评价：${ratingComment(avg, five)}"))
        return card
    }

    private fun poolPanel(pool: String, list: List<GachaRecord>): View {
        val fiveList = list.filter { it.rank >= 5 }.sortedBy { it.sortKey }
        val total = list.size
        val avg = avgFive(list)
        val miss = missRate(pool, fiveList)
        val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title.addView(TextView(this).apply { text = pool; setTextColor(Color.WHITE); textSize = 21f; typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(smallMetric(total.toString(), "总抽数"))
        title.addView(smallMetric(if (avg > 0) String.format(Locale.ROOT, "%.1f抽", avg) else "0抽", "平均出金"))
        title.addView(smallMetric(if (pool == "角色池") String.format(Locale.ROOT, "%.1f%%", miss) else fiveList.size.toString(), if (pool == "角色池") "不歪概率" else "出金数"))
        val card = glassCard().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) } }
        card.addView(title)
        card.addView(View(this).apply { setBackgroundColor(Color.parseColor("#33FFFFFF")) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        card.addView(pityProgress(pool, list))
        card.addView(tileGrid(buildTiles(pool, fiveList)))
        if (list.isNotEmpty()) {
            card.addView(line("近期记录：" + list.take(6).joinToString(" / ") { "${it.name}${if (it.rank >= 5) "★" else ""}" }))
        }
        return card
    }

    private fun buildTiles(pool: String, fiveList: List<GachaRecord>): List<Pair<String, Int>> {
        val intervals = fiveIntervalsForPool(pool)
        val tiles = mutableListOf<Pair<String, Int>>()
        if (fiveList.isEmpty()) tiles.add("?" to 0)
        fiveList.forEachIndexed { i, r -> tiles.add(r.name to (intervals.getOrNull(i) ?: 0)) }
        return tiles
    }

    private fun tileGrid(items: List<Pair<String, Int>>): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(5).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            rowItems.forEach { row.addView(itemTile(it.first, it.second), LinearLayout.LayoutParams(0, dp(92), 1f).apply { rightMargin = dp(8); bottomMargin = dp(8) }) }
            repeat(max(0, 5 - rowItems.size)) { row.addView(FrameLayout(this), LinearLayout.LayoutParams(0, dp(92), 1f).apply { rightMargin = dp(8) }) }
            box.addView(row)
        }
        return box
    }

    private fun itemTile(name: String, pity: Int): View {
        val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = strokeBg("#303646", "#FFFFFF", 14) }
        tile.addView(TextView(this).apply { text = if (name == "?") "?" else name.take(2); setTextColor(Color.WHITE); textSize = if (name == "?") 40f else 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; background = bg(if (name == "?") "#3F3F46" else "#FACC15", 10) }, LinearLayout.LayoutParams(-1, 0, 1f))
        tile.addView(TextView(this).apply { text = pity.toString(); setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER; setBackgroundColor(Color.parseColor("#AA3F3F46")) }, LinearLayout.LayoutParams(-1, dp(28)))
        return tile
    }

    private fun poolOrder(): List<String> = listOf("角色池", "武器池", "常驻角色池", "常驻武器池", "新手池", "新手自选", "角色新旅", "武器新旅")
    private fun avgFive(list: List<GachaRecord>): Double { val ints = fiveIntervals(list.sortedBy { it.sortKey }); return if (ints.isEmpty()) 0.0 else ints.average() }
    private fun fiveIntervals(list: List<GachaRecord>): List<Int> { val out = mutableListOf<Int>(); var count = 0; list.forEach { count++; if (it.rank >= 5) { out.add(count); count = 0 } }; return out }
    private fun fiveIntervalsForPool(pool: String) = fiveIntervals(records.filter { it.pool == pool }.sortedBy { it.sortKey })
    private fun missRate(pool: String, fiveList: List<GachaRecord>): Double { if (pool != "角色池" || fiveList.isEmpty()) return 0.0; val misses = fiveList.count { isLikelyStandard(it.name) }; return ((fiveList.size - misses) * 100.0 / fiveList.size) }
    private fun isLikelyStandard(name: String) = listOf("维里奈", "鉴心", "卡卡罗", "安可", "凌阳").any { name.contains(it) }

    private fun reportActions(): View {
        val card = glassCard().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(pill("复制报告", "#8B5CF6") { copy(shareReport()) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row.addView(pill("保存快照", "#2563EB") { saveSnapshot() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row.addView(pill("历史快照", "#334155") { showSnapshots() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        card.addView(row)
        return card
    }

    private fun insightPanel(): View {
        val card = panel("抽卡概览")
        val pools = poolOrder().map { it to records.filter { r -> r.pool == it } }.filter { it.second.isNotEmpty() }
        val best = pools.maxByOrNull { it.second.count { r -> r.rank >= 5 } }
        card.addView(line("当前保底：全记录 ${records.takeWhile { it.rank < 5 }.size} 抽；分池请看下方进度条"))
        card.addView(line("五星率：${String.format(Locale.ROOT, "%.2f", records.count { it.rank >= 5 } * 100.0 / max(1, records.size))}% · 四星率：${String.format(Locale.ROOT, "%.2f", records.count { it.rank == 4 } * 100.0 / max(1, records.size))}%"))
        card.addView(line("出金最多：${best?.first ?: "暂无"} ${best?.second?.count { it.rank >= 5 } ?: 0} 金"))
        return card
    }

    private fun pityProgress(pool: String, list: List<GachaRecord>): View {
        val pity = currentPity(list)
        val limit = pityLimit(pool)
        val percent = (pity * 100 / max(1, limit)).coerceIn(0, 100)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10)) }
        box.addView(line("保底进度：$pity / $limit 抽 · $percent%${if (pity >= limit - 10) " · 接近保底" else ""}"))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = bg("#1F2937", 10) }
        bar.addView(View(this).apply { background = bg(if (percent >= 85) "#FACC15" else "#8B5CF6", 10) }, LinearLayout.LayoutParams(0, dp(12), percent.toFloat()))
        bar.addView(View(this), LinearLayout.LayoutParams(0, dp(12), (100 - percent).toFloat()))
        box.addView(bar)
        return box
    }

    private fun timelinePanel(): View {
        val card = panel("五星时间线")
        val fives = records.filter { it.rank >= 5 }.sortedByDescending { it.sortKey }.take(12)
        if (fives.isEmpty()) card.addView(line("暂无五星记录，导入更多数据后会展示时间线"))
        fives.forEachIndexed { i, r -> card.addView(line("${i + 1}. ${r.name} · ${r.pool} · ${r.time.ifBlank { "未知时间" }} · 间隔 ${fiveIntervalsForPool(r.pool).getOrNull(fives.size - i - 1) ?: 0} 抽")) }
        return card
    }

    private fun overallRating(avg: Double, five: Int): String = when {
        five == 0 -> "未出金"
        avg <= 45 -> "欧皇"
        avg <= 62 -> "小欧"
        avg <= 75 -> "正常"
        else -> "偏非"
    }

    private fun ratingComment(avg: Double, five: Int): String = when {
        five == 0 -> "还没有五星记录，先看分池保底进度。"
        avg <= 45 -> "平均出金很靠前，属于明显偏欧。"
        avg <= 62 -> "整体运气不错，出金节奏优于常规期望。"
        avg <= 75 -> "整体接近期望值，重点关注下一金保底。"
        else -> "平均出金偏后，建议结合保底进度规划资源。"
    }

    private fun currentPity(list: List<GachaRecord>): Int = list.sortedByDescending { it.sortKey }.takeWhile { it.rank < 5 }.size
    private fun pityLimit(pool: String): Int = when { pool.contains("新手") -> 50; else -> 80 }

    private fun saveSnapshot() {
        if (records.isEmpty()) { toast("没有可保存的分析结果"); return }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val item = "$time||${summaryText()}"
        val old = prefs.getString("snapshots", "").orEmpty().lines().filter { it.isNotBlank() }.take(19)
        prefs.edit().putString("snapshots", listOf(item).plus(old).joinToString("\n")).apply()
        toast("已保存历史快照")
    }

    private fun showSnapshots() {
        val rows = prefs.getString("snapshots", "").orEmpty().lines().filter { it.isNotBlank() }
        resultBox.addView(panel("历史快照").apply {
            if (rows.isEmpty()) addView(line("暂无历史快照"))
            rows.take(10).forEach { addView(line(it.replace("||", "\n"))) }
        }, 0)
        toast("已展开历史快照")
    }

    private fun shareReport(): String = buildString {
        appendLine("鸣潮唤取分析报告")
        appendLine("UID：$uid")
        appendLine("评级：${overallRating(avgFive(records), records.count { it.rank >= 5 })}")
        appendLine(summaryText())
        poolOrder().forEach { pool ->
            val list = records.filter { it.pool == pool }
            if (list.isNotEmpty()) appendLine("$pool：${list.size}抽，${list.count { it.rank >= 5 }}金，当前保底${currentPity(list)}抽，平均出金${if (avgFive(list) > 0) String.format(Locale.ROOT, "%.1f", avgFive(list)) else "暂无"}")
        }
    }

    private fun panel(title: String) = glassCard().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); addView(TextView(context).apply { text = title; setTextColor(Color.parseColor("#FACC15")); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(12)) }); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) } }
    private fun glassCard() = LinearLayout(this).apply { background = GradientDrawable().apply { setColor(Color.parseColor("#CC111827")); cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#33FFFFFF")) } }
    private fun labelValue(label: String, value: String, color: String = "#FFFFFF") = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = bg("#26304F", 12); setPadding(dp(12), 0, dp(12), 0); addView(TextView(context).apply { text = label; setTextColor(Color.parseColor("#CBD5E1")); textSize = 15f }, LinearLayout.LayoutParams(0, -2, 1f)); addView(TextView(context).apply { text = value; setTextColor(Color.parseColor(color)); textSize = 20f; typeface = Typeface.DEFAULT_BOLD }) }
    private fun smallMetric(v: String, k: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(TextView(context).apply { text = v; setTextColor(Color.parseColor("#FACC15")); textSize = 18f; typeface = Typeface.DEFAULT_BOLD }); addView(TextView(context).apply { text = k; setTextColor(Color.parseColor("#CBD5E1")); textSize = 12f }) }
    private fun line(text: String) = TextView(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 13f; setPadding(0, dp(4), 0, dp(4)) }
    private fun stars(rank: Int) = when { rank >= 5 -> "★★★★★"; rank == 4 -> "★★★★"; else -> "★★★" }
    private fun summaryText() = "鸣潮抽卡分析：UID $uid，共 ${records.size} 抽，五星 ${records.count { it.rank >= 5 }}，四星 ${records.count { it.rank == 4 }}，平均出金 ${if (avgFive(records) > 0) String.format(Locale.ROOT, "%.1f", avgFive(records)) else "暂无"} 抽。"
    private fun showPlaceholder(text: String = "导入后生成鸣潮唤取报告：在线同步、欧气评级、分池保底、五星时间线、分享报告、历史快照") { resultBox.removeAllViews(); resultBox.addView(TextView(this).apply { this.text = text; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 14f; background = bg("#AA111827", 22); setPadding(dp(18), dp(28), dp(18), dp(28)) }) }
    private fun pasteClipboard() { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; input.setText(cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()); toast("已粘贴") }
    private fun clearAll() { input.setText(""); records.clear(); prefs.edit().clear().apply(); uid = "未识别"; status.text = "已清空"; showPlaceholder() }
    private fun copy(text: String) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("鸣潮抽卡分析", text)); toast("已复制") }
    private fun pill(text: String, color: String, action: () -> Unit) = Button(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; background = bg(color, 16); setOnClickListener { action() } }
    private fun bg(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun strokeBg(color: String, stroke: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat(); setStroke(dp(2), Color.parseColor(stroke)) }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private data class GachaRecord(val id: String, val name: String, val rank: Int, val type: String, val pool: String, val time: String, val sortKey: String, val index: Int, val raw: String)
}
