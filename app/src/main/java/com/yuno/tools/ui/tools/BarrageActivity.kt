package com.yuno.tools.ui.tools

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuno.tools.R

class BarrageActivity : AppCompatActivity() {
    private var selectedColor = Color.YELLOW
    private var laneIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_barrage)
        enterFullScreen()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        bindColorButtons()
        findViewById<Button>(R.id.btnSendBarrage).setOnClickListener { sendBarrage() }
    }

    override fun onResume() {
        super.onResume()
        enterFullScreen()
    }

    private fun bindColorButtons() {
        val colors = listOf(
            R.id.btnColorRed to Color.RED,
            R.id.btnColorYellow to Color.YELLOW,
            R.id.btnColorGreen to Color.GREEN,
            R.id.btnColorBlue to Color.CYAN,
            R.id.btnColorPurple to Color.MAGENTA,
            R.id.btnColorWhite to Color.WHITE
        )
        colors.forEach { (id, color) ->
            findViewById<Button>(id).apply {
                backgroundTintList = ColorStateList.valueOf(color)
                setOnClickListener {
                    selectedColor = color
                    Toast.makeText(this@BarrageActivity, "已切换颜色", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendBarrage() {
        val input = findViewById<EditText>(R.id.etBarrageText)
        val text = input.text.toString().trim().ifBlank { "尤诺工具箱" }
        val container = findViewById<FrameLayout>(R.id.barrageContainer)
        val speed = findViewById<SeekBar>(R.id.seekBarrageSpeed).progress.coerceAtLeast(1)
        val tv = TextView(this).apply {
            this.text = text
            textSize = (resources.displayMetrics.heightPixels / resources.displayMetrics.scaledDensity / 8f).coerceIn(42f, 96f)
            setTextColor(selectedColor)
            setShadowLayer(12f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val laneCount = 4
        val laneHeight = (resources.displayMetrics.heightPixels / laneCount).coerceAtLeast(80)
        val top = (laneIndex++ % laneCount) * laneHeight + laneHeight / 4
        container.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = 0
            topMargin = top
        })
        tv.post {
            val startX = container.width.toFloat()
            val endX = -tv.width.toFloat() - 80f
            tv.translationX = startX
            val duration = (11000L - speed * 900L).coerceAtLeast(1800L)
            ObjectAnimator.ofFloat(tv, "translationX", startX, endX).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        container.removeView(tv)
                    }
                })
                start()
            }
        }
    }

    private fun enterFullScreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
