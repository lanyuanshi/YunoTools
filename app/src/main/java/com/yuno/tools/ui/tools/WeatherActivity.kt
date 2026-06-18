package com.yuno.tools.ui.tools

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class WeatherActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var particleView: WeatherParticleView
    private lateinit var sceneOverlay: WeatherSceneOverlay
    private lateinit var cityInput: EditText
    private lateinit var statusText: TextView
    private lateinit var cityText: TextView
    private lateinit var tempText: TextView
    private lateinit var weatherText: TextView
    private lateinit var detailGrid: LinearLayout
    private lateinit var livingList: LinearLayout
    private lateinit var refreshButton: TextView
    private lateinit var warningText: TextView
    private var currentCity = "佛山"
    private var currentWeather = "晴"
    private var refreshAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullscreenRendering(lightStatusBar = true)
        buildUi()
        fetchWeather(currentCity)
    }

    override fun onResume() {
        super.onResume()
        particleView.start()
        sceneOverlay.start()
    }

    override fun onPause() {
        particleView.stop()
        sceneOverlay.stop()
        super.onPause()
    }

    override fun onDestroy() {
        refreshAnimator?.cancel()
        super.onDestroy()
    }

    private fun enableFullscreenRendering(lightStatusBar: Boolean) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (lightStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (lightStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#DDF4FF"), Color.parseColor("#EEF2FF"), Color.parseColor("#F8FBFF")))
            clipChildren = false
            clipToPadding = false
        }
        sceneOverlay = WeatherSceneOverlay(this)
        particleView = WeatherParticleView(this)
        root.addView(sceneOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(particleView, FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), statusBarHeight() + dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            background = rounded(Color.argb(220, 255, 255, 255), 18, Color.argb(120, 255, 255, 255), 1)
            elevation = dp(4).toFloat()
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(context).apply { text = "动态天气"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#0F172A")) })
            addView(TextView(context).apply { text = "实时天气 · 光影粒子 · 生活指数"; textSize = 13f; setTextColor(Color.parseColor("#475569")) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        val searchCard = glassCard()
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        cityInput = EditText(this).apply {
            hint = "输入城市，例如：佛山"
            setText(currentCity)
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = rounded(Color.argb(180, 255, 255, 255), 16, Color.parseColor("#D7E3F0"), 1)
            setPadding(dp(14), 0, dp(14), 0)
            setOnEditorActionListener { _, _, _ -> searchCity(); true }
        }
        searchRow.addView(cityInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        refreshButton = actionButton("刷新") { searchCity() }
        searchRow.addView(refreshButton, LinearLayout.LayoutParams(dp(76), dp(48)).apply { leftMargin = dp(10) })
        searchCard.addView(searchRow)
        content.addView(searchCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        content.addView(quickCityRow(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#2F80ED"), Color.parseColor("#56CCF2"), Color.parseColor("#A78BFA"))).apply { cornerRadius = dp(30).toFloat() }
            elevation = dp(8).toFloat()
            setPadding(dp(18), dp(18), dp(18), dp(18))
            isClickable = true
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(AccelerateDecelerateInterpolator()).start()
                }
                false
            }
            setOnClickListener { fetchWeather(currentCity) }
        }
        val heroBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cityText = TextView(this).apply { text = "--"; textSize = 18f; setTextColor(Color.argb(235, 255, 255, 255)); typeface = Typeface.DEFAULT_BOLD }
        tempText = TextView(this).apply { text = "--°"; textSize = 62f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); includeFontPadding = false }
        weatherText = TextView(this).apply { text = "正在获取天气…"; textSize = 17f; setTextColor(Color.argb(235, 255, 255, 255)); setPadding(0, dp(5), 0, 0) }
        warningText = TextView(this).apply { text = "点击卡片可刷新 · 粒子会随天气变化"; textSize = 13f; setTextColor(Color.argb(220, 255, 255, 255)); setPadding(0, dp(12), 0, 0) }
        heroBox.addView(cityText); heroBox.addView(tempText); heroBox.addView(weatherText); heroBox.addView(warningText)
        hero.addView(heroBox, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START))
        hero.addView(TextView(this).apply { text = "☁"; textSize = 82f; setTextColor(Color.argb(120, 255, 255, 255)); gravity = Gravity.CENTER }, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.TOP or Gravity.END))
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(250)).apply { topMargin = dp(16) })

        val detailCard = glassCard()
        val detailBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        detailBox.addView(sectionTitle("实时详情"))
        detailGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detailBox.addView(detailGrid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        detailCard.addView(detailBox)
        content.addView(detailCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val livingCard = glassCard()
        val livingBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        livingBox.addView(sectionTitle("生活指数"))
        livingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        livingBox.addView(livingList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        livingCard.addView(livingBox)
        content.addView(livingCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        statusText = TextView(this).apply {
            text = "准备就绪"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun quickCityRow(): HorizontalScrollView {
        val cities = listOf("佛山", "广州", "深圳", "北京", "上海", "杭州", "成都", "重庆")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cities.forEach { city ->
            row.addView(TextView(this).apply {
                text = city
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#2563EB"))
                background = rounded(Color.argb(205, 255, 255, 255), 18, Color.argb(130, 96, 165, 250), 1)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener {
                    cityInput.setText(city)
                    cityInput.setSelection(city.length)
                    fetchWeather(city)
                }
            }, LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(8) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun searchCity() {
        val city = cityInput.text.toString().trim()
        if (city.isEmpty()) {
            Toast.makeText(this, "请输入城市名称", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        fetchWeather(city)
    }

    private fun fetchWeather(city: String) {
        setLoading(true, "正在获取 ${city} 天气…")
        thread {
            try {
                val encoded = URLEncoder.encode(city, "UTF-8")
                val conn = (URL("http://api.xcvts.cn/api/weather?city=$encoded").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "YunoTools/1.1.96")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                if (json.optInt("code") != 1) throw IllegalStateException(json.optString("text", "获取失败"))
                val data = json.getJSONObject("data")
                mainHandler.post { bindWeather(data); setLoading(false, "更新成功 · ${data.optString("time", "")} ${data.optJSONObject("current")?.optString("time", "") ?: ""}") }
            } catch (e: Exception) {
                mainHandler.post {
                    setLoading(false, "获取失败：${e.message ?: "网络异常"}")
                    Toast.makeText(this, "天气获取失败，请检查城市或稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindWeather(data: JSONObject) {
        currentCity = data.optString("city", cityInput.text.toString())
        cityInput.setText(currentCity)
        cityInput.setSelection(currentCity.length)
        val current = data.optJSONObject("current") ?: JSONObject()
        currentWeather = current.optString("weather", data.optString("weather", "晴"))
        val temp = current.optString("temp", data.optString("temp", "--"))
        cityText.text = "${data.optString("city", currentCity)} · ${data.optString("cityEnglish", "")}".trim().trimEnd('·').trim()
        tempText.text = "$temp°"
        weatherText.text = "${currentWeather}  ${data.optString("wind", current.optString("wind", ""))} ${data.optString("windSpeed", current.optString("windSpeed", ""))}"
        val warning = data.optString("warning", "")
        warningText.text = if (warning.isBlank() || warning == "null") "体感 ${data.optString("tempn", "--")}° · 点击卡片刷新动态天气" else "预警：$warning"
        updateScene(currentWeather)
        detailGrid.removeAllViews()
        val details = listOf(
            "湿度" to current.optString("humidity", "--"),
            "风向" to current.optString("wind", data.optString("wind", "--")),
            "风速" to current.optString("windSpeed", data.optString("windSpeed", "--")),
            "能见度" to current.optString("visibility", "--"),
            "空气质量" to current.optString("air", "--"),
            "PM2.5" to current.optString("air_pm25", "--"),
            "最高/最低" to "${data.optString("temp", "--")}° / ${data.optString("tempn", "--")}°",
            "更新时间" to "${current.optString("date", "")} ${current.optString("time", data.optString("time", ""))}".trim()
        )
        details.chunked(2).forEach { pair -> detailGrid.addView(detailRow(pair)) }
        livingList.removeAllViews()
        val living = data.optJSONArray("living")
        if (living != null && living.length() > 0) {
            for (i in 0 until living.length()) {
                val item = living.optJSONObject(i) ?: continue
                livingList.addView(livingItem(item.optString("name", "指数"), item.optString("index", "--"), item.optString("tips", "")))
            }
        } else {
            livingList.addView(livingItem("生活提示", "暂无", "接口暂未返回生活指数。"))
        }
    }

    private fun updateScene(weather: String) {
        val rainy = weather.contains("雨") || weather.contains("雷")
        val snowy = weather.contains("雪")
        val foggy = weather.contains("雾") || weather.contains("霾") || weather.contains("阴")
        val colorSet = when {
            rainy -> intArrayOf(Color.parseColor("#1E3A8A"), Color.parseColor("#2563EB"), Color.parseColor("#93C5FD"))
            snowy -> intArrayOf(Color.parseColor("#E0F2FE"), Color.parseColor("#BAE6FD"), Color.parseColor("#F8FAFC"))
            foggy -> intArrayOf(Color.parseColor("#94A3B8"), Color.parseColor("#CBD5E1"), Color.parseColor("#F8FAFC"))
            else -> intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#818CF8"), Color.parseColor("#FDE68A"))
        }
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colorSet)
        val mode = when { rainy -> WeatherParticleView.Mode.RAIN; snowy -> WeatherParticleView.Mode.SNOW; foggy -> WeatherParticleView.Mode.FOG; else -> WeatherParticleView.Mode.SUN }
        particleView.setMode(mode)
        sceneOverlay.setMode(mode)
    }

    private fun setLoading(loading: Boolean, text: String) {
        statusText.text = text
        refreshButton.isEnabled = !loading
        refreshButton.alpha = if (loading) 0.75f else 1f
        if (loading) {
            refreshAnimator?.cancel()
            refreshAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 850
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { refreshButton.rotation = it.animatedValue as Float }
                start()
            }
        } else {
            refreshAnimator?.cancel()
            refreshButton.animate().rotation(0f).setDuration(160).start()
        }
    }

    private fun detailRow(items: List<Pair<String, String>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEachIndexed { index, item ->
            addView(infoTile(item.first, item.second), LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) rightMargin = dp(8) else leftMargin = dp(8); bottomMargin = dp(10) })
        }
        if (items.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
    }

    private fun infoTile(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.argb(135, 255, 255, 255), 18, Color.argb(80, 148, 163, 184), 1)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(TextView(context).apply { text = label; textSize = 12f; setTextColor(Color.parseColor("#64748B")) })
        addView(TextView(context).apply { text = value.ifBlank { "--" }; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#0F172A")); setPadding(0, dp(4), 0, 0) })
    }

    private fun livingItem(name: String, index: String, tips: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.argb(120, 255, 255, 255), 18, Color.argb(70, 148, 163, 184), 1)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(TextView(context).apply { text = "$name · $index"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#0F172A")) })
        addView(TextView(context).apply { text = tips.ifBlank { "暂无建议" }; textSize = 13f; setTextColor(Color.parseColor("#475569")); setPadding(0, dp(5), 0, 0); setLineSpacing(dp(2).toFloat(), 1f) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun sectionTitle(textValue: String) = TextView(this).apply { text = textValue; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#0F172A")) }

    private fun actionButton(textValue: String, action: () -> Unit) = TextView(this).apply {
        text = textValue
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = rounded(Color.parseColor("#2563EB"), 16, Color.TRANSPARENT, 0)
        elevation = dp(3).toFloat()
        setOnClickListener { action() }
    }

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.argb(170, 255, 255, 255), 24, Color.argb(120, 255, 255, 255), 1)
        elevation = dp(3).toFloat()
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(cityInput.windowToken, 0)
        cityInput.clearFocus()
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private class WeatherSceneOverlay(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shift = 0f
        private var mode = WeatherParticleView.Mode.SUN
        private val ticker = object : Runnable { override fun run() { shift += 0.012f; invalidate(); postDelayed(this, 16) } }
        fun setMode(value: WeatherParticleView.Mode) { mode = value; invalidate() }
        fun start() { removeCallbacks(ticker); post(ticker) }
        fun stop() { removeCallbacks(ticker) }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (mode == WeatherParticleView.Mode.SUN) {
                val sx = w * (0.18f + 0.035f * sin(shift * 1.2f))
                val sy = h * 0.16f
                paint.shader = RadialGradient(sx, sy, w * 0.42f, intArrayOf(Color.argb(245, 255, 236, 150), Color.argb(145, 255, 196, 87), Color.TRANSPARENT), floatArrayOf(0f, 0.34f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(sx, sy, w * 0.42f, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = w * 0.012f
                paint.color = Color.argb(105, 255, 244, 180)
                for (i in 0 until 14) {
                    val a = (i / 14f) * Math.PI * 2 + shift
                    val x1 = sx + cos(a).toFloat() * w * 0.13f
                    val y1 = sy + sin(a).toFloat() * w * 0.13f
                    val x2 = sx + cos(a).toFloat() * w * (0.25f + 0.025f * sin(shift * 2f + i))
                    val y2 = sy + sin(a).toFloat() * w * (0.25f + 0.025f * sin(shift * 2f + i))
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
                paint.style = Paint.Style.FILL
            } else {
                paint.shader = RadialGradient(w * (0.22f + 0.08f * sin(shift)), h * 0.18f, w * 0.55f, Color.argb(if (mode == WeatherParticleView.Mode.RAIN) 115 else 150, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawCircle(w * (0.22f + 0.08f * sin(shift)), h * 0.18f, w * 0.55f, paint)
            }
            paint.shader = RadialGradient(w * 0.82f, h * (0.22f + 0.05f * cos(shift * 1.4f)), w * 0.42f, Color.argb(if (mode == WeatherParticleView.Mode.RAIN) 135 else 105, 125, 211, 252), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(w * 0.82f, h * (0.22f + 0.05f * cos(shift * 1.4f)), w * 0.42f, paint)
            paint.shader = null
        }
    }

    private class WeatherParticleView(context: Context) : View(context) {
        enum class Mode { SUN, RAIN, SNOW, FOG }
        private data class Particle(var x: Float, var y: Float, var speed: Float, var size: Float, var alpha: Int, var drift: Float)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val particles = MutableList(168) { newParticle(true) }
        private var mode = Mode.SUN
        private val ticker = object : Runnable { override fun run() { step(); invalidate(); postDelayed(this, 16) } }
        fun setMode(value: Mode) { mode = value; particles.indices.forEach { particles[it] = newParticle(true) }; invalidate() }
        fun start() { removeCallbacks(ticker); post(ticker) }
        fun stop() { removeCallbacks(ticker) }
        override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            when (mode) {
                Mode.RAIN -> drawRain(canvas)
                Mode.SNOW -> drawSnow(canvas)
                Mode.FOG -> drawFog(canvas)
                Mode.SUN -> drawSun(canvas)
            }
        }
        private fun step() {
            particles.forEachIndexed { index, p ->
                when (mode) {
                    Mode.RAIN -> { p.y += p.speed * 4.2f; p.x += p.drift * 0.9f }
                    Mode.SNOW -> { p.y += p.speed * 0.62f; p.x += sin((p.y + index * 11f) * 0.018f) * p.drift }
                    Mode.FOG -> { p.x += p.speed * 0.18f; p.y += sin((p.x + index) * 0.01f) * 0.18f }
                    Mode.SUN -> { p.y -= p.speed * 0.18f; p.x += sin((p.y + index) * 0.018f) * 0.35f }
                }
                if (p.y > height + 80 || p.y < -90 || p.x > width + 120 || p.x < -120) particles[index] = newParticle(false)
            }
        }
        private fun drawRain(canvas: Canvas) {
            paint.shader = LinearGradient(0f, 0f, 0f, dp(70).toFloat(), Color.argb(85, 224, 242, 254), Color.argb(235, 96, 165, 250), Shader.TileMode.CLAMP)
            paint.strokeWidth = dp(2.2f)
            paint.strokeCap = Paint.Cap.ROUND
            particles.forEach { canvas.drawLine(it.x, it.y, it.x - it.drift * 5.5f, it.y + it.size * 12.5f, paint) }
            paint.shader = null
            paint.color = Color.argb(70, 219, 234, 254)
            paint.strokeWidth = dp(0.8f)
            for (i in 0 until 18) {
                val y = (i * height / 18f + (particles.getOrNull(i)?.y ?: 0f) * 0.08f) % (height + 1f)
                canvas.drawLine(0f, y, width.toFloat(), y + dp(8), paint)
            }
        }
        private fun drawSnow(canvas: Canvas) {
            paint.color = Color.argb(190, 255, 255, 255)
            particles.forEach { canvas.drawCircle(it.x, it.y, it.size, paint) }
        }
        private fun drawFog(canvas: Canvas) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(14).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb(42, 255, 255, 255)
            particles.take(30).forEach { canvas.drawLine(it.x - it.size * 8, it.y, it.x + it.size * 10, it.y + sin(it.x * 0.02f) * 10f, paint) }
            paint.style = Paint.Style.FILL
        }
        private fun drawSun(canvas: Canvas) {
            paint.maskFilter = BlurMaskFilter(dp(10).toFloat(), BlurMaskFilter.Blur.NORMAL)
            particles.forEach {
                paint.color = Color.argb((it.alpha + 55).coerceAtMost(240), 255, 236, 125)
                canvas.drawCircle(it.x, it.y, it.size * 2.7f, paint)
            }
            paint.maskFilter = null
            paint.color = Color.argb(95, 255, 214, 102)
            particles.take(36).forEach { canvas.drawCircle(it.x, it.y, it.size * 0.8f, paint) }
        }
        private fun newParticle(anywhere: Boolean): Particle {
            val w = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val h = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val y = if (anywhere) Random.nextFloat() * h else when (mode) { Mode.SUN -> h + 80f; else -> -80f }
            return Particle(Random.nextFloat() * w, y, Random.nextFloat() * 4.2f + 1.6f, Random.nextFloat() * dp(4).coerceAtLeast(1f) + dp(1.5f), Random.nextInt(85, 210), Random.nextFloat() * 5.5f - 2.75f)
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt().toFloat()
        private fun dp(v: Float) = v * resources.displayMetrics.density
    }
}
