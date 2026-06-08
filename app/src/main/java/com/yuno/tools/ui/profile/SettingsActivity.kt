package com.yuno.tools.ui.profile

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.ui.tools.AIChatActivity
import com.yuno.tools.util.ThemeApplier

class SettingsActivity : AppCompatActivity() {
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
        bindTheme(R.id.themeDefault, UserSettingsStore.THEME_DEFAULT)
        bindTheme(R.id.themeBlack, UserSettingsStore.THEME_BLACK)
        bindTheme(R.id.themePink, UserSettingsStore.THEME_PINK)
        bindTheme(R.id.themeBlue, UserSettingsStore.THEME_BLUE)
        bindTheme(R.id.themeAmis, UserSettingsStore.THEME_AMIS)
        bindTheme(R.id.themeYuno, UserSettingsStore.THEME_YUNO)
        bindTheme(R.id.themeFeiXue1, UserSettingsStore.THEME_FEI_XUE_1)
        bindAiSettings()
        refreshThemeState()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this); refreshThemeState(); loadAiState() }

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

    private fun bindTheme(id: Int, theme: String) {
        findViewById<MaterialCardView>(id).setOnClickListener {
            UserSettingsStore.setTheme(this, theme)
            ThemeApplier.apply(this)
            refreshThemeState()
        }
    }

    private fun refreshThemeState() {
        val current = UserSettingsStore.getTheme(this)
        listOf(R.id.themeDefault to UserSettingsStore.THEME_DEFAULT, R.id.themeBlack to UserSettingsStore.THEME_BLACK, R.id.themePink to UserSettingsStore.THEME_PINK, R.id.themeBlue to UserSettingsStore.THEME_BLUE, R.id.themeAmis to UserSettingsStore.THEME_AMIS, R.id.themeYuno to UserSettingsStore.THEME_YUNO, R.id.themeFeiXue1 to UserSettingsStore.THEME_FEI_XUE_1).forEach { (id, key) ->
            val card = findViewById<MaterialCardView>(id)
            card.strokeWidth = if (key == current) (2 * resources.displayMetrics.density).toInt() else 0
            card.strokeColor = ThemeApplier.current(this).primary
        }
        findViewById<TextView>(R.id.tvCurrentTheme).text = when(current){ UserSettingsStore.THEME_BLACK -> "当前：黑色主题"; UserSettingsStore.THEME_PINK -> "当前：粉色主题"; UserSettingsStore.THEME_BLUE -> "当前：蓝色主题"; UserSettingsStore.THEME_AMIS -> "当前：爱弥斯主题"; UserSettingsStore.THEME_YUNO -> "当前：尤诺主题"; UserSettingsStore.THEME_FEI_XUE_1 -> "当前：绯雪1主题"; else -> "当前：默认主题" }
    }
}
