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
import com.yuno.tools.BuildConfig
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.ui.tools.AIChatActivity
import com.yuno.tools.util.ThemeApplier

class SettingsActivity : AppCompatActivity() {
    private val updateUrl = "https://www.lyyp.cloud/s/ErLug"
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
        findViewById<MaterialButton>(R.id.btnCheckUpdate).setOnClickListener { openUpdateLink() }
        findViewById<TextView>(R.id.tvUpdateState).text = "当前版本：v${BuildConfig.VERSION_NAME}"
    }

    private fun openUpdateLink() {
        findViewById<TextView>(R.id.tvUpdateState).text = "已在应用内加载更新目录，请自行查看 APK 版本"
        startActivity(UpdateWebActivity.createIntent(this, updateUrl))
        overridePendingTransition(R.anim.profile_slide_up_in, R.anim.profile_stay)
    }

    private fun refreshThemeState() {
        findViewById<TextView>(R.id.tvCurrentTheme).text = "当前：${ThemeActivity.themeDisplayName(UserSettingsStore.getTheme(this))}"
    }
}
