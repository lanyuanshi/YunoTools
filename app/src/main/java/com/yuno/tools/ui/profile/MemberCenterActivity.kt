package com.yuno.tools.ui.profile

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.yuno.tools.data.AccountStore
import com.yuno.tools.ui.tools.ExpressQueryActivity
import com.yuno.tools.util.ThemeApplier
import kotlin.math.roundToInt

class MemberCenterActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvPoints: TextView
    private lateinit var tvVip: TextView
    private lateinit var tvCheckin: TextView
    private lateinit var tvStreak: TextView
    private lateinit var btnCheckin: Button

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#FFF7ED"), Color.parseColor("#EEF2FF"), Color.parseColor("#F8FAFC"))
            )
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun render() {
        content.removeAllViews()
        val state = AccountStore.state(this)
        addHeader(state)
        if (state.loggedIn) {
            addStats(state)
            addCheckin(state)
            addVipPanel(state)
            addMemberTools(state)
            addAccountActions(state)
        } else {
            addLoginPanel()
            addGuestTips()
        }
    }

    private fun addHeader(state: AccountStore.AccountState) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#111827"))
            background = rounded(Color.WHITE, dp(18), Color.parseColor("#66FFFFFF"), 1)
            elevation = dp(2).toFloat()
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        tvTitle = TextView(this).apply {
            text = if (state.loggedIn) "会员中心" else "登录 / 注册"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        }
        tvSubtitle = TextView(this).apply {
            text = if (state.loggedIn) "${state.nickname.ifBlank { state.username }} · ${AccountStore.vipText(state)}" else "当前为本地账号系统，随便注册即可使用"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
        }
        titleBox.addView(tvTitle)
        titleBox.addView(tvSubtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        row.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)
    }

    private fun addLoginPanel() {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16)) }
        val nick = input("昵称，可不填", false)
        val user = input("账号，例如 yuno", false)
        val pass = input("密码，至少 3 位", true)
        box.addView(sectionTitle("创建或登录本地账号"))
        box.addView(nick, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        box.addView(user, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        box.addView(pass, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        val btn = Button(this).apply {
            text = "登录 / 注册"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#4F46E5"), dp(18), Color.TRANSPARENT, 0)
            setOnClickListener {
                val result = AccountStore.registerOrLogin(this@MemberCenterActivity, user.text.toString(), pass.text.toString(), nick.text.toString())
                result.onSuccess {
                    toast("登录成功，新账号赠送 30 积分")
                    render()
                }.onFailure { e -> toast(e.message ?: "登录失败") }
            }
        }
        box.addView(btn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        box.addView(TextView(this).apply {
            text = "说明：当前版本为本地账号，数据保存在本机；后续可接入服务器账号、真实会员支付和云端同步。"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }

    private fun addStats(state: AccountStore.AccountState) {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(sectionTitle("账号概览"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvPoints = statBlock("积分", state.points.toString(), "可签到获取")
        tvVip = statBlock("会员", if (state.isVip) "VIP" else "普通", if (state.isVip) AccountStore.dateTime(state.vipUntil) else "可用积分兑换")
        row.addView(tvPoints, LinearLayout.LayoutParams(0, dp(96), 1f).apply { rightMargin = dp(8) })
        row.addView(tvVip, LinearLayout.LayoutParams(0, dp(96), 1f).apply { leftMargin = dp(8) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }

    private fun addCheckin(state: AccountStore.AccountState) {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(sectionTitle("每日签到"))
        tvCheckin = TextView(this).apply {
            text = if (AccountStore.todayChecked(this@MemberCenterActivity)) "今日已签到" else "今日未签到，签到可获得积分"
            textSize = 15f
            setTextColor(Color.parseColor("#334155"))
        }
        tvStreak = TextView(this).apply {
            text = "连续签到 ${state.streak} 天 · 累计签到 ${state.totalCheckIn} 天"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
        }
        btnCheckin = Button(this).apply {
            text = if (AccountStore.todayChecked(this@MemberCenterActivity)) "已签到" else "立即签到"
            isEnabled = !AccountStore.todayChecked(this@MemberCenterActivity)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            alpha = if (isEnabled) 1f else 0.65f
            background = rounded(Color.parseColor("#F97316"), dp(18), Color.TRANSPARENT, 0)
            setOnClickListener {
                val result = AccountStore.checkIn(this@MemberCenterActivity)
                toast(result.message)
                render()
            }
        }
        box.addView(tvCheckin, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        box.addView(tvStreak, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        box.addView(btnCheckin, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        box.addView(TextView(this).apply {
            text = "积分规则：基础 10 分 + 连续签到奖励，连续越多越高；会员签到额外 +8 分。"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun addVipPanel(state: AccountStore.AccountState) {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(sectionTitle("会员权益"))
        box.addView(TextView(this).apply {
            text = "当前权益：会员标识、签到积分加成、后续高级功能预留。"
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(redeemButton("7天会员\n80积分", 7, 80), LinearLayout.LayoutParams(0, dp(68), 1f).apply { rightMargin = dp(6) })
        row.addView(redeemButton("30天会员\n260积分", 30, 260), LinearLayout.LayoutParams(0, dp(68), 1f).apply { leftMargin = dp(6); rightMargin = dp(6) })
        row.addView(redeemButton("365天会员\n1999积分", 365, 1999), LinearLayout.LayoutParams(0, dp(68), 1f).apply { leftMargin = dp(6) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val codeInput = input("输入兑换码", false)
        box.addView(codeInput, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        val codeBtn = Button(this).apply {
            text = "兑换永久会员"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#7C3AED"), dp(18), Color.TRANSPARENT, 0)
            setOnClickListener {
                AccountStore.redeemCode(this@MemberCenterActivity, codeInput.text.toString())
                    .onSuccess { toast("兑换成功，已开通永久会员"); render() }
                    .onFailure { e -> toast(e.message ?: "兑换失败") }
            }
        }
        box.addView(codeBtn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }


    private fun addMemberTools(state: AccountStore.AccountState) {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(sectionTitle("会员专区"))
        box.addView(TextView(this).apply {
            text = if (state.isVip) "已解锁会员工具：快递查询" else "开通会员后可使用快递查询等专属工具"
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val express = TextView(this).apply {
            text = if (state.isVip) "快递查询\n自动识别快递公司，展示物流轨迹" else "快递查询\n会员专属，兑换后解锁"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setTextColor(Color.parseColor(if (state.isVip) "#0F766E" else "#64748B"))
            background = rounded(Color.parseColor(if (state.isVip) "#ECFDF5" else "#F1F5F9"), dp(18), Color.parseColor(if (state.isVip) "#5EEAD4" else "#CBD5E1"), 1)
            setOnClickListener {
                if (AccountStore.hasVipAccess(this@MemberCenterActivity)) {
                    startActivity(Intent(this@MemberCenterActivity, ExpressQueryActivity::class.java))
                } else {
                    toast("请先兑换或开通会员")
                }
            }
        }
        box.addView(express, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(14) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun addAccountActions(state: AccountStore.AccountState) {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(sectionTitle("账号管理"))
        box.addView(TextView(this).apply {
            text = "账号：${state.username}\n昵称：${state.nickname.ifBlank { state.username }}"
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val logout = Button(this).apply {
            text = "退出登录"
            textSize = 15f
            setTextColor(Color.parseColor("#EF4444"))
            background = rounded(Color.parseColor("#FEF2F2"), dp(16), Color.parseColor("#FECACA"), 1)
            setOnClickListener { AccountStore.logout(this@MemberCenterActivity); toast("已退出登录"); render() }
        }
        box.addView(logout, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun addGuestTips() {
        val card = card()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)) }
        box.addView(sectionTitle("功能说明"))
        box.addView(TextView(this).apply {
            text = "登录后可使用：\n• 每日签到获取积分\n• 连续签到奖励\n• 积分兑换会员\n• 个人中心展示会员状态\n\n当前版本不需要手机号和验证码，输入任意账号密码即可注册。"
            textSize = 14f
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setTextColor(Color.parseColor("#334155"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun redeemButton(text: String, days: Int, cost: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#92400E"))
        background = rounded(Color.parseColor("#FFFBEB"), dp(16), Color.parseColor("#FCD34D"), 1)
        setOnClickListener {
            AccountStore.redeemVip(this@MemberCenterActivity, days, cost)
                .onSuccess { toast("兑换成功，会员已延长 $days 天"); render() }
                .onFailure { e -> toast(e.message ?: "兑换失败") }
        }
    }

    private fun input(hint: String, password: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setSingleLine(true)
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setTextColor(Color.parseColor("#111827"))
        setHintTextColor(Color.parseColor("#94A3B8"))
        background = rounded(Color.parseColor("#88FFFFFF"), dp(16), Color.parseColor("#D8E3F0"), 1)
        setPadding(dp(14), 0, dp(14), 0)
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#111827"))
    }

    private fun statBlock(label: String, value: String, sub: String): TextView = TextView(this).apply {
        text = "$label\n$value\n$sub"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#111827"))
        background = rounded(Color.parseColor("#F8FAFC"), dp(18), Color.parseColor("#E2E8F0"), 1)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.parseColor("#CFFFFFFF"), dp(22), Color.parseColor("#AAFFFFFF"), 1)
        elevation = dp(4).toFloat()
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}