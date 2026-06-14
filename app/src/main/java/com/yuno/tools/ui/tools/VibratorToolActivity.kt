package com.yuno.tools.ui.tools

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class VibratorToolActivity : AppCompatActivity() {
    private lateinit var durationText: TextView
    private var durationMs = 500
    private val vibrator: Vibrator by lazy { if (Build.VERSION.SDK_INT >= 31) (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(18),dp(16),dp(24));setBackgroundColor(Color.parseColor("#F3F4F6"));addView(hero("震动器","支持短震、长震、节奏震动和自定义时长。"));addView(card().apply{durationText=line("自定义时长：500 ms");addView(durationText);addView(SeekBar(context).apply{max=3000;progress=500;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(sb:SeekBar?,p:Int,from:Boolean){durationMs=p.coerceAtLeast(50);durationText.text="自定义时长：$durationMs ms"};override fun onStartTrackingTouch(sb:SeekBar?){};override fun onStopTrackingTouch(sb:SeekBar?){}})})});addView(row(btn("短震") { vibrate(120) }, btn("长震") { vibrate(900) }));addView(row(btn("节奏") { pattern() }, btn("自定义") { vibrate(durationMs.toLong()) }));addView(row(btn("停止") { cancel() }))}) }
    private fun vibrate(ms:Long){ if(!vibrator.hasVibrator()){toast("设备不支持震动");return}; if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(ms) }
    private fun pattern(){ if(!vibrator.hasVibrator()){toast("设备不支持震动");return}; val p=longArrayOf(0,120,90,180,120,260); if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createWaveform(p,-1)) else @Suppress("DEPRECATION") vibrator.vibrate(p,-1) }
    private fun cancel(){vibrator.cancel();toast("已停止")}
    private fun hero(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=gradient("#A855F7","#EC4899",24);setPadding(dp(18),dp(18),dp(18),dp(18));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)};addView(TextView(context).apply{text=t;textSize=25f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)});addView(TextView(context).apply{text=s;textSize=13.5f;setTextColor(Color.argb(230,255,255,255));setPadding(0,dp(8),0,0)})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded("#FFFFFF",22);elevation=dp(2).toFloat();setPadding(dp(16),dp(14),dp(16),dp(14));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)}}
    private fun row(vararg bs:Button)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)};bs.forEachIndexed{i,b->addView(b,LinearLayout.LayoutParams(0,dp(50),1f).apply{if(i!=bs.lastIndex)rightMargin=dp(10)})}}
    private fun btn(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=rounded("#7C3AED",18);stateListAnimator=null;setOnClickListener{a()}}
    private fun line(t:String)=TextView(this).apply{text=t;textSize=17f;setTextColor(Color.parseColor("#111827"));setPadding(0,dp(5),0,dp(10))}
    private fun rounded(c:String,r:Int)=GradientDrawable().apply{setColor(Color.parseColor(c));cornerRadius=dp(r).toFloat()}
    private fun gradient(a:String,b:String,r:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b))).apply{cornerRadius=dp(r).toFloat()}
    private fun toast(m:String)=Toast.makeText(this,m,Toast.LENGTH_SHORT).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
}