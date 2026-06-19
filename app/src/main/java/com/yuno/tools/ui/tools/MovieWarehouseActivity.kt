package com.yuno.tools.ui.tools

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MovieWarehouseActivity : Activity() {
    private val warehouseUrl = "https://9763.kstore.space/aowu.json"
    private val prefs by lazy { getSharedPreferences("movie_warehouse", Context.MODE_PRIVATE) }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var container: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var searchInput: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var progress: ProgressBar
    private val sites = mutableListOf<MovieSite>()
    private val favorites = linkedSetOf<String>()
    private var currentFilter = Filter.ALL
    private var rawConfig = ""
    private var summary = WarehouseSummary()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(prefs.getStringSet("favorites", emptySet()).orEmpty())
        setContentView(buildContent())
        loadConfig(forceNetwork = false)
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#101827"), Color.parseColor("#EEF2FF")))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(container)
        root.addView(scroll)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "影视仓"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(pill("返回", "#334155") { finish() }, LinearLayout.LayoutParams(dp(74), dp(42)))
        container.addView(top)

        container.addView(TextView(this).apply {
            text = "影视仓配置浏览器 · 只展示仓库元数据，不执行第三方 Spider 或破解解析"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(14))
        })

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#F8FAFC", 24)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        statusText = TextView(this).apply {
            text = "准备加载仓库配置"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        hero.addView(statusText)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        hero.addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)).apply { topMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL })
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        actionRow.addView(pill("刷新", "#2563EB") { loadConfig(forceNetwork = true) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        actionRow.addView(pill("复制配置", "#7C3AED") { copyText("影视仓配置", rawConfig.ifBlank { warehouseUrl }) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        actionRow.addView(pill("打开源", "#0F766E") { openUrl(warehouseUrl) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        hero.addView(actionRow)
        val playRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        playRow.addView(pill("粘贴播放", "#DC2626") { showDirectPlayDialog() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        playRow.addView(pill("播放说明", "#475569") { showPlayNotice() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        hero.addView(playRow)
        container.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        searchInput = EditText(this).apply {
            hint = "搜索站点名称、Key、接口、扩展链接"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = bg("#FFFFFF", 18)
            setPadding(dp(14), 0, dp(14), 0)
            setOnEditorActionListener { _, _, _ -> renderList(); false }
        }
        container.addView(searchInput, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })

        val filters = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Filter.ALL, Filter.SEARCHABLE, Filter.QUICK, Filter.FAVORITE, Filter.EXT_LINK).forEach { filter ->
            filterRow.addView(pill(filter.label, if (currentFilter == filter) "#111827" else "#64748B") {
                currentFilter = filter
                renderList()
            }, LinearLayout.LayoutParams(dp(92), dp(40)).apply { rightMargin = dp(8) })
        }
        filters.addView(filterRow)
        container.addView(filters, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(listBox)
        return root
    }

    private fun loadConfig(forceNetwork: Boolean) {
        progress.visibility = View.VISIBLE
        statusText.text = if (forceNetwork) "正在刷新远程仓库…" else "正在加载影视仓配置…"
        listBox.removeAllViews()
        Thread {
            val cached = prefs.getString("raw_config", "").orEmpty()
            val result = runCatching {
                if (!forceNetwork && cached.isNotBlank()) cached else fetchConfig()
            }.recoverCatching {
                if (cached.isNotBlank()) cached else throw it
            }
            runOnUiThread {
                progress.visibility = View.GONE
                result.onSuccess { parseAndRender(it, fromCache = it == cached && cached.isNotBlank() && !forceNetwork) }
                    .onFailure { showError(it.message ?: "加载失败") }
            }
        }.start()
    }

    private fun fetchConfig(): String {
        val req = Request.Builder().url(warehouseUrl).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) error("远程接口异常：HTTP ${resp.code}")
            prefs.edit().putString("raw_config", body).putLong("cached_at", System.currentTimeMillis()).apply()
            return body
        }
    }

    private fun parseAndRender(text: String, fromCache: Boolean) {
        rawConfig = text
        sites.clear()
        val root = JSONObject(text)
        val arr = root.optJSONArray("sites") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            sites.add(
                MovieSite(
                    name = obj.optString("name").ifBlank { "未命名站点" },
                    key = obj.optString("key"),
                    type = obj.optString("type"),
                    api = obj.optString("api"),
                    searchable = obj.optInt("searchable", 0) == 1,
                    quickSearch = obj.optInt("quickSearch", 0) == 1,
                    changeable = obj.optInt("changeable", 0) == 1,
                    ext = obj.opt("ext")?.toString().orEmpty()
                )
            )
        }
        summary = WarehouseSummary(
            siteCount = sites.size,
            liveCount = root.optJSONArray("lives")?.length() ?: 0,
            parseCount = root.optJSONArray("parses")?.length() ?: 0,
            dohCount = root.optJSONArray("doh")?.length() ?: 0,
            ruleCount = root.optJSONArray("rules")?.length() ?: 0,
            logo = root.optString("logo"),
            spider = root.optString("spider")
        )
        statusText.text = buildString {
            append(if (fromCache) "已从本地缓存加载" else "仓库加载完成")
            append(" · ${summary.siteCount} 个站点")
            append(" · ${summary.liveCount} 个直播源")
        }
        renderList()
    }

    private fun renderList() {
        listBox.removeAllViews()
        listBox.addView(summaryCard())
        val query = searchInput.text?.toString().orEmpty().trim().lowercase(Locale.ROOT)
        val filtered = sites.filter { site ->
            val matchQuery = query.isBlank() || site.name.lowercase(Locale.ROOT).contains(query) || site.key.lowercase(Locale.ROOT).contains(query) || site.api.lowercase(Locale.ROOT).contains(query) || site.ext.lowercase(Locale.ROOT).contains(query)
            val matchFilter = when (currentFilter) {
                Filter.ALL -> true
                Filter.SEARCHABLE -> site.searchable
                Filter.QUICK -> site.quickSearch
                Filter.FAVORITE -> favorites.contains(site.keyOrName)
                Filter.EXT_LINK -> site.ext.startsWith("http", true) || site.api.startsWith("http", true)
            }
            matchQuery && matchFilter
        }
        listBox.addView(TextView(this).apply {
            text = "站点列表 · ${filtered.size}/${sites.size}"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(10))
        })
        if (filtered.isEmpty()) {
            listBox.addView(emptyCard("没有匹配站点，换个关键词或筛选条件试试"))
            return
        }
        filtered.forEach { listBox.addView(siteCard(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }
    }

    private fun summaryCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#FFFFFF", 22)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(TextView(this).apply {
            text = "仓库概览"
            setTextColor(Color.parseColor("#111827"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        grid.addView(statRow("站点", summary.siteCount, "直播", summary.liveCount, "解析", summary.parseCount))
        grid.addView(statRow("DNS", summary.dohCount, "规则", summary.ruleCount, "收藏", favorites.size))
        card.addView(grid)
        if (summary.spider.isNotBlank()) card.addView(infoLine("Spider 资源", summary.spider.take(140)))
        if (summary.logo.isNotBlank()) card.addView(infoLine("Logo", summary.logo))
        return card.apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }.also {
            (it as LinearLayout).layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        }
    }

    private fun statRow(a: String, av: Int, b: String, bv: Int, c: String, cv: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(statChip(a, av), LinearLayout.LayoutParams(0, dp(70), 1f).apply { rightMargin = dp(8) })
        row.addView(statChip(b, bv), LinearLayout.LayoutParams(0, dp(70), 1f).apply { rightMargin = dp(8) })
        row.addView(statChip(c, cv), LinearLayout.LayoutParams(0, dp(70), 1f))
        return row.apply { setPadding(0, 0, 0, dp(8)) }
    }

    private fun statChip(label: String, value: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = bg("#EEF2FF", 16)
        addView(TextView(context).apply { text = value.toString(); setTextColor(Color.parseColor("#3730A3")); textSize = 20f; typeface = Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply { text = label; setTextColor(Color.parseColor("#64748B")); textSize = 12f })
    }

    private fun infoLine(label: String, value: String) = TextView(this).apply {
        text = "$label：$value"
        setTextColor(Color.parseColor("#475569"))
        textSize = 12f
        setPadding(0, dp(6), 0, 0)
        setOnClickListener { copyText(label, value) }
    }

    private fun siteCard(site: MovieSite): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = Color.parseColor("#E2E8F0")
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = site.name
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(this).apply {
            text = if (favorites.contains(site.keyOrName)) "★" else "☆"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 24f
            gravity = Gravity.CENTER
            setOnClickListener { toggleFavorite(site) }
        }, LinearLayout.LayoutParams(dp(44), dp(40)))
        box.addView(titleRow)
        box.addView(TextView(this).apply {
            text = buildString {
                append("Key: ${site.key.ifBlank { "-" }}")
                append("  · Type: ${site.type.ifBlank { "-" }}")
                if (site.searchable) append("  · 可搜索")
                if (site.quickSearch) append("  · 快搜")
                if (site.changeable) append("  · 可切换")
            }
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        })
        if (site.api.isNotBlank()) box.addView(smallLine("接口", site.api))
        if (site.ext.isNotBlank()) box.addView(smallLine("扩展", site.ext.take(180)))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        actions.addView(pill("详情", "#2563EB") { showDetail(site) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(8) })
        actions.addView(pill("复制", "#64748B") { copyText(site.name, site.toShareText()) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(8) })
        actions.addView(pill("打开", "#0F766E") { openBestLink(site) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(8) })
        actions.addView(pill("播放", "#DC2626") { playBestDirect(site) }, LinearLayout.LayoutParams(0, dp(40), 1f))
        box.addView(actions)
        card.addView(box)
        return card
    }

    private fun smallLine(label: String, value: String) = TextView(this).apply {
        text = "$label：$value"
        setTextColor(Color.parseColor("#334155"))
        textSize = 12f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun showDetail(site: MovieSite) {
        AlertDialog.Builder(this)
            .setTitle(site.name)
            .setMessage(site.toShareText())
            .setPositiveButton("复制") { _, _ -> copyText(site.name, site.toShareText()) }
            .setNegativeButton("打开链接") { _, _ -> openBestLink(site) }
            .setNeutralButton(if (favorites.contains(site.keyOrName)) "取消收藏" else "收藏") { _, _ -> toggleFavorite(site) }
            .show()
    }

    private fun showDirectPlayDialog() {
        val input = EditText(this).apply {
            hint = "粘贴已授权的 m3u8/mp4 播放直链"
            setSingleLine(false)
            minLines = 2
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
        }
        AlertDialog.Builder(this)
            .setTitle("直链播放")
            .setMessage("仅支持你有权访问的 m3u8/mp4 等直链，不执行第三方 Spider 或破解解析。")
            .setView(input)
            .setPositiveButton("播放") { _, _ -> playDirectUrl(input.text?.toString().orEmpty(), "影视仓直链") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPlayNotice() {
        AlertDialog.Builder(this)
            .setTitle("播放能力说明")
            .setMessage("当前为合规 TVBox 风格播放器：可以读取仓库配置、识别并播放 m3u8/mp4 等直链；不会执行第三方 Spider、破解解析器或绕过站点限制。若站点只提供 spider/ext 加密参数，需要在官方 TVBox 或授权服务中使用。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun playBestDirect(site: MovieSite) {
        val url = extractDirectMediaUrl(site.ext).ifBlank { extractDirectMediaUrl(site.api) }
        if (url.isBlank()) {
            toast("该站点没有可直接播放的 m3u8/mp4 直链")
            showPlayNotice()
            return
        }
        playDirectUrl(url, site.name)
    }

    private fun playDirectUrl(url: String, title: String) {
        val direct = extractDirectMediaUrl(url).ifBlank { url.trim() }
        if (!isDirectMediaUrl(direct)) {
            toast("只支持 m3u8/mp4 等直链播放")
            return
        }
        startActivity(Intent(this, MovieWarehousePlayerActivity::class.java).putExtra("url", direct).putExtra("title", title))
    }

    private fun extractDirectMediaUrl(text: String): String {
        return Regex("""https?://[^\s\"'<>]+(?:\.m3u8|\.mp4|\.m4v|\.webm|\.mkv)(?:\?[^\s\"'<>]*)?""", RegexOption.IGNORE_CASE)

            .find(text)
            ?.value
            .orEmpty()
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".m4v") || lower.contains(".webm") || lower.contains(".mkv"))
    }

    private fun openBestLink(site: MovieSite) {
        val candidates = listOf(site.ext, site.api).flatMap { text ->
            Regex("https?://[^\\s\\\"'<>]+", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()
        }
        val url = candidates.firstOrNull()
        if (url == null) toast("这个站点没有可直接打开的外部链接") else openUrl(url)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("无法打开链接") }
    }

    private fun toggleFavorite(site: MovieSite) {
        if (!favorites.add(site.keyOrName)) favorites.remove(site.keyOrName)
        prefs.edit().putStringSet("favorites", favorites).apply()
        renderList()
    }

    private fun showError(message: String) {
        statusText.text = "加载失败：$message"
        listBox.removeAllViews()
        listBox.addView(emptyCard("接口暂时不可用，可稍后刷新；若之前加载过，会自动优先使用本地缓存。"))
    }

    private fun emptyCard(message: String) = TextView(this).apply {
        text = message
        setTextColor(Color.parseColor("#475569"))
        textSize = 14f
        gravity = Gravity.CENTER
        background = bg("#FFFFFF", 20)
        setPadding(dp(18), dp(24), dp(18), dp(24))
    }

    private fun pill(text: String, color: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        background = bg(color, 16)
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
    }

    private fun copyText(label: String, text: String) {
        if (text.isBlank()) {
            toast("没有可复制内容")
            return
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        toast("已复制")
    }

    private fun bg(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private data class MovieSite(
        val name: String,
        val key: String,
        val type: String,
        val api: String,
        val searchable: Boolean,
        val quickSearch: Boolean,
        val changeable: Boolean,
        val ext: String
    ) {
        val keyOrName: String get() = key.ifBlank { name }
        fun toShareText() = buildString {
            appendLine("名称：$name")
            appendLine("Key：${key.ifBlank { "-" }}")
            appendLine("Type：${type.ifBlank { "-" }}")
            appendLine("可搜索：${if (searchable) "是" else "否"}")
            appendLine("快速搜索：${if (quickSearch) "是" else "否"}")
            appendLine("可切换：${if (changeable) "是" else "否"}")
            if (api.isNotBlank()) appendLine("接口：$api")
            if (ext.isNotBlank()) appendLine("扩展：$ext")
        }
    }

    private data class WarehouseSummary(
        val siteCount: Int = 0,
        val liveCount: Int = 0,
        val parseCount: Int = 0,
        val dohCount: Int = 0,
        val ruleCount: Int = 0,
        val logo: String = "",
        val spider: String = ""
    )

    private enum class Filter(val label: String) {
        ALL("全部"),
        SEARCHABLE("可搜索"),
        QUICK("快搜"),
        FAVORITE("收藏"),
        EXT_LINK("链接")
    }
}
