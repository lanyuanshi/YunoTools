package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CalculatorActivity : AppCompatActivity() {
    private companion object {
        private const val DATE_PATTERN = "yyyy-MM-dd"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }

    private lateinit var expressionText: TextView
    private lateinit var resultText: TextView
    private lateinit var panel: LinearLayout
    private lateinit var modeTitle: TextView
    private lateinit var tabRow: LinearLayout
    private val tabButtons = mutableMapOf<String, Button>()
    private var expression = ""
    private var lastResult = ""
    private var activeMode = "标准"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            background = gradient("#EEF2FF", "#F8FAFC", 0)
        }
        root.addView(hero())
        expressionText = TextView(this).apply {
            text = "0"
            gravity = Gravity.END
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            background = rounded("#FFFFFF", 24)
            setPadding(dp(18), dp(18), dp(18), dp(5))
        }
        resultText = TextView(this).apply {
            text = "= 0"
            gravity = Gravity.END
            textSize = 16f
            setTextColor(Color.parseColor("#64748B"))
            background = rounded("#FFFFFF", 24)
            setPadding(dp(18), dp(5), dp(18), dp(18))
            setTextIsSelectable(true)
        }
        root.addView(expressionText, LinearLayout.LayoutParams(-1, -2))
        root.addView(resultText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        root.addView(modeTabs())
        modeTitle = TextView(this).apply {
            text = "标准计算器"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            background = rounded("#DBEAFE", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        root.addView(modeTitle)
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panel)
        setContentView(ScrollView(this).apply { addView(root) })
        switchMode("标准", ::showStandard)
    }

    private fun hero() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = gradient("#2563EB", "#9333EA", 30)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        addView(TextView(context).apply { text = "全能计算器"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        addView(TextView(context).apply { text = "常规、科学、程序员、单位、日期、房贷、个税、BMI、折扣、小费，一页整合。"; textSize = 13.5f; setTextColor(Color.argb(235,255,255,255)); setPadding(0, dp(8), 0, 0) })
    }

    private fun modeTabs() = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(12)) }
        addView(tabRow)
        listOf(
            "标准" to { showStandard() }, "科学" to { showScientific() }, "程序员" to { showProgrammer() },
            "换算" to { showConverter() }, "日期" to { showDate() }, "房贷" to { showMortgage() },
            "个税" to { showTax() }, "BMI" to { showBmi() }, "折扣" to { showDiscount() }, "小费" to { showTip() }
        ).forEach { (name, action) ->
            val button = tab(name) { switchMode(name, action) }
            tabButtons[name] = button
            tabRow.addView(button)
        }
    }

    private fun switchMode(name: String, action: () -> Unit) {
        activeMode = name
        if (::modeTitle.isInitialized) {
            modeTitle.text = "${name}计算器 · v1.2.30 已修复切换"
            modeTitle.background = rounded(modeColor(name, light = true), 20)
        }
        refreshTabs()
        safeRun("打开${name}计算器失败") { action() }
    }

    private fun refreshTabs() {
        tabButtons.forEach { (name, btn) ->
            val active = name == activeMode
            btn.setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#111827"))
            btn.background = rounded(if (active) modeColor(name) else "#FFFFFF", 18)
        }
    }


    private fun modeColor(name: String, light: Boolean = false): String = when (name) {
        "标准" -> if (light) "#DBEAFE" else "#2563EB"
        "科学" -> if (light) "#EDE9FE" else "#7C3AED"
        "程序员" -> if (light) "#DCFCE7" else "#16A34A"
        "换算" -> if (light) "#FEF3C7" else "#F59E0B"
        "日期" -> if (light) "#CCFBF1" else "#0D9488"
        "房贷" -> if (light) "#E0F2FE" else "#0284C7"
        "个税" -> if (light) "#FCE7F3" else "#DB2777"
        "BMI" -> if (light) "#DCFCE7" else "#10B981"
        "折扣" -> if (light) "#FFE4E6" else "#E11D48"
        else -> if (light) "#FFEDD5" else "#F97316"
    }

    private fun showStandard() {
        panel.removeAllViews()
        panel.addView(grid(listOf("C", "⌫", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "-", "1", "2", "3", "+", "±", "0", ".", "=")))
        panel.addView(actionRow(pill("复制结果", "#2563EB") { copy(lastResult.ifBlank { resultText.text.toString().removePrefix("= ") }) }, pill("清空", "#64748B") { clearExpression() }))
    }

    private fun showScientific() {
        panel.removeAllViews()
        panel.addView(grid(listOf("sin", "cos", "tan", "√", "ln", "log", "x²", "^", "π", "e", "(", ")", "7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "-", "C", "0", ".", "+", "⌫", "%", "±", "=")))
        panel.addView(tipBox("三角函数按角度计算，支持括号和幂运算，例如 sin(30)+2^3。"))
    }

    private fun showProgrammer() {
        panel.removeAllViews()
        panel.addView(tipBox("程序员模式已可打开：支持进制转换、AND / OR / XOR 和位移。"))
        val input = edit("输入十进制整数", "255")
        val out = resultBox("二进制 / 八进制 / 十六进制结果")
        panel.addView(card("进制转换", input, out, "转换", "#2563EB") {
            val n = input.longOrToast("请输入整数") ?: return@card
            out.text = "BIN  ${n.toString(2)}\nOCT  ${n.toString(8)}\nDEC  $n\nHEX  ${n.toString(16).uppercase()}"
        })
        val bitA = edit("整数 A", "12")
        val bitB = edit("整数 B", "5")
        val bitOut = resultBox("AND / OR / XOR")
        panel.addView(card("位运算", bitA, bitB, bitOut, "计算", "#7C3AED") {
            val a = bitA.longOrToast("请输入 A") ?: return@card
            val b = bitB.longOrToast("请输入 B") ?: return@card
            bitOut.text = "A AND B = ${a and b}\nA OR B = ${a or b}\nA XOR B = ${a xor b}\nA << 1 = ${a shl 1}\nA >> 1 = ${a shr 1}"
        })
    }

    private fun showConverter() {
        panel.removeAllViews()
        panel.addView(tipBox("换算模式：长度、重量、温度、面积、速度都在这里。"))
        addConvert("长度", "米", listOf("厘米" to 100.0, "毫米" to 1000.0, "千米" to 0.001, "英寸" to 39.3700787, "英尺" to 3.2808399))
        addConvert("重量", "千克", listOf("克" to 1000.0, "吨" to 0.001, "斤" to 2.0, "磅" to 2.2046226, "盎司" to 35.2739619))
        addConvert("温度", "摄氏度", emptyList()) { v -> "华氏度：${fmt(v * 9 / 5 + 32)} °F\n开尔文：${fmt(v + 273.15)} K" }
        addConvert("面积", "平方米", listOf("平方厘米" to 10000.0, "平方千米" to 0.000001, "亩" to 0.0015, "公顷" to 0.0001))
        addConvert("速度", "米/秒", listOf("千米/小时" to 3.6, "英里/小时" to 2.236936, "节" to 1.943844))
    }

    private fun showDate() {
        panel.removeAllViews()
        val start = edit("开始日期 yyyy-MM-dd", "2026-06-21")
        val end = edit("结束日期 yyyy-MM-dd", "2026-07-01")
        val out = resultBox("相差天数")
        panel.addView(card("日期间隔", start, end, out, "计算", "#10B981") {
            runCatching {
                val startCal = parseDate(start.text.toString().trim())
                val endCal = parseDate(end.text.toString().trim())
                val days = ((stripTime(endCal).timeInMillis - stripTime(startCal).timeInMillis) / DAY_MILLIS).toInt()
                out.text = "相差：${abs(days)} 天\n开始：${formatDate(startCal)}\n结束：${formatDate(endCal)}"
            }.onFailure { toast("日期格式应为 yyyy-MM-dd") }
        })
        val base = edit("基准日期 yyyy-MM-dd", todayDate())
        val add = edit("加减天数，例如 30 或 -7", "30")
        val out2 = resultBox("目标日期")
        panel.addView(card("日期加减", base, add, out2, "计算", "#F59E0B") {
            runCatching { out2.text = formatDate(parseDate(base.text.toString().trim()).apply { add(Calendar.DAY_OF_MONTH, add.text.toString().trim().toInt()) }) }.onFailure { toast("请检查日期和天数") }
        })
    }

    private fun showMortgage() {
        panel.removeAllViews()
        val amount = edit("贷款总额（万元）", "100")
        val years = edit("贷款年限", "30")
        val rate = edit("年利率 %", "3.95")
        val out = resultBox("月供结果")
        panel.addView(card("房贷计算", amount, years, rate, out, "计算", "#2563EB") {
            val principal = (amount.numOrToast("请输入贷款总额") ?: return@card) * 10000
            val months = ((years.numOrToast("请输入年限") ?: return@card) * 12).roundToInt().coerceAtLeast(1)
            val monthRate = (rate.numOrToast("请输入利率") ?: return@card) / 100 / 12
            val equalPayment = if (monthRate == 0.0) principal / months else principal * monthRate * (1 + monthRate).pow(months) / ((1 + monthRate).pow(months) - 1)
            val total = equalPayment * months
            val firstPrincipal = principal / months + principal * monthRate
            out.text = "等额本息月供：${money(equalPayment)}\n总利息：${money(total - principal)}\n等额本金首月：${money(firstPrincipal)}\n贷款期数：$months 期"
        })
    }

    private fun showTax() {
        panel.removeAllViews()
        val salary = edit("税前月收入", "15000")
        val social = edit("五险一金/专项扣除", "3000")
        val out = resultBox("个税估算")
        panel.addView(card("个人所得税", salary, social, out, "计算", "#7C3AED") {
            val income = salary.numOrToast("请输入收入") ?: return@card
            val taxable = income - (social.numOrNull() ?: 0.0) - 5000
            val tax = monthlyTax(taxable.coerceAtLeast(0.0))
            out.text = "应纳税所得额：${money(taxable.coerceAtLeast(0.0))}\n预估个税：${money(tax)}\n税后收入：${money(income - tax)}"
        })
    }

    private fun showBmi() {
        panel.removeAllViews()
        val height = edit("身高 cm", "170")
        val weight = edit("体重 kg", "65")
        val out = resultBox("BMI 结果")
        panel.addView(card("BMI 与健康体重", height, weight, out, "计算", "#10B981") {
            val h = (height.numOrToast("请输入身高") ?: return@card) / 100
            val w = weight.numOrToast("请输入体重") ?: return@card
            if (h <= 0) return@card toast("身高要大于 0")
            val bmi = w / (h * h)
            val status = when { bmi < 18.5 -> "偏瘦"; bmi < 24 -> "正常"; bmi < 28 -> "超重"; else -> "肥胖" }
            out.text = "BMI：${fmt(bmi)}\n状态：$status\n标准体重范围：${fmt(18.5*h*h)} - ${fmt(23.9*h*h)} kg"
        })
    }

    private fun showDiscount() {
        panel.removeAllViews()
        val price = edit("原价", "199")
        val discount = edit("折扣，例如 8.5 表示 85 折", "8.5")
        val out = resultBox("折扣结果")
        panel.addView(card("折扣计算", price, discount, out, "计算", "#EC4899") {
            val p = price.numOrToast("请输入原价") ?: return@card
            val d = discount.numOrToast("请输入折扣") ?: return@card
            val finalPrice = p * d / 10
            out.text = "到手价：${money(finalPrice)}\n节省：${money(p - finalPrice)}\n折扣率：${fmt(d * 10)}%"
        })
    }

    private fun showTip() {
        panel.removeAllViews()
        val bill = edit("账单金额", "128")
        val rate = edit("小费比例 %", "15")
        val people = edit("人数", "2")
        val out = resultBox("AA 结果")
        panel.addView(card("小费 / AA", bill, rate, people, out, "计算", "#F97316") {
            val b = bill.numOrToast("请输入账单金额") ?: return@card
            val r = rate.numOrToast("请输入小费比例") ?: return@card
            val n = (people.numOrToast("请输入人数") ?: return@card).roundToInt().coerceAtLeast(1)
            val tip = b * r / 100
            val total = b + tip
            out.text = "小费：${money(tip)}\n总计：${money(total)}\n每人：${money(total / n)}"
        })
    }

    private fun grid(keys: List<String>) = GridLayout(this).apply {
        columnCount = 4
        keys.forEach { key -> addView(calcKey(key), GridLayout.LayoutParams().apply { width = 0; height = dp(56); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
    }

    private fun calcKey(key: String) = Button(this).apply {
        text = key
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        val accent = key == "=" || key in listOf("+", "-", "×", "÷")
        setTextColor(if (accent) Color.WHITE else Color.parseColor("#111827"))
        background = rounded(if (key == "=") "#2563EB" else if (accent) "#7C3AED" else if (key in listOf("C", "⌫")) "#E0E7FF" else "#FFFFFF", 18)
        setOnClickListener { safeRun("按键处理失败") { handleKey(key) } }
    }

    private fun handleKey(key: String) {
        when (key) {
            "C" -> clearExpression()
            "⌫" -> expression = expression.dropLast(1)
            "=" -> evaluate()
            "±" -> expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"
            "π" -> expression += Math.PI.toString()
            "e" -> expression += Math.E.toString()
            "√" -> expression += "sqrt("
            "x²" -> expression += "^2"
            "sin", "cos", "tan", "ln", "log" -> expression += "$key("
            "×" -> expression += "*"
            "÷" -> expression += "/"
            else -> expression += key
        }
        refreshDisplay()
    }

    private fun evaluate() {
        if (expression.isBlank()) return
        runCatching { ExpressionParser(expression).parse() }
            .onSuccess { value -> lastResult = fmt(value); resultText.text = "= $lastResult"; expression = fmt(value) }
            .onFailure { toast("表达式错误"); resultText.text = "= --" }
        refreshDisplay()
    }

    private fun refreshDisplay() { expressionText.text = expression.ifBlank { "0" } }
    private fun clearExpression() { expression = ""; lastResult = ""; expressionText.text = "0"; resultText.text = "= 0" }

    private fun addConvert(title: String, unit: String, targets: List<Pair<String, Double>>, custom: ((Double) -> String)? = null) {
        val input = edit("输入$unit", "1")
        val out = resultBox("换算结果")
        panel.addView(card(title, input, out, "换算", "#2563EB") {
            val v = input.numOrToast("请输入数字") ?: return@card
            out.text = custom?.invoke(v) ?: targets.joinToString("\n") { "${it.first}：${fmt(v * it.second)}" }
        })
    }

    private fun card(title: String, vararg views: View, buttonText: String, color: String, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#FFFFFF", 24)
        elevation = dp(2).toFloat()
        setPadding(dp(16), dp(14), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        addView(TextView(context).apply { text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")); setPadding(0, 0, 0, dp(8)) })
        views.forEach { addView(it) }
        addView(pill(buttonText, color) { safeRun("${title}失败", action) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
    }

    private fun card(title: String, a: EditText, out: TextView, buttonText: String, color: String, action: () -> Unit): LinearLayout = card(title, a, out, buttonText = buttonText, color = color, action = action)
    private fun card(title: String, a: EditText, b: EditText, out: TextView, buttonText: String, color: String, action: () -> Unit): LinearLayout = card(title, a, b, out, buttonText = buttonText, color = color, action = action)
    private fun card(title: String, a: EditText, b: EditText, c: EditText, out: TextView, buttonText: String, color: String, action: () -> Unit): LinearLayout = card(title, a, b, c, out, buttonText = buttonText, color = color, action = action)

    private fun tab(text: String, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#111827")); background = rounded("#FFFFFF", 18); setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(8) } }
    private fun edit(hint: String, value: String = "") = EditText(this).apply { this.hint = hint; setText(value); textSize = 15f; setTextColor(Color.parseColor("#111827")); setHintTextColor(Color.parseColor("#94A3B8")); background = rounded("#F8FAFC", 16); setPadding(dp(12), dp(9), dp(12), dp(9)); layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) } }
    private fun resultBox(text: String) = TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.parseColor("#334155")); background = rounded("#F8FAFC", 16); setPadding(dp(12), dp(10), dp(12), dp(10)); setTextIsSelectable(true) }
    private fun tipBox(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#64748B")); background = rounded("#EEF2FF", 16); setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
    private fun actionRow(vararg views: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0); views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) }) } }
    private fun pill(t: String, color: String, action: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); textSize = 14f; background = rounded(color, 18); setOnClickListener { action() } }
    private fun rounded(c: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(c)); cornerRadius = dp(r).toFloat() }
    private fun gradient(a: String, b: String, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))).apply { cornerRadius = dp(r).toFloat() }
    private fun EditText.numOrNull() = text.toString().trim().toDoubleOrNull()
    private fun EditText.numOrToast(msg: String) = numOrNull() ?: run { toast(msg); null }
    private fun EditText.longOrToast(msg: String) = text.toString().trim().toLongOrNull() ?: run { toast(msg); null }
    private fun fmt(v: Double): String = if (v.isNaN() || v.isInfinite()) "--" else if (abs(v - v.roundToInt()) < 0.0000001 && abs(v) < Int.MAX_VALUE) v.roundToInt().toString() else "%.6f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    private fun money(v: Double) = "¥${"%,.2f".format(Locale.US, v)}"
    private fun monthlyTax(x: Double): Double = when { x <= 3000 -> x * .03; x <= 12000 -> x * .10 - 210; x <= 25000 -> x * .20 - 1410; x <= 35000 -> x * .25 - 2660; x <= 55000 -> x * .30 - 4410; x <= 80000 -> x * .35 - 7160; else -> x * .45 - 15160 }.coerceAtLeast(0.0)
    private fun copy(text: String) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("计算结果", text)); toast("已复制") }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun safeRun(message: String, action: () -> Unit) { runCatching(action).onFailure { toast(message) } }
    private fun parseDate(value: String): Calendar = Calendar.getInstance().apply { time = SimpleDateFormat(DATE_PATTERN, Locale.CHINA).apply { isLenient = false }.parse(value) ?: error("Invalid date") }
    private fun stripTime(calendar: Calendar): Calendar = (calendar.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private fun formatDate(calendar: Calendar): String = SimpleDateFormat(DATE_PATTERN, Locale.CHINA).format(calendar.time)
    private fun todayDate(): String = formatDate(Calendar.getInstance())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private class ExpressionParser(private val source: String) {
        private var pos = -1
        private var ch = 0
        fun parse(): Double { nextChar(); val x = parseExpression(); if (pos < source.length) error("Unexpected"); return x }
        private fun nextChar() { ch = if (++pos < source.length) source[pos].code else -1 }
        private fun eat(c: Int): Boolean { while (ch == ' '.code) nextChar(); return if (ch == c) { nextChar(); true } else false }
        private fun parseExpression(): Double { var x = parseTerm(); while (true) x = when { eat('+'.code) -> x + parseTerm(); eat('-'.code) -> x - parseTerm(); else -> return x } }
        private fun parseTerm(): Double { var x = parseFactor(); while (true) x = when { eat('*'.code) -> x * parseFactor(); eat('/'.code) -> x / parseFactor(); eat('%'.code) -> x % parseFactor(); else -> return x } }
        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val start = pos
            when {
                eat('('.code) -> { x = parseExpression(); if (!eat(')'.code)) error("Missing )") }
                ch in '0'.code..'9'.code || ch == '.'.code -> { while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar(); x = source.substring(start, pos).toDouble() }
                ch in 'a'.code..'z'.code -> { while (ch in 'a'.code..'z'.code) nextChar(); val f = source.substring(start, pos); x = parseFactor(); x = when (f) { "sqrt" -> sqrt(x); "sin" -> kotlin.math.sin(Math.toRadians(x)); "cos" -> kotlin.math.cos(Math.toRadians(x)); "tan" -> kotlin.math.tan(Math.toRadians(x)); "ln" -> ln(x); "log" -> log10(x); else -> error("Unknown") } }
                else -> error("Unexpected")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
