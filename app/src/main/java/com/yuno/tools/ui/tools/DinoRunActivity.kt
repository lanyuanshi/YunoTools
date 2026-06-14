package com.yuno.tools.ui.tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt
import kotlin.random.Random

class DinoRunActivity : AppCompatActivity() {
    private lateinit var gameView: ChromeDinoView
    private lateinit var scoreText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(20))
            setBackgroundColor(Color.WHITE)
            addView(TextView(context).apply {
                text = "恐龙快跑"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#202124"))
            })
            addView(TextView(context).apply {
                text = "Chrome 断网页同款风格：点击跳跃，躲避仙人掌。障碍高度已限制在可跳过范围。"
                textSize = 13f
                setTextColor(Color.parseColor("#5F6368"))
                setPadding(0, dp(6), 0, dp(12))
            })
            scoreText = TextView(context).apply {
                text = "00000    HI 00000"
                textSize = 16f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#535353"))
                gravity = android.view.Gravity.END
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            addView(scoreText, LinearLayout.LayoutParams(-1, -2))
            gameView = ChromeDinoView(context) { score, best ->
                scoreText.text = score.toString().padStart(5, '0') + "    HI " + best.toString().padStart(5, '0')
            }
            addView(gameView, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(12) })
            addView(Button(context).apply {
                text = "重新开始"
                textSize = 15f
                setTextColor(Color.WHITE)
                background = rounded("#3C4043", 16)
                stateListAnimator = null
                setOnClickListener { gameView.restart() }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        })
    }

    override fun onResume() { super.onResume(); if (::gameView.isInitialized) gameView.resumeGame() }
    override fun onPause() { if (::gameView.isInitialized) gameView.pauseGame(); super.onPause() }

    class ChromeDinoView(context: android.content.Context, private val scoreCallback: (Int, Int) -> Unit) : View(context), Runnable {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var running = false
        private var gameOver = false
        private var thread: Thread? = null
        private var groundY = 0f
        private var dinoY = 0f
        private var velocity = 0f
        private var obstacleX = 0f
        private var obstacleType = 0
        private var speed = 8.4f
        private var score = 0
        private var frame = 0
        private var cloudX1 = 0f
        private var cloudX2 = 0f
        private val prefs = context.getSharedPreferences("chrome_dino_run", MODE_PRIVATE)
        private var best = prefs.getInt("best", 0)

        private val dinoW get() = dp(46).toFloat()
        private val dinoH get() = dp(50).toFloat()
        private val maxObstacleH get() = dp(46).toFloat()

        init { setBackgroundColor(Color.WHITE); restart() }

        fun restart() {
            gameOver = false
            score = 0
            speed = 8.4f
            velocity = 0f
            dinoY = 0f
            obstacleX = (width.takeIf { it > 0 } ?: 900).toFloat() + dp(180)
            obstacleType = 0
            cloudX1 = (width.takeIf { it > 0 } ?: 900).toFloat() * 0.65f
            cloudX2 = (width.takeIf { it > 0 } ?: 900).toFloat() * 1.15f
            scoreCallback(score, best)
            invalidate()
        }

        fun resumeGame() { if (!running) { running = true; thread = Thread(this).also { it.start() } } }
        fun pauseGame() { running = false; thread = null }

        override fun run() {
            while (running) {
                update()
                postInvalidate()
                Thread.sleep(16)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (gameOver) restart() else jump()
                return true
            }
            return true
        }

        private fun jump() {
            val floor = groundY - dinoH
            if (dinoY >= floor - dp(2)) velocity = -20.5f
        }

        private fun update() {
            if (width <= 0 || height <= 0 || gameOver) return
            groundY = height * 0.70f
            val floor = groundY - dinoH
            if (dinoY == 0f) dinoY = floor
            velocity += 0.92f
            dinoY += velocity
            if (dinoY > floor) { dinoY = floor; velocity = 0f }

            obstacleX -= speed
            if (obstacleX < -dp(80)) {
                obstacleX = width.toFloat() + Random.nextInt(dp(90), dp(210))
                obstacleType = Random.nextInt(0, 3)
                score += 1
                if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
                speed = (speed + 0.18f).coerceAtMost(14.2f)
                scoreCallback(score, best)
            }
            cloudX1 -= speed * 0.22f
            cloudX2 -= speed * 0.18f
            if (cloudX1 < -dp(80)) cloudX1 = width + dp(80).toFloat()
            if (cloudX2 < -dp(80)) cloudX2 = width + dp(160).toFloat()

            if (hitTest()) gameOver = true
            frame++
        }

        private fun hitTest(): Boolean {
            val dino = RectF(dp(64).toFloat() + dp(8), dinoY + dp(8), dp(64).toFloat() + dinoW - dp(6), dinoY + dinoH - dp(3))
            val obs = obstacleRect().apply { inset(dp(5).toFloat(), dp(4).toFloat()) }
            return RectF.intersects(dino, obs)
        }

        private fun obstacleRect(): RectF {
            val w = when (obstacleType) { 0 -> dp(22); 1 -> dp(34); else -> dp(48) }.toFloat()
            val h = when (obstacleType) { 0 -> dp(38).toFloat(); 1 -> dp(44).toFloat(); else -> maxObstacleH }.coerceAtMost(maxObstacleH)
            return RectF(obstacleX, groundY - h, obstacleX + w, groundY)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            groundY = height * 0.70f
            if (dinoY == 0f) dinoY = groundY - dinoH
            paint.isAntiAlias = false
            canvas.drawColor(Color.WHITE)
            drawCloud(canvas, cloudX1, height * 0.22f)
            drawCloud(canvas, cloudX2, height * 0.15f)
            drawGround(canvas)
            drawDino(canvas, dp(64).toFloat(), dinoY)
            drawCactus(canvas, obstacleRect(), obstacleType)
            if (gameOver) drawGameOver(canvas)
        }

        private fun drawGround(canvas: Canvas) {
            paint.color = Color.parseColor("#535353")
            paint.strokeWidth = dp(2).toFloat()
            canvas.drawLine(0f, groundY, width.toFloat(), groundY, paint)
            paint.strokeWidth = dp(1).toFloat()
            for (i in 0..12) {
                val x = ((i * dp(76) - (frame * speed).toInt()) % (width + dp(90))).toFloat()
                canvas.drawLine(x, groundY + dp(10), x + dp(Random.nextInt(10, 26)), groundY + dp(10), paint)
            }
        }

        private fun drawDino(canvas: Canvas, x: Float, y: Float) {
            paint.color = Color.parseColor("#535353")
            // body
            canvas.drawRect(x + dp(8), y + dp(18), x + dp(35), y + dp(45), paint)
            // head
            canvas.drawRect(x + dp(28), y + dp(2), x + dp(54), y + dp(25), paint)
            // mouth pixel
            canvas.drawRect(x + dp(46), y + dp(18), x + dp(58), y + dp(22), paint)
            // neck
            canvas.drawRect(x + dp(25), y + dp(14), x + dp(36), y + dp(34), paint)
            // tail
            canvas.drawRect(x, y + dp(24), x + dp(12), y + dp(31), paint)
            canvas.drawRect(x - dp(8), y + dp(20), x + dp(3), y + dp(26), paint)
            // arms
            canvas.drawRect(x + dp(31), y + dp(31), x + dp(43), y + dp(36), paint)
            // legs
            val onGround = y >= groundY - dinoH - dp(2)
            val step = if (onGround && (frame / 6) % 2 == 0) dp(6) else 0
            canvas.drawRect(x + dp(12), y + dp(43), x + dp(20), y + dp(55) + step, paint)
            canvas.drawRect(x + dp(27), y + dp(43), x + dp(35), y + dp(55) - step, paint)
            // eye
            paint.color = Color.WHITE
            canvas.drawRect(x + dp(44), y + dp(8), x + dp(48), y + dp(12), paint)
        }

        private fun drawCactus(canvas: Canvas, rect: RectF, type: Int) {
            paint.color = Color.parseColor("#535353")
            fun cactus(cx: Float, base: Float, scale: Float) {
                val trunkW = dp(10) * scale
                val h = dp(38) * scale
                canvas.drawRect(cx, base - h, cx + trunkW, base, paint)
                canvas.drawRect(cx - dp(8) * scale, base - h * .62f, cx, base - h * .48f, paint)
                canvas.drawRect(cx - dp(8) * scale, base - h * .75f, cx - dp(4) * scale, base - h * .48f, paint)
                canvas.drawRect(cx + trunkW, base - h * .48f, cx + trunkW + dp(8) * scale, base - h * .34f, paint)
                canvas.drawRect(cx + trunkW + dp(4) * scale, base - h * .58f, cx + trunkW + dp(8) * scale, base - h * .34f, paint)
            }
            when (type) {
                0 -> cactus(rect.left + dp(4), rect.bottom, 1.0f)
                1 -> { cactus(rect.left, rect.bottom, .95f); cactus(rect.left + dp(18), rect.bottom, .75f) }
                else -> { cactus(rect.left, rect.bottom, .85f); cactus(rect.left + dp(16), rect.bottom, 1.05f); cactus(rect.left + dp(34), rect.bottom, .70f) }
            }
        }

        private fun drawCloud(canvas: Canvas, x: Float, y: Float) {
            paint.color = Color.parseColor("#DADCE0")
            paint.strokeWidth = dp(2).toFloat()
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x, y, x + dp(12), y, paint)
            canvas.drawLine(x + dp(12), y, x + dp(18), y - dp(7), paint)
            canvas.drawLine(x + dp(18), y - dp(7), x + dp(30), y - dp(7), paint)
            canvas.drawLine(x + dp(30), y - dp(7), x + dp(38), y, paint)
            canvas.drawLine(x + dp(38), y, x + dp(56), y, paint)
            paint.style = Paint.Style.FILL
        }

        private fun drawGameOver(canvas: Canvas) {
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.MONOSPACE
            paint.color = Color.parseColor("#535353")
            paint.textSize = dp(24).toFloat()
            canvas.drawText("GAME OVER", width / 2f, height * .35f, paint)
            paint.textSize = dp(14).toFloat()
            canvas.drawText("点击屏幕重新开始", width / 2f, height * .35f + dp(28), paint)
            paint.isAntiAlias = false
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    }

    private fun rounded(color: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(r).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}