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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class CompassToolActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private var rotation: Sensor? = null
    private lateinit var arrow: TextView
    private lateinit var degree: TextView
    private lateinit var direction: TextView
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); sm=getSystemService(Context.SENSOR_SERVICE) as SensorManager; rotation=sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR); setContentView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(18),dp(16),dp(24));setBackgroundColor(Color.parseColor("#F8FAFC"));addView(hero("指南针","实时显示方位角与方向，使用前可画 8 字校准传感器。"));addView(FrameLayout(context).apply{background=rounded("#FFFFFF",28);elevation=dp(2).toFloat();layoutParams=LinearLayout.LayoutParams(-1,dp(390)).apply{bottomMargin=dp(14)};addView(TextView(context).apply{text="N\n\n\nW     E\n\n\nS";textSize=28f;gravity=Gravity.CENTER;setTextColor(Color.parseColor("#CBD5E1"));typeface=Typeface.DEFAULT_BOLD},FrameLayout.LayoutParams(-1,-1));arrow=TextView(context).apply{text="↑";textSize=120f;gravity=Gravity.CENTER;setTextColor(Color.parseColor("#EF4444"))};addView(arrow,FrameLayout.LayoutParams(-1,-1))});addView(card().apply{degree=line("方位：--°");direction=line(if(rotation==null)"设备不支持指南针/旋转传感器" else "方向：--");addView(degree);addView(direction)})}) }
    override fun onResume(){super.onResume();rotation?.let{sm.registerListener(this,it,SensorManager.SENSOR_DELAY_UI)}}
    override fun onPause(){sm.unregisterListener(this);super.onPause()}
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int){}
    override fun onSensorChanged(e: SensorEvent){val m=FloatArray(9);SensorManager.getRotationMatrixFromVector(m,e.values);val o=FloatArray(3);SensorManager.getOrientation(m,o);var az=Math.toDegrees(o[0].toDouble()).toFloat();if(az<0)az+=360f;arrow.rotation=-az;degree.text="方位：${"%.0f".format(az)}°";direction.text="方向：${dir(az)}"}
    private fun dir(a:Float)=when{a<22.5||a>=337.5->"北 N";a<67.5->"东北 NE";a<112.5->"东 E";a<157.5->"东南 SE";a<202.5->"南 S";a<247.5->"西南 SW";a<292.5->"西 W";else->"西北 NW"}
    private fun hero(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=gradient("#F97316","#EF4444",24);setPadding(dp(18),dp(18),dp(18),dp(18));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)};addView(TextView(context).apply{text=t;textSize=25f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)});addView(TextView(context).apply{text=s;textSize=13.5f;setTextColor(Color.argb(230,255,255,255));setPadding(0,dp(8),0,0)})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded("#FFFFFF",22);elevation=dp(2).toFloat();setPadding(dp(16),dp(14),dp(16),dp(14))}
    private fun line(t:String)=TextView(this).apply{text=t;textSize=18f;setTextColor(Color.parseColor("#111827"));setPadding(0,dp(5),0,dp(5))}
    private fun rounded(c:String,r:Int)=GradientDrawable().apply{setColor(Color.parseColor(c));cornerRadius=dp(r).toFloat()}
    private fun gradient(a:String,b:String,r:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b))).apply{cornerRadius=dp(r).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
}