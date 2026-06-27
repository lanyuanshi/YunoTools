package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
    private lateinit var modeTitle: TextView
    private lateinit var modeDesc: TextView
    private lateinit var smartPanel: LinearLayout
    private lateinit var keypadPanel: LinearLayout
    private val tabButtons = mutableMapOf<String, Button>()
    private var expression = ""
    private var lastResult = ""
    private var activeMode = "标准"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(20))
            background = gradient("#E0EAFF", "#F8FAFC", 0)
        }
        root.addView(header())
        root.addView(displayCard())
        root.addView(modeTabs())
        root.addView(modeInfoCard())
        smartPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keypadPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(smartPanel)
        root.addView(keypadPanel)
        setContentView(ScrollView(this).apply { addView(root) })
        switchMode("标准")
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = gradient("#1D4ED8", "#7C3AED", 30)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        addView(TextView(context).apply {
            text = "全能计算器"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            text = "标准 / 科学 / 程序员 / 单位换算 / 日期 / 财务 / 健康，一套重新整理。"
            textSize = 13.5f
            setTextColor(Color.argb(235, 255, 255, 255))
            setPadding(0, dp(7), 0, 0)
        })
    }

    private fun displayCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#0F172A", 28)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        expressionText = TextView(context).apply {
            text = "0"
            gravity = Gravity.END
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 3
        }
        resultText = TextView(context).apply {
            text = "= 0"
            gravity = Gravity.END
            textSize = 17f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(8), 0, 0)
            setTextIsSelectable(true)
            setOnClickListener { copy(lastResult.ifBlank { text.toString().removePrefix("= ") }) }
        }
        addView(expressionText)
        addView(resultText)
    }

    private fun modeTabs() = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addView(row)
        listOf("标准", "科学", "程序员", "换算", "日期", "房贷", "个税", "BMI", "折扣", "小费").forEach { name ->
            val button = Button(context).apply {
                text = name
                textSize = 13f
                minHeight = 0
                minWidth = 0
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { rightMargin = dp(8) }
                setOnClickListener { switchMode(name) }
            }
            tabButtons[name] = button
            row.addView(button)
        }
    }

    private fun modeInfoCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#FFFFFF", 24)
        elevation = dp(2).toFloat()
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        modeTitle = TextView(context).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        }
        modeDesc = TextView(context).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(5), 0, 0)
        }
        addView(modeTitle)
        addView(modeDesc)
    }

    private fun switchMode(name: String) {
        activeMode = name
        refreshTabs()
        modeTitle.text = "$name · 全新布局"
        modeDesc.text = when (name) {
            "标准" -> "日常四则运算，常用操作更大更清晰，结果可点击复制。"
            "科学" -> "支持三角函数、对数、开方、幂、圆周率、自然常数和括号。"
            "程序员" -> "进制转换、位运算、移位一次完成，适合开发和调试。"
            "换算" -> "长度、重量、温度、面积、速度、时间、存储容量统一换算。"
            "日期" -> "日期间隔、日期加减、今天距离目标日都能算。"
            "房贷" -> "等额本息、等额本金首月、总利息估算。"
            "个税" -> "按月收入、扣除项估算个税与税后收入。"
            "BMI" -> "BMI、身体状态和标准体重范围。"
            "折扣" -> "折扣、满减、优惠后价格和节省金额。"
            else -> "小费、AA、人均和总计快速计算。"
        }
        safeRun("打开${name}计算器失败") {
            smartPanel.removeAllViews()
            keypadPanel.removeAllViews()
            when (name) {
                "标准" -> showStandard()
                "科学" -> showScientific()
                "程序员" -> showProgrammer()
                "换算" -> showConverter()
                "日期" -> showDate()
                "房贷" -> showMortgage()
                "个税" -> showTax()
                "BMI" -> showBmi()
                "折扣" -> showDiscount()
                "小费" -> showTip()
            }
        }
    }

    private fun refreshTabs() {
        tabButtons.forEach { (name, button) ->
            val active = name == activeMode
            button.setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#1F2937"))
            button.background = rounded(if (active) modeColor(name) else "#FFFFFF", 18)
            button.elevation = if (active) dp(3).toFloat() else 0f
        }
    }

    private fun showStandard() {
        addKeypad(listOf("C", "⌫", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "-", "1", "2", "3", "+", "±", "0", ".", "="))
        smartPanel.addView(actionStrip("复制结果" to { copy(lastResult.ifBlank { resultText.text.toString().removePrefix("= ") }) }, "清空" to { clearExpression() }))
    }

    private fun showScientific() {
        smartPanel.addView(quickFunctionGrid(listOf("sin", "cos", "tan", "√", "ln", "log", "x²", "^", "π", "e", "(", ")")))
        addKeypad(listOf("C", "⌫", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "-", "1", "2", "3", "+", "±", "0", ".", "="))
    }

    private fun showProgrammer() {
        val input = edit("十进制整数", "255")
        val out = resultBox("转换后会显示 BIN / OCT / DEC / HEX")
        smartPanel.addView(toolCard("进制转换", listOf(input, out), "转换", "#16A34A") {
            val n = input.longOrToast("请输入整数") ?: return@toolCard
            out.text = "BIN  ${n.toString(2)}\nOCT  ${n.toString(8)}\nDEC  $n\nHEX  ${n.toString(16).uppercase()}"
        })
        val a = edit("整数 A", "12")
        val b = edit("整数 B", "5")
        val bitOut = resultBox("位运算结果")
        smartPanel.addView(toolCard("位运算", listOf(a, b, bitOut), "计算 AND / OR / XOR / 位移", "#15803D") {
            val left = a.longOrToast("请输入整数 A") ?: return@toolCard
            val right = b.longOrToast("请输入整数 B") ?: return@toolCard
            bitOut.text = "A AND B = ${left and right}\nA OR B = ${left or right}\nA XOR B = ${left xor right}\nA << 1 = ${left shl 1}\nA >> 1 = ${left shr 1}"
        })
    }

    private fun showConverter() {
        addConvert("长度", "米", listOf("厘米" to 100.0, "毫米" to 1000.0, "千米" to 0.001, "英寸" to 39.3700787, "英尺" to 3.2808399))
        addConvert("重量", "千克", listOf("克" to 1000.0, "吨" to 0.001, "斤" to 2.0, "磅" to 2.2046226, "盎司" to 35.2739619))
        addConvert("温度", "摄氏度", emptyList()) { v -> "华氏度：${fmt(v * 9 / 5 + 32)} °F\n开尔文：${fmt(v + 273.15)} K" }
        addConvert("面积", "平方米", listOf("平方厘米" to 10000.0, "平方千米" to 0.000001, "亩" to 0.0015, "公顷" to 0.0001))
        addConvert("速度", "米/秒", listOf("千米/小时" to 3.6, "英里/小时" to 2.236936, "节" to 1.943844))
        addConvert("时间", "小时", listOf("分钟" to 60.0, "秒" to 3600.0, "天" to 1.0 / 24.0, "周" to 1.0 / 168.0))
        addConvert("存储", "GB", listOf("MB" to 1024.0, "KB" to 1024.0 * 1024.0, "TB" to 1.0 / 1024.0, "GiB" to 0.9313226))
    }

    private fun showDate() {
        val start = edit("开始日期 yyyy-MM-dd", todayDate())
        val end = edit("结束日期 yyyy-MM-dd", "2026-12-31")
        val out = resultBox("日期间隔结果")
        smartPanel.addView(toolCard("日期间隔", listOf(start, end, out), "计算间隔", "#0D9488") {
            runCatching {
                val a = stripTime(parseDate(start.text.toString().trim()))
                val b = stripTime(parseDate(end.text.toString().trim()))
                val days = ((b.timeInMillis - a.timeInMillis) / DAY_MILLIS).toInt()
                out.text = "相差：${abs(days)} 天\n开始：${formatDate(a)}\n结束：${formatDate(b)}"
            }.onFailure { toast("日期格式应为 yyyy-MM-dd") }
        })
        val base = edit("基准日期 yyyy-MM-dd", todayDate())
        val add = edit("加减天数，例如 30 或 -7", "30")
        val targetOut = resultBox("目标日期")
        smartPanel.addView(toolCard("日期加减", listOf(base, add, targetOut), "计算目标日", "#0F766E") {
            runCatching {
                targetOut.text = formatDate(parseDate(base.text.toString().trim()).apply { add(Calendar.DAY_OF_MONTH, add.text.toString().trim().toInt()) })
            }.onFailure { toast("请检查日期和天数") }
        })
    }

    private fun showMortgage() {
        val amount = edit("贷款总额（万元）", "100")
        val years = edit("贷款年限", "30")
        val rate = edit("年利率 %", "3.95")
        val out = resultBox("房贷结果")
        smartPanel.addView(toolCard("房贷计算", listOf(amount, years, rate, out), "计算月供", "#0284C7") {
            val principal = (amount.numOrToast("请输入贷款总额") ?: return@toolCard) * 10000
            val months = ((years.numOrToast("请输入年限") ?: return@toolCard) * 12).roundToInt().coerceAtLeast(1)
            val monthRate = (rate.numOrToast("请输入利率") ?: return@toolCard) / 100 / 12
            val equalPayment = if (monthRate == 0.0) principal / months else principal * monthRate * (1 + monthRate).pow(months) / ((1 + monthRate).pow(months) - 1)
            val total = equalPayment * months
            val firstPrincipal = principal / months + principal * monthRate
            out.text = "等额本息月供：${money(equalPayment)}\n总利息：${money(total - principal)}\n等额本金首月：${money(firstPrincipal)}\n贷款期数：$months 期"
        })
    }

    private fun showTax() {
        val salary = edit("税前月收入", "15000")
        val social = edit("五险一金 / 专项扣除", "3000")
        val out = resultBox("个税估算结果")
        smartPanel.addView(toolCard("个人所得税", listOf(salary, social, out), "计算个税", "#DB2777") {
            val income = salary.numOrToast("请输入收入") ?: return@toolCard
            val taxable = income - (social.numOrNull() ?: 0.0) - 5000
            val tax = monthlyTax(taxable.coerceAtLeast(0.0))
            out.text = "应纳税所得额：${money(taxable.coerceAtLeast(0.0))}\n预估个税：${money(tax)}\n税后收入：${money(income - tax)}"
        })
    }

    private fun showBmi() {
        val height = edit("身高 cm", "170")
        val weight = edit("体重 kg", "65")
        val out = resultBox("BMI 结果")
        smartPanel.addView(toolCard("BMI 与健康体重", listOf(height, weight, out), "计算 BMI", "#10B981") {
            val h = (height.numOrToast("请输入身高") ?: return@toolCard) / 100
            val w = weight.numOrToast("请输入体重") ?: return@toolCard
            if (h <= 0) return@toolCard toast("身高要大于 0")
            val bmi = w / (h * h)
            val status = when { bmi < 18.5 -> "偏瘦"; bmi < 24 -> "正常"; bmi < 28 -> "超重"; else -> "肥胖" }
            out.text = "BMI：${fmt(bmi)}\n状态：$status\n标准体重范围：${fmt(18.5 * h * h)} - ${fmt(23.9 * h * h)} kg"
        })
    }

    private fun showDiscount() {
        val price = edit("原价", "199")
        val discount = edit("折扣，例如 8.5 表示 85 折", "8.5")
        val reduce = edit("满减 / 优惠券金额", "20")
        val out = resultBox("优惠结果")
        smartPanel.addView(toolCard("折扣与满减", listOf(price, discount, reduce, out), "计算到手价", "#E11D48") {
            val p = price.numOrToast("请输入原价") ?: return@toolCard
            val d = discount.numOrToast("请输入折扣") ?: return@toolCard
            val r = reduce.numOrNull() ?: 0.0
            val finalPrice = (p * d / 10 - r).coerceAtLeast(0.0)
            out.text = "到手价：${money(finalPrice)}\n节省：${money(p - finalPrice)}\n实际折扣：${fmt(finalPrice / p * 100)}%"
        })
    }

    private fun showTip() {
        val bill = edit("账单金额", "128")
        val rate = edit("小费 / 服务费 %", "15")
        val people = edit("人数", "2")
        val out = resultBox("AA 结果")
        smartPanel.addView(toolCard("小费 / AA", listOf(bill, rate, people, out), "计算人均", "#F97316") {
            val b = bill.numOrToast("请输入账单金额") ?: return@toolCard
            val r = rate.numOrToast("请输入比例") ?: return@toolCard
            val n = (people.numOrToast("请输入人数") ?: return@toolCard).roundToInt().coerceAtLeast(1)
            val tip = b * r / 100
            val total = b + tip
            out.text = "服务费：${money(tip)}\n总计：${money(total)}\n每人：${money(total / n)}"
        })
    }

    private fun addKeypad(keys: List<String>) {
        keypadPanel.addView(GridLayout(this).apply {
            columnCount = 4
            keys.forEach { key ->
                addView(calcKey(key), GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(58)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
        })
    }

    private fun quickFunctionGrid(keys: List<String>) = GridLayout(this).apply {
        columnCount = 4
        background = rounded("#FFFFFF", 24)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        keys.forEach { key ->
            addView(calcKey(key, compact = true), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(44)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    private fun calcKey(key: String, compact: Boolean = false) = Button(this).apply {
        text = key
        textSize = if (compact) 14f else 17f
        typeface = Typeface.DEFAULT_BOLD
        minHeight = 0
        minWidth = 0
        val op = key in listOf("+", "-", "×", "÷", "=", "^", "sin", "cos", "tan", "√", "ln", "log")
        val danger = key in listOf("C", "⌫")
        setTextColor(Color.parseColor(if (key == "=") "#FFFFFF" else if (op) "#FFFFFF" else "#0F172A"))
        background = rounded(when {
            key == "=" -> "#2563EB"
            op -> "#7C3AED"
            danger -> "#DBEAFE"
            else -> "#FFFFFF"
        }, 18)
        setOnClickListener { safeRun("按键处理失败") { handleKey(key) } }
    }

    private fun handleKey(key: String) {
        when (key) {
            "C" -> clearExpression()
            "⌫" -> expression = expression.dropLast(1)
            "=" -> evaluate()
            "±" -> expression = if (expression.startsWith("-")) expression.drop(1) else if (expression.isNotBlank()) "-$expression" else "-"
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
        if (expression.isBlank() || expression == "-") return
        runCatching { ExpressionParser(expression).parse() }
            .onSuccess { value ->
                lastResult = fmt(value)
                resultText.text = "= $lastResult"
                expression = lastResult
            }
            .onFailure {
                toast("表达式错误")
                resultText.text = "= --"
            }
        refreshDisplay()
    }

    private fun addConvert(title: String, unit: String, targets: List<Pair<String, Double>>, custom: ((Double) -> String)? = null) {
        val input = edit("输入$unit", "1")
        val out = resultBox("换算结果")
        smartPanel.addView(toolCard(title, listOf(input, out), "换算", "#2563EB") {
            val v = input.numOrToast("请输入数字") ?: return@toolCard
            out.text = custom?.invoke(v) ?: targets.joinToString("\n") { "${it.first}：${fmt(v * it.second)}" }
        })
    }

    private fun toolCard(title: String, views: List<View>, buttonText: String, color: String, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#FFFFFF", 24)
        elevation = dp(2).toFloat()
        setPadding(dp(16), dp(14), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        addView(TextView(context).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, 0, 0, dp(10))
        })
        views.forEach { addView(it) }
        addView(pill(buttonText, color) { safeRun("${title}失败", action) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
    }

    private fun actionStrip(vararg actions: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        actions.forEachIndexed { index, action ->
            addView(pill(action.first, if (index == 0) "#2563EB" else "#64748B", action.second), LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (index < actions.lastIndex) rightMargin = dp(8) })
        }
    }

    private fun edit(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        textSize = 15f
        setTextColor(Color.parseColor("#111827"))
        setHintTextColor(Color.parseColor("#94A3B8"))
        background = rounded("#F8FAFC", 16)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) }
    }

    private fun resultBox(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.parseColor("#334155"))
        background = rounded("#F8FAFC", 16)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun pill(textValue: String, color: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = rounded(color, 18)
        setOnClickListener { action() }
    }

    private fun refreshDisplay() { expressionText.text = expression.ifBlank { "0" } }
    private fun clearExpression() { expression = ""; lastResult = ""; expressionText.text = "0"; resultText.text = "= 0" }
    private fun copy(text: String) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("calc", text)); toast("已复制") }
    private fun safeRun(msg: String, block: () -> Unit) { runCatching(block).onFailure { toast(msg) } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun rounded(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun gradient(a: String, b: String, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))).apply { cornerRadius = dp(radius).toFloat() }
    private fun modeColor(name: String): String = when (name) { "标准" -> "#2563EB"; "科学" -> "#7C3AED"; "程序员" -> "#16A34A"; "换算" -> "#F59E0B"; "日期" -> "#0D9488"; "房贷" -> "#0284C7"; "个税" -> "#DB2777"; "BMI" -> "#10B981"; "折扣" -> "#E11D48"; else -> "#F97316" }
    private fun EditText.numOrNull() = text.toString().trim().toDoubleOrNull()
    private fun EditText.numOrToast(msg: String) = numOrNull() ?: run { toast(msg); null }
    private fun EditText.longOrToast(msg: String) = text.toString().trim().toLongOrNull() ?: run { toast(msg); null }
    private fun fmt(v: Double): String = if (v.isNaN() || v.isInfinite()) "--" else if (abs(v - v.roundToInt()) < 0.0000001 && abs(v) < Int.MAX_VALUE) v.roundToInt().toString() else "%.6f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    private fun money(v: Double) = "¥${"%,.2f".format(Locale.US, v)}"
    private fun monthlyTax(x: Double): Double = when { x <= 3000 -> x * .03; x <= 12000 -> x * .10 - 210; x <= 25000 -> x * .20 - 1410; x <= 35000 -> x * .25 - 2660; x <= 55000 -> x * .30 - 4410; x <= 80000 -> x * .35 - 7160; else -> x * .45 - 15160 }.coerceAtLeast(0.0)
    private fun parseDate(text: String): Calendar = Calendar.getInstance().apply { time = SimpleDateFormat(DATE_PATTERN, Locale.US).apply { isLenient = false }.parse(text) ?: error("bad date") }
    private fun stripTime(cal: Calendar): Calendar = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private fun formatDate(cal: Calendar) = SimpleDateFormat(DATE_PATTERN, Locale.US).format(cal.time)
    private fun todayDate() = formatDate(Calendar.getInstance())

    private class ExpressionParser(private val input: String) {
        private var pos = -1
        private var ch = 0
        fun parse(): Double { nextChar(); val x = parseExpression(); if (pos < input.length) error("Unexpected"); return x }
        private fun nextChar() { ch = if (++pos < input.length) input[pos].code else -1 }
        private fun eat(c: Int): Boolean { while (ch == ' '.code) nextChar(); return if (ch == c) { nextChar(); true } else false }
        private fun parseExpression(): Double { var x = parseTerm(); while (true) x = when { eat('+'.code) -> x + parseTerm(); eat('-'.code) -> x - parseTerm(); else -> return x } }
        private fun parseTerm(): Double { var x = parseFactor(); while (true) x = when { eat('*'.code) -> x * parseFactor(); eat('/'.code) -> x / parseFactor(); eat('%'.code) -> x % parseFactor(); else -> return x } }
        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            val start = pos
            var x: Double
            when {
                eat('('.code) -> { x = parseExpression(); eat(')'.code) }
                (ch in '0'.code..'9'.code) || ch == '.'.code -> { while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar(); x = input.substring(start, pos).toDouble() }
                ch in 'a'.code..'z'.code -> { while (ch in 'a'.code..'z'.code) nextChar(); val func = input.substring(start, pos); x = parseFactor(); x = when (func) { "sqrt" -> sqrt(x); "sin" -> kotlin.math.sin(Math.toRadians(x)); "cos" -> kotlin.math.cos(Math.toRadians(x)); "tan" -> kotlin.math.tan(Math.toRadians(x)); "ln" -> ln(x); "log" -> log10(x); else -> error("Unknown") } }
                else -> error("Unexpected")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
