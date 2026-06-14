package com.yuno.tools.ui.tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class MagicCubeActivity : AppCompatActivity() {
    private lateinit var cubeView: Cube3DView
    private lateinit var stepText: TextView
    private val colors = intArrayOf(Color.WHITE, Color.parseColor("#FACC15"), Color.parseColor("#EF4444"), Color.parseColor("#F97316"), Color.parseColor("#2563EB"), Color.parseColor("#22C55E"))
    private val faces = Array(6) { f -> IntArray(9) { f } }
    private var steps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetData()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            addView(hero("3D 魔方", "拖动魔方可 3D 旋转视角；支持打乱、复位和基础面旋转练习。"))
            cubeView = Cube3DView(context, faces, colors).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(12) } }
            addView(cubeView)
            addView(card().apply {
                stepText = line("步数：0")
                addView(stepText)
                addView(line("玩法：单指拖动查看 3D 魔方；按钮可打乱或旋转可见面，目标是还原六面同色。"))
            })
            addView(row(btn("打乱") { shuffleCube() }, btn("复位") { resetCube() }))
            addView(row(btn("顶面↻") { rotateFace(0) }, btn("正面↻") { rotateFace(2) }, btn("右面↻") { rotateFace(4) }))
        })
    }

    private fun resetData() { for (f in 0 until 6) for (i in 0 until 9) faces[f][i] = f; steps = 0 }
    private fun resetCube() { resetData(); cubeView.invalidate(); updateStep() }
    private fun shuffleCube() { repeat(28) { rotateFace(Random.nextInt(6), countStep = false) }; steps = 0; updateStep(); cubeView.invalidate() }
    private fun rotateFace(face: Int, countStep: Boolean = true) {
        val a = faces[face].copyOf()
        faces[face][0] = a[6]; faces[face][1] = a[3]; faces[face][2] = a[0]
        faces[face][3] = a[7]; faces[face][4] = a[4]; faces[face][5] = a[1]
        faces[face][6] = a[8]; faces[face][7] = a[5]; faces[face][8] = a[2]
        val ring = when (face) {
            0 -> arrayOf(Pair(2, intArrayOf(0,1,2)), Pair(4, intArrayOf(0,1,2)), Pair(3, intArrayOf(0,1,2)), Pair(5, intArrayOf(0,1,2)))
            1 -> arrayOf(Pair(2, intArrayOf(6,7,8)), Pair(5, intArrayOf(6,7,8)), Pair(3, intArrayOf(6,7,8)), Pair(4, intArrayOf(6,7,8)))
            2 -> arrayOf(Pair(0, intArrayOf(6,7,8)), Pair(5, intArrayOf(2,5,8)), Pair(1, intArrayOf(2,1,0)), Pair(4, intArrayOf(0,3,6)))
            3 -> arrayOf(Pair(0, intArrayOf(0,1,2)), Pair(4, intArrayOf(2,5,8)), Pair(1, intArrayOf(8,7,6)), Pair(5, intArrayOf(0,3,6)))
            4 -> arrayOf(Pair(0, intArrayOf(2,5,8)), Pair(2, intArrayOf(2,5,8)), Pair(1, intArrayOf(2,5,8)), Pair(3, intArrayOf(6,3,0)))
            else -> arrayOf(Pair(0, intArrayOf(0,3,6)), Pair(3, intArrayOf(8,5,2)), Pair(1, intArrayOf(0,3,6)), Pair(2, intArrayOf(0,3,6)))
        }
        val tmp = ring.last().second.map { faces[ring.last().first][it] }
        for (i in ring.size - 1 downTo 1) {
            val from = ring[i - 1]; val to = ring[i]
            for (k in 0..2) faces[to.first][to.second[k]] = faces[from.first][from.second[k]]
        }
        val first = ring[0]
        for (k in 0..2) faces[first.first][first.second[k]] = tmp[k]
        if (countStep) steps++
        updateStep(); cubeView.invalidate()
    }
    private fun updateStep() { if (::stepText.isInitialized) stepText.text = "步数：$steps" }

    class Cube3DView(context: android.content.Context, private val faces: Array<IntArray>, private val colors: IntArray) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var yaw = -32f
        private var pitch = -22f
        private var lastX = 0f
        private var lastY = 0f
        private val faceDefs = listOf(FaceDef(0, Vec(0.0, -1.0, 0.0)), FaceDef(1, Vec(0.0, 1.0, 0.0)), FaceDef(2, Vec(0.0, 0.0, 1.0)), FaceDef(3, Vec(0.0, 0.0, -1.0)), FaceDef(4, Vec(1.0, 0.0, 0.0)), FaceDef(5, Vec(-1.0, 0.0, 0.0)))

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; return true }
                MotionEvent.ACTION_MOVE -> { yaw += (event.x - lastX) * 0.45f; pitch += (event.y - lastY) * 0.35f; pitch = pitch.coerceIn(-70f, 70f); lastX = event.x; lastY = event.y; invalidate(); return true }
            }
            return true
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#F8FAFC"))
            val size = width.coerceAtMost(height) * 0.42f
            val cx = width / 2f
            val cy = height / 2f - dp(8)
            val items = mutableListOf<Poly>()
            val half = 1.5
            for (fd in faceDefs) for (r in 0..2) for (c in 0..2) {
                val x0 = -half + c; val x1 = x0 + 0.92; val y0 = -half + r; val y1 = y0 + 0.92
                val pts = squarePoints(fd.normal, x0, x1, y0, y1)
                val rotated = pts.map { rotate(it) }
                val avgZ = rotated.map { it.z }.average()
                val normalZ = rotate(fd.normal).z
                if (normalZ > -0.18) {
                    val screen = rotated.map { PointF(cx + (it.x * size).toFloat(), cy + (it.y * size).toFloat()) }
                    items += Poly(screen, avgZ, colors[faces[fd.index][r * 3 + c]], normalZ.toFloat())
                }
            }
            items.sortedBy { it.z }.forEach { drawSticker(canvas, it) }
        }
        private fun squarePoints(n: Vec, x0: Double, x1: Double, y0: Double, y1: Double): List<Vec> = when {
            n.z == 1.0 -> listOf(Vec(x0,y0,1.5), Vec(x1,y0,1.5), Vec(x1,y1,1.5), Vec(x0,y1,1.5))
            n.z == -1.0 -> listOf(Vec(-x0,y0,-1.5), Vec(-x1,y0,-1.5), Vec(-x1,y1,-1.5), Vec(-x0,y1,-1.5))
            n.x == 1.0 -> listOf(Vec(1.5,y0,-x0), Vec(1.5,y0,-x1), Vec(1.5,y1,-x1), Vec(1.5,y1,-x0))
            n.x == -1.0 -> listOf(Vec(-1.5,y0,x0), Vec(-1.5,y0,x1), Vec(-1.5,y1,x1), Vec(-1.5,y1,x0))
            n.y == -1.0 -> listOf(Vec(x0,-1.5,y0), Vec(x1,-1.5,y0), Vec(x1,-1.5,y1), Vec(x0,-1.5,y1))
            else -> listOf(Vec(x0,1.5,-y0), Vec(x1,1.5,-y0), Vec(x1,1.5,-y1), Vec(x0,1.5,-y1))
        }
        private fun rotate(v: Vec): Vec {
            val yr = Math.toRadians(yaw.toDouble()); val pr = Math.toRadians(pitch.toDouble())
            val x1 = v.x * cos(yr) + v.z * sin(yr); val z1 = -v.x * sin(yr) + v.z * cos(yr)
            val y2 = v.y * cos(pr) - z1 * sin(pr); val z2 = v.y * sin(pr) + z1 * cos(pr)
            return Vec(x1, y2, z2)
        }
        private fun drawSticker(canvas: Canvas, poly: Poly) {
            val path = Path().apply { moveTo(poly.points[0].x, poly.points[0].y); for (i in 1 until poly.points.size) lineTo(poly.points[i].x, poly.points[i].y); close() }
            paint.style = Paint.Style.FILL; paint.color = shade(poly.color, 0.72f + poly.light.coerceIn(0f, 1f) * 0.28f); canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(4).toFloat(); paint.strokeJoin = Paint.Join.ROUND; paint.color = Color.parseColor("#1F2937"); canvas.drawPath(path, paint)
        }
        private fun shade(color: Int, factor: Float): Int = Color.rgb((Color.red(color) * factor).roundToInt().coerceIn(0,255), (Color.green(color) * factor).roundToInt().coerceIn(0,255), (Color.blue(color) * factor).roundToInt().coerceIn(0,255))
        private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
        data class Vec(val x: Double, val y: Double, val z: Double)
        data class FaceDef(val index: Int, val normal: Vec)
        data class Poly(val points: List<PointF>, val z: Double, val color: Int, val light: Float)
    }

    private fun hero(title: String, sub: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded("#EFFFFFFF", 26); elevation = dp(4).toFloat(); setPadding(dp(18), dp(16), dp(18), dp(16)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }; addView(TextView(context).apply { text = title; textSize = 26f; setTextColor(Color.parseColor("#0F172A")); typeface = Typeface.DEFAULT_BOLD }); addView(TextView(context).apply { text = sub; textSize = 14f; setTextColor(Color.parseColor("#64748B")); setPadding(0, dp(6), 0, 0) }) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded("#EFFFFFFF", 22); elevation = dp(2).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) } }
    private fun line(t: String) = TextView(this).apply { text = t; textSize = 14f; setTextColor(Color.parseColor("#475569")); setPadding(0, dp(3), 0, dp(3)) }
    private fun row(vararg views: View) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) } }
    private fun btn(t: String, cb: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); textSize = 14f; background = rounded("#2563EB", 18); setOnClickListener { cb() } }
    private fun rounded(color: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(r).toFloat(); setStroke(dp(1), Color.parseColor("#66FFFFFF")) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
