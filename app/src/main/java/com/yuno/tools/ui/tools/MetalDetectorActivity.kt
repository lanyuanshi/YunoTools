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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MetalDetectorActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null
    private lateinit var valueText: TextView
    private lateinit var statusText: TextView
    private lateinit var bar: ProgressBar
    private var baseline = 50f
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); sm=getSystemService(Context.SENSOR_SERVICE) as SensorManager; sensor=sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD); setContentView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(18),dp(16),dp(24));setBackgroundColor(Color.parseColor("#F8FAFC"));addView(hero("金属探测仪","通过磁场传感器估算周围磁场强度；靠近金属/磁体时数值会上升。"));addView(card().apply{valueText=TextView(context).apply{text="-- μT";textSize=42f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.parseColor("#0F172A"))};statusText=line(if(sensor==null)"设备不支持磁场传感器" else "等待磁场数据...");bar=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply{max=200;progress=0};addView(valueText);addView(bar,LinearLayout.LayoutParams(-1,dp(18)).apply{topMargin=dp(12);bottomMargin=dp(12)});addView(statusText);addView(line("提示：手机扬声器、磁吸壳、无线充电线圈会影响结果。"))})}) }
    override fun onResume(){super.onResume();sensor?.let{sm.registerListener(this,it,SensorManager.SENSOR_DELAY_UI)}}
    override fun onPause(){sm.unregisterListener(this);super.onPause()}
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int){}
    override fun onSensorChanged(e: SensorEvent){val x=e.values[0];val y=e.values[1];val z=e.values[2];val v=sqrt(x*x+y*y+z*z);baseline=baseline*0.98f+v*0.02f;valueText.text="${"%.1f".format(v)} μT";bar.progress=v.coerceIn(0f,200f).toInt();statusText.text=when{v>120->"强磁场：附近可能有磁体/金属";v>70->"磁场偏高：靠近可疑物体";v>35->"正常环境磁场";else->"磁场较低"}}
    private fun hero(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=gradient("#111827","#64748B",24);setPadding(dp(18),dp(18),dp(18),dp(18));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)};addView(TextView(context).apply{text=t;textSize=25f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)});addView(TextView(context).apply{text=s;textSize=13.5f;setTextColor(Color.argb(230,255,255,255));setPadding(0,dp(8),0,0)})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded("#FFFFFF",22);elevation=dp(2).toFloat();setPadding(dp(16),dp(18),dp(16),dp(18))}
    private fun line(t:String)=TextView(this).apply{text=t;textSize=15f;setTextColor(Color.parseColor("#334155"));setPadding(0,dp(5),0,dp(5))}
    private fun rounded(c:String,r:Int)=GradientDrawable().apply{setColor(Color.parseColor(c));cornerRadius=dp(r).toFloat()}
    private fun gradient(a:String,b:String,r:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b))).apply{cornerRadius=dp(r).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
}