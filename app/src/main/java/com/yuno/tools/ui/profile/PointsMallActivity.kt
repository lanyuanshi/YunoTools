package com.yuno.tools.ui.profile

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.R
import com.yuno.tools.data.AccountStore
import com.yuno.tools.util.ThemeApplier

class PointsMallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_points_mall)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnVip7).setOnClickListener { redeemVip(7, 80) }
        findViewById<Button>(R.id.btnVip30).setOnClickListener { redeemVip(30, 260) }
        findViewById<Button>(R.id.btnVip365).setOnClickListener { redeemVip(365, 1999) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
        refresh()
    }

    private fun redeemVip(days: Int, cost: Int) {
        AccountStore.redeemVip(this, days, cost)
            .onSuccess { toast("兑换成功，会员已延长 ${days}天"); refresh() }
            .onFailure { toast(it.message ?: "兑换失败") }
    }

    private fun refresh() {
        val state = AccountStore.state(this)
        findViewById<TextView>(R.id.tvMallSummary).text = if (state.loggedIn) {
            "${AccountStore.vipText(state)} · 当前${state.points}积分"
        } else {
            "未登录 · 登录后可使用积分兑换 VIP"
        }
        findViewById<TextView>(R.id.tvMallHint).text = if (state.loggedIn) {
            "累计签到${state.totalCheckIn}天 · 连续签到${state.streak}天"
        } else {
            "请先返回我的页面登录/注册，再进入积分商城兑换"
        }
        listOf(R.id.btnVip7, R.id.btnVip30, R.id.btnVip365).forEach { id ->
            findViewById<Button>(id).isEnabled = state.loggedIn
            findViewById<Button>(id).alpha = if (state.loggedIn) 1f else 0.55f
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
