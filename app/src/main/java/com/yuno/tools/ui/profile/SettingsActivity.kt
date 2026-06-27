package com.yuno.tools.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yuno.tools.BuildConfig
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.ui.tools.AIChatActivity
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {
    private val updateUrl = "https://www.lyyp.cloud/s/ErLug"
    private val updateClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    private lateinit var switchDefaultApi: Switch
    private lateinit var etEndpoint: EditText
    private lateinit var etApiKey: EditText
    private lateinit var tvApiMode: TextView
    private lateinit var customApiPanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.cardThemeSettings).setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
            overridePendingTransition(R.anim.profile_slide_up_in, R.anim.profile_stay)
        }
        bindAiSettings()
        bindMusicBarSettings()
        bindMusicSpectrumSettings()
        bindLyricHighlightSettings()
        bindUpdateChecker()
        refreshThemeState()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this); refreshThemeState(); loadAiState(); refreshMusicBarState(); refreshMusicSpectrumState(); refreshLyricHighlightState() }

    private fun bindAiSettings() {
        switchDefaultApi = findViewById(R.id.switchDefaultApi)
        etEndpoint = findViewById(R.id.etAiEndpoint)
        etApiKey = findViewById(R.id.etAiApiKey)
        tvApiMode = findViewById(R.id.tvApiMode)
        customApiPanel = findViewById(R.id.customApiPanel)
        etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        loadAiState()
        switchDefaultApi.setOnCheckedChangeListener { _, checked -> applyAiMode(checked) }
        findViewById<MaterialButton>(R.id.btnSaveAiSettings).setOnClickListener { saveAiSettings() }
        findViewById<MaterialButton>(R.id.btnResetAiSettings).setOnClickListener {
            switchDefaultApi.isChecked = true
            etEndpoint.setText(AIChatActivity.DEFAULT_ENDPOINT)
            etApiKey.setText("")
            saveAiSettings()
        }
    }

    private fun loadAiState() {
        val sp = getSharedPreferences(AIChatActivity.PREF, Context.MODE_PRIVATE)
        val useDefault = sp.getBoolean(AIChatActivity.KEY_USE_DEFAULT, true)
        switchDefaultApi.setOnCheckedChangeListener(null)
        switchDefaultApi.isChecked = useDefault
        switchDefaultApi.setOnCheckedChangeListener { _, checked -> applyAiMode(checked) }
        if (etEndpoint.text.isEmpty()) etEndpoint.setText(sp.getString(AIChatActivity.KEY_CUSTOM_ENDPOINT, AIChatActivity.DEFAULT_ENDPOINT))
        if (etApiKey.text.isEmpty()) etApiKey.setText(sp.getString(AIChatActivity.KEY_CUSTOM_API_KEY, ""))
        applyAiMode(useDefault)
    }

    private fun applyAiMode(useDefault: Boolean) {
        customApiPanel.alpha = if (useDefault) 0.45f else 1f
        etEndpoint.isEnabled = !useDefault
        etApiKey.isEnabled = !useDefault
        tvApiMode.text = if (useDefault) "当前：使用默认 API（${AIChatActivity.DEFAULT_ENDPOINT}）" else "当前：使用自定义 API"
    }

    private fun saveAiSettings() {
        val useDefault = switchDefaultApi.isChecked
        val endpoint = etEndpoint.text.toString().trim().ifBlank { AIChatActivity.DEFAULT_ENDPOINT }
        val key = etApiKey.text.toString().trim()
        if (!useDefault && key.isBlank()) { Toast.makeText(this, "请填写自定义 API Key，或切回默认 API", Toast.LENGTH_SHORT).show(); return }
        getSharedPreferences(AIChatActivity.PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(AIChatActivity.KEY_USE_DEFAULT, useDefault)
            .putString(AIChatActivity.KEY_CUSTOM_ENDPOINT, endpoint)
            .putString(AIChatActivity.KEY_CUSTOM_API_KEY, key)
            .apply()
        Toast.makeText(this, "AI设置已保存", Toast.LENGTH_SHORT).show()
        loadAiState()
    }


    private fun bindMusicBarSettings() {
        val group = findViewById<ChipGroup>(R.id.chipMusicBarStyle)
        val styles = listOf(
            R.id.chipMusicBarMulti to UserSettingsStore.MUSIC_BAR_MULTI,
            R.id.chipMusicBarBlue to UserSettingsStore.MUSIC_BAR_BLUE,
            R.id.chipMusicBarRed to UserSettingsStore.MUSIC_BAR_RED,
            R.id.chipMusicBarGreen to UserSettingsStore.MUSIC_BAR_GREEN,
            R.id.chipMusicBarPurple to UserSettingsStore.MUSIC_BAR_PURPLE
        )
        styles.forEach { (id, style) ->
            findViewById<Chip>(id).setOnClickListener {
                UserSettingsStore.setMusicBarStyle(this, style)
                refreshMusicBarState()
                Toast.makeText(this, "音条特效已切换：${musicBarStyleName(style)}", Toast.LENGTH_SHORT).show()
            }
        }
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val style = styles.firstOrNull { it.first == checkedIds.firstOrNull() }?.second ?: return@setOnCheckedStateChangeListener
            UserSettingsStore.setMusicBarStyle(this, style)
            refreshMusicBarState()
        }
        refreshMusicBarState()
    }

    private fun refreshMusicBarState() {
        val style = UserSettingsStore.getMusicBarStyle(this)
        val checkedId = when (style) {
            UserSettingsStore.MUSIC_BAR_BLUE -> R.id.chipMusicBarBlue
            UserSettingsStore.MUSIC_BAR_RED -> R.id.chipMusicBarRed
            UserSettingsStore.MUSIC_BAR_GREEN -> R.id.chipMusicBarGreen
            UserSettingsStore.MUSIC_BAR_PURPLE -> R.id.chipMusicBarPurple
            else -> R.id.chipMusicBarMulti
        }
        findViewById<ChipGroup>(R.id.chipMusicBarStyle).check(checkedId)
        findViewById<TextView>(R.id.tvMusicBarState).text = "当前：${musicBarStyleName(style)}"
    }

    private fun musicBarStyleName(style: String): String = when (style) {
        UserSettingsStore.MUSIC_BAR_BLUE -> "蓝色"
        UserSettingsStore.MUSIC_BAR_RED -> "红色"
        UserSettingsStore.MUSIC_BAR_GREEN -> "绿色"
        UserSettingsStore.MUSIC_BAR_PURPLE -> "紫色"
        else -> "多彩"
    }


    private fun bindMusicSpectrumSettings() {
        val group = findViewById<ChipGroup>(R.id.chipMusicSpectrumStyle)
        val styles = listOf(
            R.id.chipMusicSpectrumMirror to UserSettingsStore.MUSIC_SPECTRUM_MIRROR,
            R.id.chipMusicSpectrumUp to UserSettingsStore.MUSIC_SPECTRUM_UP,
            R.id.chipMusicSpectrumWave to UserSettingsStore.MUSIC_SPECTRUM_WAVE
        )
        styles.forEach { (id, style) ->
            findViewById<Chip>(id).setOnClickListener {
                UserSettingsStore.setMusicSpectrumStyle(this, style)
                refreshMusicSpectrumState()
                Toast.makeText(this, "频谱样式已切换：${musicSpectrumStyleName(style)}", Toast.LENGTH_SHORT).show()
            }
        }
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val style = styles.firstOrNull { it.first == checkedIds.firstOrNull() }?.second ?: return@setOnCheckedStateChangeListener
            UserSettingsStore.setMusicSpectrumStyle(this, style)
            refreshMusicSpectrumState()
        }
        refreshMusicSpectrumState()
    }

    private fun refreshMusicSpectrumState() {
        val style = UserSettingsStore.getMusicSpectrumStyle(this)
        val checkedId = when (style) {
            UserSettingsStore.MUSIC_SPECTRUM_UP -> R.id.chipMusicSpectrumUp
            UserSettingsStore.MUSIC_SPECTRUM_WAVE -> R.id.chipMusicSpectrumWave
            else -> R.id.chipMusicSpectrumMirror
        }
        findViewById<ChipGroup>(R.id.chipMusicSpectrumStyle).check(checkedId)
        findViewById<TextView>(R.id.tvMusicSpectrumState).text = "当前：${musicSpectrumStyleName(style)}"
    }

    private fun musicSpectrumStyleName(style: String): String = when (style) {
        UserSettingsStore.MUSIC_SPECTRUM_UP -> "仅向上"
        UserSettingsStore.MUSIC_SPECTRUM_WAVE -> "光波线条"
        else -> "唱片包裹"
    }

    private fun bindLyricHighlightSettings() {
        val group = findViewById<ChipGroup>(R.id.chipLyricHighlightStyle)
        val styles = listOf(
            R.id.chipLyricHighlightBlue to UserSettingsStore.LYRIC_HIGHLIGHT_BLUE,
            R.id.chipLyricHighlightRed to UserSettingsStore.LYRIC_HIGHLIGHT_RED,
            R.id.chipLyricHighlightGreen to UserSettingsStore.LYRIC_HIGHLIGHT_GREEN,
            R.id.chipLyricHighlightPurple to UserSettingsStore.LYRIC_HIGHLIGHT_PURPLE,
            R.id.chipLyricHighlightOrange to UserSettingsStore.LYRIC_HIGHLIGHT_ORANGE
        )
        styles.forEach { (id, style) ->
            findViewById<Chip>(id).setOnClickListener {
                UserSettingsStore.setLyricHighlightStyle(this, style)
                refreshLyricHighlightState()
                Toast.makeText(this, "歌词颜色已切换：${lyricHighlightName(style)}", Toast.LENGTH_SHORT).show()
            }
        }
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val style = styles.firstOrNull { it.first == checkedIds.firstOrNull() }?.second ?: return@setOnCheckedStateChangeListener
            UserSettingsStore.setLyricHighlightStyle(this, style)
            refreshLyricHighlightState()
        }
        refreshLyricHighlightState()
    }

    private fun refreshLyricHighlightState() {
        val style = UserSettingsStore.getLyricHighlightStyle(this)
        val checkedId = when (style) {
            UserSettingsStore.LYRIC_HIGHLIGHT_RED -> R.id.chipLyricHighlightRed
            UserSettingsStore.LYRIC_HIGHLIGHT_GREEN -> R.id.chipLyricHighlightGreen
            UserSettingsStore.LYRIC_HIGHLIGHT_PURPLE -> R.id.chipLyricHighlightPurple
            UserSettingsStore.LYRIC_HIGHLIGHT_ORANGE -> R.id.chipLyricHighlightOrange
            else -> R.id.chipLyricHighlightBlue
        }
        findViewById<ChipGroup>(R.id.chipLyricHighlightStyle).check(checkedId)
        findViewById<TextView>(R.id.tvLyricHighlightState).text = "当前：${lyricHighlightName(style)}"
    }

    private fun lyricHighlightName(style: String): String = when (style) {
        UserSettingsStore.LYRIC_HIGHLIGHT_RED -> "红色"
        UserSettingsStore.LYRIC_HIGHLIGHT_GREEN -> "绿色"
        UserSettingsStore.LYRIC_HIGHLIGHT_PURPLE -> "紫色"
        UserSettingsStore.LYRIC_HIGHLIGHT_ORANGE -> "橙色"
        else -> "蓝色"
    }


    private fun bindUpdateChecker() {
        findViewById<MaterialButton>(R.id.btnCheckUpdate).setOnClickListener { checkForUpdate() }
        findViewById<TextView>(R.id.tvUpdateState).text = "当前版本：v${BuildConfig.VERSION_NAME}"
    }

    private fun checkForUpdate() {
        val button = findViewById<MaterialButton>(R.id.btnCheckUpdate)
        val stateView = findViewById<TextView>(R.id.tvUpdateState)
        button.isEnabled = false
        button.text = "检查中..."
        stateView.text = "正在后台读取更新目录，不会跳转到浏览器"
        Thread {
            val result = runCatching { fetchLatestApk() }
            runOnUiThread {
                button.isEnabled = true
                button.text = "检查更新"
                result.onSuccess { latest ->
                    if (latest == null) {
                        stateView.text = "当前已是最新：v${BuildConfig.VERSION_NAME}"
                        showUpdateDialog("已是最新版本", "当前版本 v${BuildConfig.VERSION_NAME}，更新目录里没有发现更高版本 APK。")
                    } else {
                        stateView.text = "发现新版本：v${latest.version}"
                        showUpdateDialog(
                            "发现新版本 v${latest.version}",
                            "检测到更高版本 APK：\n${latest.fileName}\n\n当前版本：v${BuildConfig.VERSION_NAME}\n来源：$updateUrl\n\n软件已在后台完成识别，没有跳转到其他页面。"
                        )
                    }
                }.onFailure { error ->
                    stateView.text = "检查失败：${error.message ?: "网络异常"}"
                    showUpdateDialog("检查失败", "无法读取更新目录：${error.message ?: "网络异常"}\n\n请稍后重试。")
                }
            }
        }.start()
    }

    private fun fetchLatestApk(): ApkCandidate? {
        val request = Request.Builder()
            .url(updateUrl)
            .header("User-Agent", "YunoTools/${BuildConfig.VERSION_NAME} Android UpdateChecker")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        updateClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("服务器返回 ${response.code}")
            val html = response.body?.string().orEmpty()
            val candidates = parseApkCandidates(html)
            return candidates
                .filter { compareVersion(it.version, BuildConfig.VERSION_NAME) > 0 }
                .maxWithOrNull { a, b -> compareVersion(a.version, b.version) }
        }
    }

    private fun parseApkCandidates(html: String): List<ApkCandidate> {
        val decoded = runCatching { URLDecoder.decode(html, "UTF-8") }.getOrDefault(html)
        val apkRegex = Regex("[A-Za-z0-9_ .\\-()]*?(?:v|V)?(\\d+(?:\\.\\d+){1,3})[A-Za-z0-9_ .\\-()]*?\\.apk")
        return apkRegex.findAll(decoded)
            .mapNotNull { match ->
                val fileName = match.value.trim().substringAfterLast('/').substringAfterLast('=')
                val version = match.groupValues.getOrNull(1).orEmpty()
                if (version.isBlank()) null else ApkCandidate(fileName, version)
            }
            .distinctBy { it.fileName }
            .toList()
    }

    private fun compareVersion(left: String, right: String): Int {
        val l = left.split('.').map { it.toIntOrNull() ?: 0 }
        val r = right.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(l.size, r.size)
        for (i in 0 until size) {
            val diff = (l.getOrNull(i) ?: 0) - (r.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun showUpdateDialog(title: String, message: String) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private data class ApkCandidate(val fileName: String, val version: String)

    private fun refreshThemeState() {
        findViewById<TextView>(R.id.tvCurrentTheme).text = "当前：${ThemeActivity.themeDisplayName(UserSettingsStore.getTheme(this))}"
    }
}
