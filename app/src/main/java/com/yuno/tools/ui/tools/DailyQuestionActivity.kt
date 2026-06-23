package com.yuno.tools.ui.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class DailyQuestionActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("daily_question", Context.MODE_PRIVATE) }
    private lateinit var questionText: TextView
    private lateinit var statusText: TextView
    private lateinit var explanationText: TextView
    private lateinit var optionsBox: LinearLayout
    private lateinit var streakText: TextView
    private lateinit var refreshButton: Button
    private lateinit var copyButton: Button
    private lateinit var todayKey: String
    private lateinit var current: Question

    private data class Question(val title: String, val options: List<String>, val answer: Int, val explain: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        current = questionForToday()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            background = gradient("#FFF7ED", "#EEF2FF", 0)
        }
        root.addView(hero())
        root.addView(infoCard())
        questionText = TextView(this).apply {
            text = current.title
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            background = rounded("#FFFFFF", 24)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(questionText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        optionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(optionsBox)
        explanationText = TextView(this).apply {
            text = "选一个答案，今天的大脑就算营业了。"
            textSize = 15f
            setTextColor(Color.parseColor("#475569"))
            background = rounded("#FFFFFF", 22)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(explanationText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) })
        root.addView(actions())
        setContentView(ScrollView(this).apply { addView(root) })
        renderOptions()
        restoreTodayAnswer()
    }

    private fun hero() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = gradient("#F97316", "#7C3AED", 30)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        addView(TextView(context).apply { text = "每日一题"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        addView(TextView(context).apply { text = "每天随机一道离谱题，认真答就输了，但不答也不太礼貌。"; textSize = 13.5f; setTextColor(Color.argb(238,255,255,255)); setPadding(0, dp(8), 0, 0) })
    }

    private fun infoCard() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded("#FFFFFF", 22)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        statusText = TextView(context).apply { text = "今日：$todayKey"; textSize = 14f; setTextColor(Color.parseColor("#334155")) }
        streakText = TextView(context).apply { text = "连续营业 ${prefs.getInt("streak", 0)} 天"; textSize = 14f; gravity = Gravity.END; setTextColor(Color.parseColor("#F97316")); typeface = Typeface.DEFAULT_BOLD }
        addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        addView(streakText, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun renderOptions() {
        optionsBox.removeAllViews()
        current.options.forEachIndexed { index, option ->
            optionsBox.addView(Button(this).apply {
                text = "${'A' + index}. $option"
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.parseColor("#111827"))
                background = rounded("#FFFFFF", 18)
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { choose(index) }
            }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) })
        }
    }

    private fun choose(index: Int) {
        val right = index == current.answer
        val answered = prefs.getString("answered_date", "") == todayKey
        if (!answered) {
            val lastDate = prefs.getString("last_date", "") ?: ""
            val streak = if (lastDate.isBlank() || lastDate == todayKey) prefs.getInt("streak", 0) else prefs.getInt("streak", 0) + 1
            prefs.edit().putString("answered_date", todayKey).putString("last_date", todayKey).putInt("streak", streak.coerceAtLeast(1)).putInt("answer_$todayKey", index).apply()
        } else {
            prefs.edit().putInt("answer_$todayKey", index).apply()
        }
        paintAnswer(index)
        explanationText.text = if (right) "答对了，但这不代表题目有道理。\n${current.explain}" else "答错了，不过这题本来就不太做人。\n正确答案：${'A' + current.answer}. ${current.options[current.answer]}\n${current.explain}"
        streakText.text = "连续营业 ${prefs.getInt("streak", 1)} 天"
    }

    private fun restoreTodayAnswer() {
        if (prefs.getString("answered_date", "") == todayKey) {
            val index = prefs.getInt("answer_$todayKey", -1)
            if (index >= 0) {
                paintAnswer(index)
                explanationText.text = "今天已经答过啦。\n${current.explain}"
            }
        }
    }

    private fun paintAnswer(selected: Int) {
        for (i in 0 until optionsBox.childCount) {
            val btn = optionsBox.getChildAt(i) as? Button ?: continue
            val color = when (i) { current.answer -> "#DCFCE7"; selected -> "#FEE2E2"; else -> "#FFFFFF" }
            val textColor = when (i) { current.answer -> "#166534"; selected -> "#991B1B"; else -> "#111827" }
            btn.background = rounded(color, 18)
            btn.setTextColor(Color.parseColor(textColor))
        }
    }

    private fun actions() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        refreshButton = pill("换一道离谱题", "#7C3AED") {
            current = randomQuestion()
            questionText.text = current.title
            explanationText.text = "临时换题不影响今日记录，只负责把脑子摇匀。"
            renderOptions()
        }
        copyButton = pill("复制题目", "#2563EB") {
            val text = buildString {
                append(current.title).append('\n')
                current.options.forEachIndexed { i, it -> append('A' + i).append(". " ).append(it).append('\n') }
            }
            (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("每日一题", text))
            toast("已复制")
        }
        addView(refreshButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        addView(copyButton, LinearLayout.LayoutParams(0, dp(48), 1f))
    }

    private fun questionForToday(): Question {
        val daySeed = todayKey.filter { it.isDigit() }.toIntOrNull() ?: 1
        return questions()[abs(daySeed) % questions().size]
    }

    private fun randomQuestion(): Question {
        val now = System.currentTimeMillis().toInt()
        return questions()[abs(now) % questions().size]
    }

    private fun questions() = listOf(
        Question("如果一只鸽子开会迟到，它最可能提交什么理由？", listOf("路上堵云", "忘带翅膀", "导航让我直飞月球", "被面包贿赂了"), 0, "堵云是鸟类通勤的顶级借口，听起来离谱但很有画面。"),
        Question("冰箱半夜嗡嗡响，最科学又最不科学的解释是？", listOf("压缩机工作", "它在练低音炮", "西瓜在开演唱会", "冷空气开会"), 0, "正确答案还是压缩机，但其他选项更像它的副业。"),
        Question("如果手机电量只剩 1%，你应该先做什么？", listOf("找充电器", "给它加油打气", "截图纪念", "告诉它别睡"), 0, "先找充电器，情绪价值可以边充边给。"),
        Question("为什么袜子总会少一只？", listOf("洗衣机吞了", "它去追梦了", "左右脚性格不合", "宇宙收税"), 0, "现实里多半是洗衣机和角落合谋，但宇宙收税也不是没可能。"),
        Question("当老板说‘简单改一下’，真实含义是什么？", listOf("重做但别说重做", "真的简单", "改个颜色", "喝口水就好了"), 0, "这是一种职场压缩包，解压后通常很大。"),
        Question("猫盯着空气看，最可能是因为？", listOf("听到细微声音", "空气欠它钱", "看见隐形外卖", "在加载系统更新"), 0, "猫的听觉很灵，但它们也确实像在审核空气。"),
        Question("如果键盘会说话，它最想控诉什么？", listOf("别再拍我了", "空格键想退休", "回车键压力大", "以上都对"), 3, "键盘每天承受太多指指点点，以上都对。"),
        Question("一杯奶茶最怕听到哪句话？", listOf("少糖去冰", "我要减肥", "你热量多少", "吸管呢"), 2, "热量问题是奶茶界最尖锐的灵魂拷问。"),
        Question("如果地球请假一天，最合理的替班是谁？", listOf("月亮", "一个超大橙子", "老板的饼", "旋转木马"), 0, "月亮至少熟悉轨道，虽然业务范围不太一样。"),
        Question("为什么周一看起来比其他日子长？", listOf("心理感受", "周一偷偷加班", "时间被拉面师傅拉长了", "闹钟施法"), 0, "心理感受最科学，但拉面师傅也很可疑。")
    )

    private fun pill(text: String, color: String, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 14f; setTextColor(Color.WHITE); background = rounded(color, 18); setOnClickListener { action() } }
    private fun rounded(c: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(c)); cornerRadius = dp(r).toFloat() }
    private fun gradient(a: String, b: String, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))).apply { cornerRadius = dp(r).toFloat() }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
