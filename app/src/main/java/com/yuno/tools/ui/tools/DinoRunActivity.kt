package com.yuno.tools.ui.tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    private lateinit var gameView: DinoGameView
    private lateinit var scoreText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(20))
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            addView(TextView(context).apply {
                text = "恐龙快跑"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            addView(TextView(context).apply {
                text = "点击屏幕跳跃，躲避仙人掌。速度会越来越快。"
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(6), 0, dp(12))
            })
            scoreText = TextView(context).apply {
                text = "分数：0    最高：0"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#334155"))
                background = rounded("#EFFFFFFF", 18)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            addView(scoreText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            gameView = DinoGameView(context) { score, best -> scoreText.text = "分数：$score    最高：$best" }
            addView(gameView, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(12) })
            addView(Button(context).apply {
                text = "重新开始"
                textSize = 15f
                setTextColor(Color.WHITE)
                background = rounded("#2563EB", 18)
                stateListAnimator = null
                setOnClickListener { gameView.restart() }
            }, LinearLayout.LayoutParams(-1, dp(50)))
        })
    }

    override fun onResume() { super.onResume(); if (::gameView.isInitialized) gameView.resumeGame() }
    override fun onPause() { if (::gameView.isInitialized) gameView.pauseGame(); super.onPause() }

    class DinoGameView(context: android.content.Context, private val scoreCallback: (Int, Int) -> Unit) : View(context), Runnable {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var running = false
        private var gameOver = false
        private var thread: Thread? = null
        private var dinoY = 0f
        private var velocity = 0f
        private var groundY = 0f
        private var cactusX = 0f
        private var cactusW = 34f
        private var cactusH = 70f
        private var speed = 10f
        private var score = 0
        private var best = context.getSharedPreferences("dino_run", MODE_PRIVATE).getInt("best", 0)
        private var frame = 0

        init { setBackgroundColor(Color.parseColor("#EFFFFFFF")); restart() }

        fun restart() {
            gameOver = false
            score = 0
            speed = 10f
            velocity = 0f
            dinoY = 0f
            cactusX = width.takeIf { it > 0 }?.toFloat() ?: 900f
            scoreCallback(score, best)
            invalidate()
        }

        fun resumeGame() {
            if (!running) {
                running = true
                thread = Thread(this).also { it.start() }
            }
        }
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
            if (dinoY >= groundY - dp(54) - 2) velocity = -22f
        }

        private fun update() {
            if (width <= 0 || height <= 0 || gameOver) return
            groundY = height * 0.72f
            if (dinoY == 0f) dinoY = groundY - dp(54)
            velocity += 1.05f
            dinoY += velocity
            val floor = groundY - dp(54)
            if (dinoY > floor) { dinoY = floor; velocity = 0f }
            cactusX -= speed
            if (cactusX < -cactusW) {
                cactusX = width.toFloat() + Random.nextInt(80, 260)
                cactusH = dp(Random.nextInt(48, 86)).toFloat()
                cactusW = dp(Random.nextInt(26, 42)).toFloat()
                score += 1
                if (score > best) {
                    best = score
                    context.getSharedPreferences("dino_run", MODE_PRIVATE).edit().putInt("best", best).apply()
                }
                speed = (speed + 0.35f).coerceAtMost(22f)
                scoreCallback(score, best)
            }
            val dinoLeft = dp(64).toFloat()
            val dinoRight = dinoLeft + dp(48)
            val dinoTop = dinoY
            val dinoBottom = dinoY + dp(54)
            val cLeft = cactusX
            val cRight = cactusX + cactusW
            val cTop = groundY - cactusH
            val cBottom = groundY
            if (dinoRight > cLeft + dp(6) && dinoLeft + dp(8) < cRight && dinoBottom > cTop + dp(8) && dinoTop < cBottom) {
                gameOver = true
            }
            frame++
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            groundY = height * 0.72f
            if (dinoY == 0f) dinoY = groundY - dp(54)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(24).toFloat(), dp(24).toFloat(), paint)
            paint.color = Color.parseColor("#CBD5E1")
            paint.strokeWidth = dp(3).toFloat()
            canvas.drawLine(dp(18).toFloat(), groundY, width - dp(18).toFloat(), groundY, paint)
            drawCloud(canvas, width * .72f, height * .18f)
            drawCloud(canvas, width * .34f, height * .28f)
            drawDino(canvas, dp(64).toFloat(), dinoY)
            drawCactus(canvas, cactusX, groundY - cactusH, cactusW, cactusH)
            if (gameOver) drawGameOver(canvas)
        }

        private fun drawDino(canvas: Canvas, x: Float, y: Float) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#334155")
            canvas.drawRoundRect(x, y + dp(14), x + dp(42), y + dp(54), dp(8).toFloat(), dp(8).toFloat(), paint)
            canvas.drawRoundRect(x + dp(26), y, x + dp(58), y + dp(30), dp(8).toFloat(), dp(8).toFloat(), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(x + dp(48), y + dp(10), dp(3).toFloat(), paint)
            paint.color = Color.parseColor("#334155")
            val legOffset = if ((frame / 8) % 2 == 0) 0 else dp(7)
            canvas.drawRect(x + dp(10), y + dp(52), x + dp(18), y + dp(66) + legOffset, paint)
            canvas.drawRect(x + dp(28), y + dp(52), x + dp(36), y + dp(66) - legOffset, paint)
            canvas.drawRect(x - dp(14), y + dp(30), x + dp(4), y + dp(38), paint)
        }

        private fun drawCactus(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
            paint.color = Color.parseColor("#16A34A")
            canvas.drawRoundRect(x + w * .35f, y, x + w * .65f, y + h, dp(6).toFloat(), dp(6).toFloat(), paint)
            canvas.drawRoundRect(x, y + h * .35f, x + w * .45f, y + h * .55f, dp(6).toFloat(), dp(6).toFloat(), paint)
            canvas.drawRoundRect(x + w * .55f, y + h * .2f, x + w, y + h * .4f, dp(6).toFloat(), dp(6).toFloat(), paint)
        }

        private fun drawCloud(canvas: Canvas, x: Float, y: Float) {
            paint.color = Color.parseColor("#DCE7F3")
            canvas.drawCircle(x, y, dp(14).toFloat(), paint)
            canvas.drawCircle(x + dp(16), y - dp(6), dp(18).toFloat(), paint)
            canvas.drawCircle(x + dp(36), y, dp(14).toFloat(), paint)
            canvas.drawRoundRect(x - dp(4), y, x + dp(42), y + dp(14), dp(10).toFloat(), dp(10).toFloat(), paint)
        }

        private fun drawGameOver(canvas: Canvas) {
            paint.color = Color.parseColor("#CC0F172A")
            canvas.drawRoundRect(dp(36).toFloat(), height * .32f, width - dp(36).toFloat(), height * .55f, dp(24).toFloat(), dp(24).toFloat(), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = dp(28).toFloat()
            canvas.drawText("游戏结束", width / 2f, height * .41f, paint)
            paint.textSize = dp(15).toFloat()
            canvas.drawText("点击屏幕或按钮重新开始", width / 2f, height * .48f, paint)
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    }

    private fun rounded(color: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(r).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
