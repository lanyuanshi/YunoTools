package com.yuno.tools.ui.tools

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class GachaAnalysisActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#111827"), Color.parseColor("#EEF2FF")))
        }
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(box)
        root.addView(scroll)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "抽卡分析"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(pill("返回", "#334155") { finish() }, LinearLayout.LayoutParams(dp(74), dp(42)))
        box.addView(top)
        box.addView(TextView(this).apply {
            text = "导入脚本导出的抽卡记录，自动生成保底、出金、卡池和最近记录统计。"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(18))
        })

        box.addView(gameCard(
            title = "鸣潮抽卡分析",
            subtitle = "支持 URL / JSON / 文本记录导入，统计五星、四星、当前保底、平均出金和卡池分布。",
            icon = "鸣",
            color = "#7C3AED"
        ) { startActivity(Intent(this, WutheringWavesGachaActivity::class.java)) })

        box.addView(infoCard("后续可扩展", "当前已完成鸣潮抽卡分析入口和完整解析页；后续可以继续在此页添加原神、星铁、绝区零等游戏。"))
        return root
    }

    private fun gameCard(title: String, subtitle: String, icon: String, color: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg("#FFFFFF", 24)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = icon
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            background = bg(color, 18)
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(14) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; setTextColor(Color.parseColor("#0F172A")); textSize = 18f; typeface = Typeface.DEFAULT_BOLD })
        texts.addView(TextView(this).apply { text = subtitle; setTextColor(Color.parseColor("#64748B")); textSize = 13f; setPadding(0, dp(5), 0, 0) })
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply { text = "›"; setTextColor(Color.parseColor(color)); textSize = 30f; gravity = Gravity.CENTER })
        return card
    }

    private fun infoCard(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg("#F8FAFC", 22)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(TextView(context).apply { text = title; setTextColor(Color.parseColor("#111827")); textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply { text = body; setTextColor(Color.parseColor("#64748B")); textSize = 13f; setPadding(0, dp(6), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    private fun pill(text: String, color: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        background = bg(color, 16)
        setOnClickListener { action() }
    }
    private fun bg(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
