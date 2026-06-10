package com.yuno.tools.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
import java.net.URLEncoder

class BangumiWatchActivity : AppCompatActivity() {
    private data class Portal(val title: String, val sub: String, val url: String, val color: String)
    private data class BangumiItem(val title: String, val meta: String, val tag: String)

    private val prefs by lazy { getSharedPreferences("yuno_bangumi_watch", Context.MODE_PRIVATE) }
    private lateinit var searchInput: EditText
    private lateinit var content: LinearLayout
    private lateinit var historyBox: LinearLayout
    private var currentTab = "发现"

    private val portals = listOf(
        Portal("Bangumi", "番组资料 / 评分 / 条目", "https://bgm.tv/subject_search/%s?cat=2", "#7C4DFF"),
        Portal("哔哩哔哩", "官方番剧搜索", "https://search.bilibili.com/bangumi?keyword=%s", "#FB7299"),
        Portal("腾讯视频", "官方动漫搜索", "https://v.qq.com/x/search/?q=%s", "#11C85B"),
        Portal("爱奇艺", "官方动漫搜索", "https://so.iqiyi.com/so/q_%s", "#00BE06"),
        Portal("优酷", "官方动漫搜索", "https://so.youku.com/search_video/q_%s", "#1E88E5"),
        Portal("芒果TV", "官方内容搜索", "https://so.mgtv.com/so?k=%s", "#FFB300")
    )

    private val picks = listOf(
        BangumiItem("葬送的芙莉莲", "奇幻 · 冒险 · 高分番剧", "热门"),
        BangumiItem("孤独摇滚！", "音乐 · 日常 · 喜剧", "收藏"),
        BangumiItem("迷宫饭", "奇幻 · 美食 · 冒险", "推荐"),
        BangumiItem("跃动青春", "校园 · 青春 · 治愈", "口碑"),
        BangumiItem("夏日重现", "悬疑 · 轮回 · 剧情", "完结"),
        BangumiItem("赛博朋克：边缘行者", "科幻 · 动作 · 原创", "经典")
    )

    private val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6FB"))
        }

        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(30))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        renderPage()
    }

    private fun renderPage() {
        content.removeAllViews()
        addTopBar()
        addSearchCard()
        addTabs()
        when (currentTab) {
            "发现" -> addDiscover()
            "时间表" -> addSchedule()
            "源" -> addSources()
            "我的" -> addMine()
        }
    }

    private fun addTopBar() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#151923"))
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = "看番"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#151923")) })
            addView(TextView(context).apply { text = "番剧搜索 · 源管理 · 历史收藏"; textSize = 13f; setTextColor(Color.parseColor("#7B8494")) })
        })
        row.addView(TextView(this).apply {
            text = "无账号绑定"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5D6B85"))
            setBackgroundColor(Color.parseColor("#EAF0FF"))
            setPadding(dp(10), dp(7), dp(10), dp(7))
        })
        content.addView(row)
    }

    private fun addSearchCard() {
        content.addView(card(22).apply {
            val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            searchInput = EditText(context).apply {
                hint = "搜索番剧 / 动漫 / 关键词"
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setTextColor(Color.parseColor("#151923"))
                setHintTextColor(Color.parseColor("#9AA4B2"))
                setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchNow(); true } else false }
            }
            box.addView(searchInput)
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(10), 0, 0) }
            actions.addView(button("搜索", "#3D6BFF") { searchNow() })
            actions.addView(button("清空", "#8B95A8") { searchInput.setText(""); Toast.makeText(this@BangumiWatchActivity, "已清空", Toast.LENGTH_SHORT).show() })
            box.addView(actions)
            addView(box)
        })
    }

    private fun addTabs() {
        val wrap = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(10)) }
        listOf("发现", "时间表", "源", "我的").forEach { tab ->
            row.addView(TextView(this).apply {
                text = tab
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = if (tab == currentTab) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (tab == currentTab) "#FFFFFF" else "#536071"))
                setBackgroundColor(Color.parseColor(if (tab == currentTab) "#3D6BFF" else "#E9EEF8"))
                setPadding(dp(18), dp(10), dp(18), dp(10))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(8), 0) }
                setOnClickListener { currentTab = tab; renderPage() }
            })
        }
        wrap.addView(row)
        content.addView(wrap)
    }

    private fun addDiscover() {
        content.addView(section("今日推荐"))
        picks.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { item -> row.addView(animeCard(item), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            if (pair.size == 1) row.addView(LinearLayout(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(row)
        }
        content.addView(section("快速入口"))
        addPortalList(portals.take(3))
    }

    private fun addSchedule() {
        content.addView(section("本周放送"))
        week.forEachIndexed { index, day ->
            content.addView(card(18).apply {
                val box = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
                box.addView(TextView(context).apply { text = day; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#151923")); layoutParams = LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT) })
                box.addView(TextView(context).apply { text = picks[index % picks.size].title + "  ·  更新提醒"; textSize = 14f; setTextColor(Color.parseColor("#5D6B85")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                box.addView(button("搜索", "#3D6BFF") { openKeyword(picks[index % picks.size].title) })
                addView(box)
            })
        }
    }

    private fun addSources() {
        content.addView(section("源管理"))
        content.addView(tipCard("已移除第三方账号绑定", "这里不提供登录、账号同步或第三方账号绑定入口。影视源只保留官方/公开平台搜索入口；如需添加源，请确保你拥有合法授权。"))
        addPortalList(portals)
        content.addView(tipCard("关于影视仓库", "出于版权和平台规则原因，App 不会内置可能聚合未授权内容的影视仓库地址，也不会解析会员、登录、地区限制或 DRM 内容。"))
    }

    private fun addMine() {
        content.addView(section("我的"))
        content.addView(tipCard("收藏", "当前版本保留收藏区 UI，后续可接入本地收藏番剧和追番进度。"))
        content.addView(section("最近搜索"))
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(historyBox)
        renderHistory()
    }

    private fun addPortalList(list: List<Portal>) {
        list.forEach { p ->
            content.addView(card(18).apply {
                val box = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(14), dp(14)) }
                box.addView(TextView(context).apply { text = p.title.take(1); textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setBackgroundColor(Color.parseColor(p.color)); layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)) })
                box.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply { text = p.title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#151923")) })
                    addView(TextView(context).apply { text = p.sub; textSize = 12f; setTextColor(Color.parseColor("#7B8494")) })
                })
                box.addView(button("打开", p.color) { openPortal(p, searchInput.text?.toString()?.trim().orEmpty()) })
                addView(box)
            })
        }
    }

    private fun animeCard(item: BangumiItem) = card(20).apply {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }
        box.addView(TextView(context).apply { text = item.tag; textSize = 11f; setTextColor(Color.parseColor("#3D6BFF")); typeface = Typeface.DEFAULT_BOLD })
        box.addView(TextView(context).apply { text = item.title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#151923")); setPadding(0, dp(8), 0, dp(4)) })
        box.addView(TextView(context).apply { text = item.meta; textSize = 12f; setTextColor(Color.parseColor("#7B8494")) })
        box.addView(button("去搜索", "#3D6BFF") { openKeyword(item.title) })
        addView(box)
    }

    private fun tipCard(title: String, msg: String) = card(18).apply {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        box.addView(TextView(context).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#151923")) })
        box.addView(TextView(context).apply { text = msg; textSize = 13f; setTextColor(Color.parseColor("#6B7688")); setPadding(0, dp(6), 0, 0) })
        addView(box)
    }

    private fun searchNow() {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        if (keyword.isBlank()) { Toast.makeText(this, "请输入番剧名", Toast.LENGTH_SHORT).show(); return }
        saveHistory(keyword)
        openKeyword(keyword)
    }

    private fun openKeyword(keyword: String) {
        searchInput.setText(keyword)
        saveHistory(keyword)
        openPortal(portals.first(), keyword)
    }

    private fun openPortal(portal: Portal, keyword: String) {
        val encoded = URLEncoder.encode(keyword.ifBlank { "动漫" }, "UTF-8")
        val url = portal.url.format(encoded)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "没有可用浏览器打开链接", Toast.LENGTH_SHORT).show() }
    }

    private fun renderHistory() {
        historyBox.removeAllViews()
        val history = loadHistory()
        if (history.isEmpty()) {
            historyBox.addView(tipCard("暂无记录", "搜索番剧后会在这里显示最近搜索。"))
            return
        }
        history.forEach { kw -> historyBox.addView(tipCard(kw, "点击可再次搜索").apply { setOnClickListener { openKeyword(kw) } }) }
    }

    private fun saveHistory(keyword: String) {
        val next = (listOf(keyword) + loadHistory().filterNot { it == keyword }).take(12)
        prefs.edit().putString("history", JSONArray(next).toString()).apply()
    }

    private fun loadHistory(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun section(text: String) = TextView(this).apply { this.text = text; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#151923")); setPadding(dp(2), dp(16), 0, dp(8)) }

    private fun button(text: String, color: String, action: () -> Unit) = MaterialButton(this).apply { this.text = text; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor(color)); setOnClickListener { action() } }

    private fun card(radiusDp: Int) = MaterialCardView(this).apply {
        radius = dp(radiusDp).toFloat(); cardElevation = 0f; setCardBackgroundColor(Color.WHITE); strokeColor = Color.parseColor("#E8EDF7"); strokeWidth = dp(1); useCompatPadding = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(10)) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
