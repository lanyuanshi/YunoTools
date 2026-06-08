package com.yuno.tools.ui.tools

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.util.ClipboardUtil
import com.yuno.tools.util.ThemeApplier

class SubscriptionActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout

    private data class SubscriptionModule(
        val id: String,
        val title: String,
        val desc: String,
        val link: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        list = findViewById(R.id.subscriptionList)
        buildModuleList()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out)
    }

    private fun buildModuleList() {
        list.removeAllViews()
        modules.forEach { list.addView(createModuleCard(it)) }
    }

    private fun createModuleCard(module: SubscriptionModule): MaterialCardView {
        val primary = ThemeApplier.current(this).primary
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            radius = dp(20).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            setOnClickListener { copyModuleLink(module) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val textBox = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(TextView(this).apply {
            text = module.title
            textSize = 16f
            setTextColor(Color.parseColor("#111827"))
            setTypeface(typeface, Typeface.BOLD)
        })
        textBox.addView(TextView(this).apply {
            text = module.desc
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
        })

        val action = TextView(this).apply {
            text = "复制"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(primary)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        row.addView(textBox)
        row.addView(action)
        card.addView(row)
        return card
    }

    private fun copyModuleLink(module: SubscriptionModule) {
        ClipboardUtil.copyToClipboard(this, module.link, "${module.title}订阅链接")
        Toast.makeText(this, "已复制：${module.title}", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val modules = listOf(
            SubscriptionModule("basic", "基础模块订阅", "适合日常基础功能使用", "YUNO_SUBSCRIPTION_BASIC_PENDING_LINK"),
            SubscriptionModule("advanced", "进阶模块订阅", "包含更多增强功能入口", "YUNO_SUBSCRIPTION_ADVANCED_PENDING_LINK"),
            SubscriptionModule("vip", "VIP模块订阅", "预留高级订阅通道", "YUNO_SUBSCRIPTION_VIP_PENDING_LINK"),
            SubscriptionModule("backup", "备用模块订阅", "用于后续扩展或备用线路", "YUNO_SUBSCRIPTION_BACKUP_PENDING_LINK")
        )
    }
}
