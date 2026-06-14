package com.yuno.tools.ui.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class LevelToolActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var bubble: TextView
    private lateinit var pitchText: TextView
    private lateinit var rollText: TextView
    private lateinit var statusText: TextView
    private var accelerometer: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F3F6FF"))
            addView(hero("水平仪", "实时检测手机倾斜角度，接近水平时会显示绿色状态。"))
            addView(FrameLayout(context).apply {
                background = rounded("#FFFFFF", 28)
                elevation = dp(2).toFloat()
                layoutParams = LinearLayout.LayoutParams(-1, dp(360)).apply { bottomMargin = dp(14) }
                addView(TextView(context).apply { text = "+"; textSize = 96f; setTextColor(Color.parseColor("#E5E7EB")); gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
                bubble = TextView(context).apply { text = "●"; textSize = 64f; setTextColor(Color.parseColor("#2563EB")); gravity = Gravity.CENTER }
                addView(bubble, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER))
            })
            addView(card().apply {
                pitchText = line("俯仰：--°")
                rollText = line("横滚：--°")
                statusText = line(if (accelerometer == null) "设备不支持加速度传感器" else "等待传感器数据...")
                addView(pitchText); addView(rollText); addView(statusText)
            })
        })
    }

    override fun onResume() { super.onResume(); accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    override fun onPause() { sensorManager.unregisterListener(this); super.onPause() }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val roll = Math.toDegrees(kotlin.math.atan2(x.toDouble(), z.toDouble())).toFloat()
        val pitch = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat()
        pitchText.text = "俯仰：${"%.1f".format(pitch)}°"
        rollText.text = "横滚：${"%.1f".format(roll)}°"
        val ok = abs(pitch) < 3 && abs(roll) < 3
        statusText.text = if (ok) "接近水平" else "请继续调整角度"
        bubble.setTextColor(Color.parseColor(if (ok) "#10B981" else "#2563EB"))
        bubble.translationX = (-roll * dp(2)).coerceIn(-dp(120).toFloat(), dp(120).toFloat())
        bubble.translationY = (pitch * dp(2)).coerceIn(-dp(120).toFloat(), dp(120).toFloat())
    }
    private fun hero(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=gradient("#06B6D4","#3B82F6",24);setPadding(dp(18),dp(18),dp(18),dp(18));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)};addView(TextView(context).apply{text=t;textSize=25f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)});addView(TextView(context).apply{text=s;textSize=13.5f;setTextColor(Color.argb(230,255,255,255));setPadding(0,dp(8),0,0)})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded("#FFFFFF",22);elevation=dp(2).toFloat();setPadding(dp(16),dp(14),dp(16),dp(14))}
    private fun line(t:String)=TextView(this).apply{text=t;textSize=16f;setTextColor(Color.parseColor("#111827"));setPadding(0,dp(5),0,dp(5))}
    private fun rounded(c:String,r:Int)=GradientDrawable().apply{setColor(Color.parseColor(c));cornerRadius=dp(r).toFloat()}
    private fun gradient(a:String,b:String,r:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b))).apply{cornerRadius=dp(r).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
}
