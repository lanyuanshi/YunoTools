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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
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
        findViewById<View>(R.id.cardThemeSettings).setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
            overridePendingTransition(R.anim.profile_slide_up_in, R.anim.profile_stay)
        }
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

    private fun refreshThemeState() {
        findViewById<TextView>(R.id.tvCurrentTheme).text = "当前：${ThemeActivity.themeDisplayName(UserSettingsStore.getTheme(this))}"
    }
}
