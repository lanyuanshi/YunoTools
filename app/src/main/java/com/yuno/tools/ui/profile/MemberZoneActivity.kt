package com.yuno.tools.ui.profile

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.yuno.tools.data.AccountStore
import com.yuno.tools.ui.tools.ExpressQueryActivity
import com.yuno.tools.util.ThemeApplier
import kotlin.math.roundToInt

class MemberZoneActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        buildUi()
        render()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
        render()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#2E1065"), Color.parseColor("#4C1D95"), Color.parseColor("#F8FAFC")))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(22), dp(18), dp(30)) }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun render() {
        content.removeAllViews()
        val state = AccountStore.state(this)
        addHeader(state)
        addVipCard(state)
        addExpressTool(state)
        addComingSoon()
    }

    private fun addHeader(state: AccountStore.AccountState) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply {
            text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#33FFFFFF"), dp(18), Color.parseColor("#55FFFFFF"), 1)
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titles.addView(TextView(this).apply { text = "会员专区"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titles.addView(TextView(this).apply { text = if (state.isVip) "${AccountStore.vipText(state)} · 专属工具已解锁" else "兑换会员后解锁专属工具"; textSize = 13f; setTextColor(Color.parseColor("#DDD6FE")) })
        row.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)
    }

    private fun addVipCard(state: AccountStore.AccountState) {
        val card = card(Color.parseColor("#EFFFFFFF"))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(TextView(this).apply { text = if (state.isVip) "尊贵会员" else "未开通会员"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#1E1B4B")) })
        box.addView(TextView(this).apply { text = if (state.loggedIn) "${state.nickname.ifBlank { state.username }} · ${AccountStore.vipText(state)} · ${state.points}积分" else "请先到我的页面登录/注册，再兑换会员"; textSize = 14f; setTextColor(Color.parseColor("#64748B")) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
    }

    private fun addExpressTool(state: AccountStore.AccountState) {
        val card = card(Color.WHITE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(TextView(this).apply { text = "快递查询"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        box.addView(TextView(this).apply { text = "会员专属工具，接入 UAPI，支持自动识别快递公司、手机尾号验证、轨迹复制和状态展示。"; textSize = 14f; setLineSpacing(dp(4).toFloat(), 1f); setTextColor(Color.parseColor("#64748B")) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val btn = Button(this).apply {
            text = if (state.isVip) "进入快递查询" else "未解锁 · 去我的页面兑换"
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = rounded(Color.parseColor(if (state.isVip) "#10B981" else "#8B5CF6"), dp(18), Color.TRANSPARENT, 0)
            setOnClickListener {
                if (AccountStore.hasVipAccess(this@MemberZoneActivity)) startActivity(Intent(this@MemberZoneActivity, ExpressQueryActivity::class.java))
                else { toast("请先在我的页面输入兑换码开通会员"); startActivity(Intent(this@MemberZoneActivity, ProfileActivity::class.java)) }
            }
        }
        box.addView(btn, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun addComingSoon() {
        val card = card(Color.parseColor("#F8FAFC"))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(TextView(this).apply { text = "后续会员权益"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        box.addView(TextView(this).apply { text = """• 更多高级工具统一放在会员专区
• 会员状态和兑换码在我的页面管理
• 专区工具会持续扩展"""; textSize = 14f; setLineSpacing(dp(5).toFloat(), 1f); setTextColor(Color.parseColor("#475569")) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun card(color: Int) = LinearLayout(this).apply { background = rounded(color, dp(24), Color.parseColor("#33FFFFFF"), 1); elevation = dp(3).toFloat() }
    private fun rounded(color: Int, radius: Int, stroke: Int, strokeWidth: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke) }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
