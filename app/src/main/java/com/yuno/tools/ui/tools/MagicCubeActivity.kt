package com.yuno.tools.ui.tools

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt
import kotlin.random.Random

class MagicCubeActivity : AppCompatActivity() {
    private lateinit var cubeGrid: GridLayout
    private lateinit var stepText: TextView
    private val colors = intArrayOf(
        Color.parseColor("#EF4444"), Color.parseColor("#F59E0B"), Color.parseColor("#FACC15"),
        Color.parseColor("#22C55E"), Color.parseColor("#3B82F6"), Color.parseColor("#A855F7")
    )
    private val cells = MutableList(9) { it % 6 }
    private var steps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetData()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            addView(hero("魔方", "简化 3×3 魔方面板：打乱颜色，旋转行列，挑战还原同色面。"))
            cubeGrid = GridLayout(context).apply {
                columnCount = 3
                rowCount = 3
                background = rounded("#111827", 24)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(-1, dp(360)).apply { bottomMargin = dp(14) }
            }
            addView(cubeGrid)
            addView(card().apply {
                stepText = line("步数：0")
                addView(stepText)
                addView(line("玩法：点击下方按钮旋转某一行/列，目标是把九宫格恢复为同色。"))
            })
            addView(row(btn("打乱") { shuffle() }, btn("重置") { reset() }))
            addView(row(btn("上行↻") { rotateRow(0) }, btn("中行↻") { rotateRow(1) }, btn("下行↻") { rotateRow(2) }))
            addView(row(btn("左列↧") { rotateCol(0) }, btn("中列↧") { rotateCol(1) }, btn("右列↧") { rotateCol(2) }))
            render()
        })
    }

    private fun resetData() { for (i in cells.indices) cells[i] = 4; steps = 0 }
    private fun render() {
        cubeGrid.removeAllViews()
        cells.forEachIndexed { index, c ->
            cubeGrid.addView(TextView(this).apply {
                text = ""
                background = roundedColor(colors[c], 12)
                setOnClickListener { cells[index] = (cells[index] + 1) % colors.size; steps++; updateStep() }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        updateStep()
    }
    private fun shuffle() { repeat(24) { if (Random.nextBoolean()) rotateRow(Random.nextInt(3), false) else rotateCol(Random.nextInt(3), false) }; steps = 0; render() }
    private fun reset() { resetData(); render() }
    private fun rotateRow(row: Int, countStep: Boolean = true) { val a = row * 3; val t = cells[a + 2]; cells[a + 2] = cells[a + 1]; cells[a + 1] = cells[a]; cells[a] = t; if (countStep) steps++; render() }
    private fun rotateCol(col: Int, countStep: Boolean = true) { val t = cells[col + 6]; cells[col + 6] = cells[col + 3]; cells[col + 3] = cells[col]; cells[col] = t; if (countStep) steps++; render() }
    private fun updateStep() { stepText.text = if (cells.distinct().size == 1) "步数：$steps · 已还原" else "步数：$steps" }

    private fun hero(t: String, s: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = gradient("#2563EB", "#7C3AED", 24); setPadding(dp(18), dp(18), dp(18), dp(18)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }; addView(TextView(context).apply { text = t; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }); addView(TextView(context).apply { text = s; textSize = 13.5f; setTextColor(Color.argb(230, 255, 255, 255)); setPadding(0, dp(8), 0, 0) }) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded("#FFFFFF", 22); elevation = dp(2).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) } }
    private fun row(vararg bs: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }; bs.forEachIndexed { i, b -> addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (i != bs.lastIndex) rightMargin = dp(8) }) } }
    private fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = rounded("#4F46E5", 16); stateListAnimator = null; setOnClickListener { a() } }
    private fun line(t: String) = TextView(this).apply { text = t; textSize = 15f; setTextColor(Color.parseColor("#334155")); setPadding(0, dp(4), 0, dp(4)) }
    private fun rounded(c: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(c)); cornerRadius = dp(r).toFloat() }
    private fun roundedColor(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat(); setStroke(dp(3), Color.WHITE) }
    private fun gradient(a: String, b: String, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))).apply { cornerRadius = dp(r).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}