package com.yuno.tools.ui.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class CompassToolActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private lateinit var compassView: CompassView
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView
    private lateinit var accuracyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            addView(hero("指南针", "罗盘式方位显示，支持实时方位角、方向和校准提示。"))
            compassView = CompassView(context).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(430)).apply { bottomMargin = dp(14) } }
            addView(compassView)
            addView(card().apply {
                degreeText = line("方位：--°")
                directionText = line(if (rotationSensor == null) "方向：设备不支持旋转传感器" else "方向：--")
                accuracyText = hint("提示：如果指向不准，请拿手机画 8 字校准。")
                addView(degreeText); addView(directionText); addView(accuracyText)
            })
        })
    }

    override fun onResume() { super.onResume(); rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    override fun onPause() { sensorManager.unregisterListener(this); super.onPause() }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        accuracyText.text = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "精度：高"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "精度：中，可画 8 字校准"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "精度：低，请远离磁铁并画 8 字校准"
            else -> "精度：未知"
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(matrix, orientation)
        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f
        compassView.setAzimuth(azimuth)
        degreeText.text = "方位：${"%.0f".format(azimuth)}°"
        directionText.text = "方向：${directionOf(azimuth)}"
    }
    private fun directionOf(a: Float) = when { a < 22.5 || a >= 337.5 -> "北 N"; a < 67.5 -> "东北 NE"; a < 112.5 -> "东 E"; a < 157.5 -> "东南 SE"; a < 202.5 -> "南 S"; a < 247.5 -> "西南 SW"; a < 292.5 -> "西 W"; else -> "西北 NW" }

    private class CompassView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textBounds = Rect()
        private var azimuth = 0f
        fun setAzimuth(value: Float) { azimuth = value; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) * 0.38f
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(cx, cy, r + dp(22), paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat(); paint.color = Color.parseColor("#E2E8F0"); canvas.drawCircle(cx, cy, r + dp(22), paint)
            canvas.save(); canvas.rotate(-azimuth, cx, cy)
            for (i in 0 until 360 step 5) {
                val rad = Math.toRadians(i.toDouble() - 90); val major = i % 30 == 0; val len = if (major) dp(18) else dp(9)
                paint.strokeWidth = if (major) dp(2).toFloat() else dp(1).toFloat(); paint.color = if (major) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
                canvas.drawLine(cx + cos(rad).toFloat() * (r - len), cy + sin(rad).toFloat() * (r - len), cx + cos(rad).toFloat() * r, cy + sin(rad).toFloat() * r, paint)
            }
            listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W").forEach { (deg, label) ->
                val rad = Math.toRadians(deg.toDouble() - 90); val tx = cx + cos(rad).toFloat() * (r - dp(45)); val ty = cy + sin(rad).toFloat() * (r - dp(45))
                paint.style = Paint.Style.FILL; paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = dp(30).toFloat(); paint.color = if (label == "N") Color.parseColor("#EF4444") else Color.parseColor("#0F172A")
                paint.getTextBounds(label, 0, label.length, textBounds); canvas.drawText(label, tx - textBounds.width() / 2f, ty + textBounds.height() / 2f, paint)
            }
            canvas.restore()
            paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#EF4444")
            canvas.drawPath(Path().apply { moveTo(cx, cy - r + dp(35)); lineTo(cx - dp(13), cy); lineTo(cx, cy + dp(18)); lineTo(cx + dp(13), cy); close() }, paint)
            paint.color = Color.parseColor("#2563EB"); canvas.drawCircle(cx, cy, dp(10).toFloat(), paint)
            val text = "${"%.0f".format(azimuth)}°"; paint.color = Color.parseColor("#0F172A"); paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = dp(32).toFloat(); paint.getTextBounds(text, 0, text.length, textBounds); canvas.drawText(text, cx - textBounds.width() / 2f, cy + r + dp(54), paint)
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    }

    private fun hero(t: String, s: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = gradient("#F97316", "#EF4444", 24); setPadding(dp(18), dp(18), dp(18), dp(18)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }; addView(TextView(context).apply { text = t; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }); addView(TextView(context).apply { text = s; textSize = 13.5f; setTextColor(Color.argb(230, 255, 255, 255)); setPadding(0, dp(8), 0, 0) }) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded("#FFFFFF", 22); elevation = dp(2).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14)) }
    private fun line(t: String) = TextView(this).apply { text = t; textSize = 18f; setTextColor(Color.parseColor("#111827")); setPadding(0, dp(5), 0, dp(5)) }
    private fun hint(t: String) = TextView(this).apply { text = t; textSize = 14f; setTextColor(Color.parseColor("#64748B")); setPadding(0, dp(5), 0, dp(5)) }
    private fun rounded(c: String, r: Int) = GradientDrawable().apply { setColor(Color.parseColor(c)); cornerRadius = dp(r).toFloat() }
    private fun gradient(a: String, b: String, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))).apply { cornerRadius = dp(r).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}