package com.yuno.tools.ui.tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class WoodenFishActivity : AppCompatActivity() {
    private lateinit var countText: TextView
    private lateinit var meritText: TextView
    private lateinit var fishView: WoodenFishView
    private lateinit var autoButton: Button
    private var soundEnabled = true
    private var count = 0
    private var auto = false
    private val handler = Handler(Looper.getMainLooper())
    private val autoTask = object : Runnable { override fun run() { if (auto) { knock(); handler.postDelayed(this, 800) } } }
    private val vibrator: Vibrator by lazy { if (Build.VERSION.SDK_INT >= 31) (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        count = getSharedPreferences("wooden_fish", MODE_PRIVATE).getInt("count", 0)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#FFF7ED"))
            addView(hero("电子木鱼", "轻点木鱼，功德 +1；支持自动敲击、重置和震动反馈。"))
            addView(FrameLayout(context).apply {
                background = rounded("#FFFFFF", 28)
                elevation = dp(2).toFloat()
                layoutParams = LinearLayout.LayoutParams(-1, dp(390)).apply { bottomMargin = dp(14) }
                fishView = WoodenFishView(context).apply { setOnClickListener { knock() } }
                addView(fishView, FrameLayout.LayoutParams(-1, -1))
            })
            addView(card().apply {
                countText = big("功德：$count")
                meritText = line(levelText())
                addView(countText); addView(meritText)
            })
            addView(row(btn("敲一下") { _ -> knock() }, btn("自动") { _ -> toggleAuto() }.also { autoButton = it }))
            addView(row(btn("清零") { _ -> reset() }, btn("音效：开") {
                soundEnabled = !soundEnabled
                (it as Button).text = if (soundEnabled) "音效：开" else "音效：关"
            }))
        })
    }

    override fun onPause() { super.onPause(); auto = false; if (::autoButton.isInitialized) autoButton.text = "自动"; handler.removeCallbacks(autoTask); save() }
    private fun knock() {
        count += 1
        updateTexts()
        fishView.hit()
        playWoodenFishSound()
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(35)
        }
        save()
    }
    private fun playWoodenFishSound() {
        if (!soundEnabled) return
        Thread {
            try {
                val sampleRate = 22050
                val durationMs = 150
                val samples = sampleRate * durationMs / 1000
                val data = ShortArray(samples)
                for (i in 0 until samples) {
                    val t = i.toDouble() / sampleRate
                    val env = kotlin.math.exp(-22.0 * t)
                    val tone = kotlin.math.sin(2.0 * Math.PI * 260.0 * t) * 0.75 + kotlin.math.sin(2.0 * Math.PI * 520.0 * t) * 0.25
                    data[i] = (tone * env * Short.MAX_VALUE * 0.55).toInt().toShort()
                }
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val fmt = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val track = if (Build.VERSION.SDK_INT >= 23) {
                    AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(fmt)
                        .setBufferSizeInBytes(data.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, data.size * 2, AudioTrack.MODE_STATIC)
                }
                track.write(data, 0, data.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 40)
                track.release()
            } catch (_: Exception) {}
        }.start()
    }

    private fun toggleAuto() {
        auto = !auto
        autoButton.text = if (auto) "停止自动" else "自动"
        if (auto) { toast("自动敲击已开启"); handler.post(autoTask) } else { toast("自动敲击已停止"); handler.removeCallbacks(autoTask) }
    }
    private fun reset() { count = 0; updateTexts(); save(); toast("功德已清零") }
    private fun updateTexts() { countText.text = "功德：$count"; meritText.text = levelText() }
    private fun levelText() = when { count >= 10000 -> "境界：功德圆满"; count >= 1000 -> "境界：心如止水"; count >= 100 -> "境界：略有小成"; count >= 10 -> "境界：静心入门"; else -> "境界：刚开始修行" }
    private fun save() = getSharedPreferences("wooden_fish", MODE_PRIVATE).edit().putInt("count", count).apply()

    private class WoodenFishView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun hit() {
            animate().scaleX(0.93f).scaleY(0.93f).setDuration(65).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f + dp(10)
            val r = minOf(width, height) * 0.30f

            // soft halo
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFEDD5")
            canvas.drawCircle(cx, cy, r + dp(52), paint)
            paint.color = Color.parseColor("#FED7AA")
            canvas.drawCircle(cx, cy, r + dp(28), paint)

            // wooden fish body - rounded wooden oval
            paint.color = Color.parseColor("#8B4513")
            canvas.drawOval(cx - r * 1.35f, cy - r * .82f, cx + r * 1.35f, cy + r * .82f, paint)
            paint.color = Color.parseColor("#A16207")
            canvas.drawOval(cx - r * 1.18f, cy - r * .68f, cx + r * 1.18f, cy + r * .66f, paint)
            paint.color = Color.parseColor("#78350F")
            canvas.drawOval(cx - r * .90f, cy - r * .46f, cx + r * .90f, cy + r * .48f, paint)

            // mouth / resonance slot
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(13).toFloat()
            paint.color = Color.parseColor("#FBBF24")
            canvas.drawArc(cx - r * .72f, cy - r * .42f, cx + r * .72f, cy + r * .48f, 198f, 144f, false, paint)
            paint.strokeWidth = dp(5).toFloat()
            paint.color = Color.parseColor("#451A03")
            canvas.drawArc(cx - r * .56f, cy - r * .28f, cx + r * .56f, cy + r * .34f, 204f, 132f, false, paint)

            // eyes
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#1C1917")
            canvas.drawCircle(cx - r * .42f, cy - r * .18f, dp(9).toFloat(), paint)
            canvas.drawCircle(cx + r * .42f, cy - r * .18f, dp(9).toFloat(), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx - r * .45f, cy - r * .21f, dp(3).toFloat(), paint)
            canvas.drawCircle(cx + r * .39f, cy - r * .21f, dp(3).toFloat(), paint)

            // top wood grain
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3).toFloat()
            paint.color = Color.parseColor("#D97706")
            canvas.drawArc(cx - r * .98f, cy - r * .62f, cx + r * .98f, cy + r * .34f, 215f, 110f, false, paint)
            canvas.drawArc(cx - r * .55f, cy - r * .55f, cx + r * .55f, cy + r * .28f, 215f, 110f, false, paint)

            // mallet
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(10).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.parseColor("#92400E")
            canvas.drawLine(cx + r * .72f, cy - r * 1.25f, cx + r * 1.10f, cy - r * .62f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#B45309")
            canvas.drawRoundRect(cx + r * .42f, cy - r * 1.45f, cx + r * .98f, cy - r * 1.18f, dp(12).toFloat(), dp(12).toFloat(), paint)

            // text
            paint.color = Color.parseColor("#EA580C")
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = dp(25).toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("咚～", cx, cy + r + dp(76), paint)
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    }

    private fun hero(t: String, s: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = gradient("#F59E0B", "#EA580C", 24); setPadding(dp(18), dp(18), dp(18), dp(18)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }; addView(TextView(context).apply { text = t; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }); addView(TextView(context).apply { text = s; textSize = 13.5f; setTextColor(Color.argb(230, 255, 255, 255)); setPadding(0, dp(8), 0, 0) }) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded("#FFFFFF", 22); elevation = dp(2).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) } }
    private fun row(vararg bs: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }; bs.forEachIndexed { i, b -> addView(b, LinearLayout.LayoutParams(0, dp(50), 1f).apply { if (i != bs.lastIndex) rightMargin = dp(10) }) } }
    private fun btn(t: String, a: (View) -> Unit) = Button(this).apply { text = t; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = rounded("#EA580C", 18); stateListAnimator = null; setOnClickListener { a(it) } }
    private fun big(t: String) = TextView(this).apply { text = t; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#7C2D12")); setPadding(0, dp(5), 0, dp(5)) }
    private fun line(t: String) = TextView(this).apply { text = t; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#9A3412")); setPadding(0, dp(5), 0, dp(5)) }
    private fun rounded(c: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(c)); cornerRadius = dp(r).toFloat() }
    private fun gradient(a: String, b: String, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))).apply { cornerRadius = dp(r).toFloat() }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}