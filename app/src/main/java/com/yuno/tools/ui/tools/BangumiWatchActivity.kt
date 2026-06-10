package com.yuno.tools.ui.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.util.ThemeApplier
import org.json.JSONArray

class BangumiWatchActivity : AppCompatActivity() {
    private data class SourceUi(val name: String, val status: String, val pages: List<String>, val color: String, val enabled: Boolean = true)
    private data class CartoonUi(val title: String, val intro: String, val source: String, val status: String, val episode: String, val color: String)

    private enum class Page { HOME, SEARCH, SOURCES, MINE, DETAIL }

    private val prefs by lazy { getSharedPreferences("yuno_bangumi_watch", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var searchInput: EditText
    private var page = Page.HOME
    private var selectedSource = 0
    private var selectedCartoon: CartoonUi? = null
    private var query = ""

    private val sources = listOf(
        SourceUi("本地演示源", "已启用", listOf("推荐", "新番", "完结", "剧场"), "#4F6EF7"),
        SourceUi("合法授权源", "待添加", listOf("首页", "排行", "更新"), "#00A878", enabled = false),
        SourceUi("自管扩展", "未配置", listOf("搜索", "收藏"), "#FF8A34", enabled = false)
    )

    private val cartoons = listOf(
        CartoonUi("葬送的芙莉莲", "奇幻 / 冒险 / 治愈", "本地演示源", "已完结", "28 话", "#7B8CDE"),
        CartoonUi("迷宫饭", "奇幻 / 美食 / 冒险", "本地演示源", "更新至 24", "24 话", "#D8875B"),
        CartoonUi("孤独摇滚！", "音乐 / 日常 / 喜剧", "本地演示源", "已完结", "12 话", "#E06C9F"),
        CartoonUi("跃动青春", "校园 / 青春 / 治愈", "本地演示源", "已完结", "12 话", "#5ABFA3"),
        CartoonUi("夏日重现", "悬疑 / 轮回 / 剧情", "本地演示源", "已完结", "25 话", "#4F6473"),
        CartoonUi("赛博朋克：边缘行者", "科幻 / 动作 / 原创", "本地演示源", "已完结", "10 话", "#D64D4D"),
        CartoonUi("间谍过家家", "喜剧 / 家庭 / 动作", "本地演示源", "更新中", "37 话", "#64A96B"),
        CartoonUi("摇曳露营", "日常 / 旅行 / 治愈", "本地演示源", "更新中", "多季", "#58A6D6")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(88))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar())
        setContentView(root)
        render()
    }

    private fun render() {
        content.removeAllViews()
        if (root.childCount > 1) {
            root.removeViewAt(1)
            root.addView(bottomBar())
        }
        when (page) {
            Page.HOME -> renderHome()
            Page.SEARCH -> renderSearch()
            Page.SOURCES -> renderSources()
            Page.MINE -> renderMine()
            Page.DETAIL -> renderDetail()
        }
    }

    private fun renderHome() {
        topBar("看番", "源驱动番剧列表", false)
        searchBox()
        sourceStrip()
        pageChips(sources[selectedSource].pages)
        addCartoonGrid(cartoons)
    }

    private fun renderSearch() {
        topBar("搜索", "按扩展源返回番剧卡片", false)
        searchBox()
        val list = if (query.isBlank()) cartoons else cartoons.filter { it.title.contains(query, true) || it.intro.contains(query, true) }
        if (list.isEmpty()) {
            emptyCard("没有找到结果", "换一个关键词，或在源管理里添加你拥有授权的扩展源。")
        } else {
            section("搜索结果")
            addCartoonGrid(list)
        }
        section("最近搜索")
        val history = loadHistory()
        if (history.isEmpty()) emptyCard("暂无记录", "搜索过的关键词会显示在这里。") else history.forEach { keyword ->
            content.addView(rowCard(keyword, "点击再次筛选", "搜索") {
                query = keyword
                page = Page.SEARCH
                render()
            })
        }
    }

    private fun renderSources() {
        topBar("源", "扩展源与页面分组", false)
        sources.forEachIndexed { index, source ->
            content.addView(sourceCard(source, index == selectedSource) {
                selectedSource = index
                page = Page.HOME
                render()
            })
        }
        emptyCard("无账号绑定", "本页不提供第三方登录、账号同步或账号绑定入口。")
        emptyCard("合法源占位", "可保留源管理 UI，但不会内置或解析可能聚合未授权播放内容的影视仓库地址。")
    }

    private fun renderMine() {
        topBar("我的", "收藏与观看进度", false)
        section("收藏")
        val starred = loadStars().mapNotNull { key -> cartoons.find { it.title == key } }
        if (starred.isEmpty()) emptyCard("暂无收藏", "长按番剧卡片即可加入或移出收藏。") else addCartoonGrid(starred)
        section("继续观看")
        content.addView(progressCard("葬送的芙莉莲", "第 18 话", "18/28"))
        content.addView(progressCard("迷宫饭", "第 7 话", "7/24"))
    }

    private fun renderDetail() {
        val item = selectedCartoon ?: cartoons.first()
        topBar(item.title, item.source, true)
        content.addView(coverHeader(item))
        section("剧集")
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(0, dp(4), 0, dp(8))
        }
        for (i in 1..episodeCount(item.episode)) {
            grid.addView(TextView(this).apply {
                text = i.toString()
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#2B2F36"))
                setBackgroundColor(Color.parseColor("#EEF1F6"))
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { Toast.makeText(this@BangumiWatchActivity, "这里仅保留播放线路 UI，占位不解析片源", Toast.LENGTH_SHORT).show() }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            })
        }
        content.addView(grid)
        section("播放线路")
        content.addView(rowCard("线路 A", "等待合法源提供播放组件", "占位") {})
        content.addView(rowCard("线路 B", "等待合法源提供播放组件", "占位") {})
    }

    private fun topBar(title: String, sub: String, back: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        row.addView(TextView(this).apply {
            text = if (back) "‹" else "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#202124"))
            setOnClickListener { if (back) { page = Page.HOME; render() } else finish() }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = title; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")); maxLines = 1 })
            addView(TextView(context).apply { text = sub; textSize = 12f; setTextColor(Color.parseColor("#7A7F89")); maxLines = 1 })
        })
        row.addView(TextView(this).apply { text = "本地"; textSize = 12f; setTextColor(Color.parseColor("#4F6EF7")); setPadding(dp(10), dp(6), dp(10), dp(6)) })
        content.addView(row)
    }

    private fun searchBox() {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(Color.parseColor("#F0F2F6")) }
        searchInput = EditText(this).apply {
            hint = "搜索番剧"
            setText(query)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.parseColor("#202124"))
            setHintTextColor(Color.parseColor("#8B9099"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        wrap.addView(searchInput)
        wrap.addView(smallButton("搜索") { doSearch() })
        content.addView(wrap, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(8)) })
    }

    private fun sourceStrip() {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sources.forEachIndexed { index, source ->
            row.addView(TextView(this).apply {
                text = source.name
                textSize = 13f
                typeface = if (index == selectedSource) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (index == selectedSource) "#FFFFFF" else "#2B2F36"))
                setBackgroundColor(Color.parseColor(if (index == selectedSource) source.color else "#EEF1F6"))
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener { selectedSource = index; render() }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), dp(8), dp(8)) }
            })
        }
        scroll.addView(row)
        content.addView(scroll)
    }

    private fun pageChips(labels: List<String>) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        labels.forEachIndexed { index, label ->
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(if (index == 0) "#FFFFFF" else "#40454F"))
                setBackgroundColor(Color.parseColor(if (index == 0) "#607D8B" else "#F5F6F8"))
                setPadding(dp(12), dp(4), dp(12), dp(4))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(2), 0, dp(4), dp(8)) }
            })
        }
        scroll.addView(row)
        content.addView(scroll)
    }

    private fun addCartoonGrid(list: List<CartoonUi>) {
        val grid = GridLayout(this).apply { columnCount = 3 }
        list.forEach { item -> grid.addView(cartoonCard(item)) }
        content.addView(grid)
    }

    private fun cartoonCard(item: CartoonUi): View {
        val star = loadStars().contains(item.title)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(8)) }
        val cover = MaterialCardView(this).apply {
            radius = dp(4).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor(item.color))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(dp(6), dp(6), dp(6), dp(8))
                addView(TextView(context).apply { text = if (star) "已收藏" else item.status; textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(context).apply { text = item.episode; textSize = 12f; setTextColor(Color.WHITE) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(142))
        }
        box.addView(cover)
        box.addView(TextView(this).apply { text = item.title; textSize = 13f; setTextColor(Color.parseColor("#202124")); maxLines = 2; setPadding(0, dp(5), 0, 0) })
        box.addView(TextView(this).apply { text = item.intro; textSize = 11f; setTextColor(Color.parseColor("#7A7F89")); maxLines = 1 })
        box.setOnClickListener { selectedCartoon = item; page = Page.DETAIL; render() }
        box.setOnLongClickListener { toggleStar(item.title); render(); true }
        return box.apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, 0, dp(2))
            }
        }
    }

    private fun sourceCard(source: SourceUi, selected: Boolean, action: () -> Unit) = card().apply {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        row.addView(TextView(context).apply { text = source.name.take(1); textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setBackgroundColor(Color.parseColor(source.color)); layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)) })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = source.name; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
            addView(TextView(context).apply { text = "${source.status} · ${source.pages.joinToString(" / ")}"; textSize = 12f; setTextColor(Color.parseColor("#7A7F89")) })
        })
        row.addView(TextView(context).apply { text = if (selected) "当前" else "切换"; textSize = 12f; setTextColor(Color.parseColor(source.color)); setPadding(dp(8), dp(6), dp(8), dp(6)) })
        setOnClickListener { action() }
        addView(row)
    }

    private fun coverHeader(item: CartoonUi) = card().apply {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        row.addView(TextView(context).apply { text = item.title.take(2); textSize = 22f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor(item.color)); layoutParams = LinearLayout.LayoutParams(dp(96), dp(136)) })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = item.title; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
            addView(TextView(context).apply { text = item.intro; textSize = 13f; setTextColor(Color.parseColor("#606772")); setPadding(0, dp(6), 0, 0) })
            addView(TextView(context).apply { text = "${item.status} · ${item.episode} · ${item.source}"; textSize = 12f; setTextColor(Color.parseColor("#7A7F89")); setPadding(0, dp(8), 0, dp(12)) })
            addView(smallButton(if (loadStars().contains(item.title)) "取消收藏" else "收藏") { toggleStar(item.title); render() })
        })
        addView(row)
    }

    private fun bottomBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
        listOf(Page.HOME to "首页", Page.SEARCH to "搜索", Page.SOURCES to "源", Page.MINE to "我的").forEach { (target, label) ->
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = if (page == target) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (page == target) "#4F6EF7" else "#6D737D"))
                setOnClickListener { page = target; render() }
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    private fun rowCard(title: String, sub: String, actionText: String, action: () -> Unit) = card().apply {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
            addView(TextView(context).apply { text = sub; textSize = 12f; setTextColor(Color.parseColor("#7A7F89")) })
        })
        row.addView(smallButton(actionText, action))
        addView(row)
    }

    private fun progressCard(title: String, ep: String, progress: String) = rowCard(title, "$ep · $progress", "继续") {
        selectedCartoon = cartoons.find { it.title == title }
        page = Page.DETAIL
        render()
    }

    private fun emptyCard(title: String, msg: String) = content.addView(card().apply {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        box.addView(TextView(context).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
        box.addView(TextView(context).apply { text = msg; textSize = 12f; setTextColor(Color.parseColor("#7A7F89")); setPadding(0, dp(5), 0, 0) })
        addView(box)
    })

    private fun section(text: String) = content.addView(TextView(this).apply { this.text = text; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")); setPadding(dp(2), dp(12), 0, dp(6)) })

    private fun smallButton(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#4F6EF7"))
        setOnClickListener { action() }
    }

    private fun doSearch() {
        query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(this, "请输入番剧名", Toast.LENGTH_SHORT).show()
            return
        }
        saveHistory(query)
        page = Page.SEARCH
        render()
    }

    private fun toggleStar(title: String) {
        val stars = loadStars().toMutableSet()
        if (!stars.add(title)) stars.remove(title)
        prefs.edit().putString("stars", JSONArray(stars.toList()).toString()).apply()
        Toast.makeText(this, if (stars.contains(title)) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    private fun saveHistory(keyword: String) {
        val next = (listOf(keyword) + loadHistory().filterNot { it == keyword }).take(12)
        prefs.edit().putString("history", JSONArray(next).toString()).apply()
    }

    private fun loadHistory(): List<String> = jsonList("history")
    private fun loadStars(): Set<String> = jsonList("stars").toSet()

    private fun jsonList(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun episodeCount(text: String): Int = text.filter { it.isDigit() }.toIntOrNull()?.coerceIn(4, 28) ?: 12

    private fun card() = MaterialCardView(this).apply {
        radius = dp(4).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.WHITE)
        strokeColor = Color.parseColor("#ECEFF3")
        strokeWidth = dp(1)
        useCompatPadding = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(6)) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
