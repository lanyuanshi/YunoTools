package com.yuno.tools.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object AccountStore {
    private const val PREFS = "yuno_account_store"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_POINTS = "points"
    private const val KEY_TOTAL_CHECKIN = "total_checkin"
    private const val KEY_STREAK = "streak"
    private const val KEY_LAST_CHECKIN = "last_checkin"
    private const val KEY_VIP_UNTIL = "vip_until"
    private const val KEY_CREATED_AT = "created_at"
    private const val PERMANENT_VIP_UNTIL = 4102444800000L

    data class AccountState(
        val loggedIn: Boolean,
        val username: String,
        val nickname: String,
        val points: Int,
        val totalCheckIn: Int,
        val streak: Int,
        val lastCheckIn: String,
        val vipUntil: Long,
        val createdAt: Long
    ) {
        val isVip: Boolean get() = vipUntil > System.currentTimeMillis()
    }

    data class CheckInResult(
        val success: Boolean,
        val message: String,
        val gained: Int,
        val state: AccountState
    )

    fun state(context: Context): AccountState {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AccountState(
            loggedIn = sp.getBoolean(KEY_LOGGED_IN, false),
            username = sp.getString(KEY_USERNAME, "") ?: "",
            nickname = sp.getString(KEY_NICKNAME, "") ?: "",
            points = sp.getInt(KEY_POINTS, 0),
            totalCheckIn = sp.getInt(KEY_TOTAL_CHECKIN, 0),
            streak = sp.getInt(KEY_STREAK, 0),
            lastCheckIn = sp.getString(KEY_LAST_CHECKIN, "") ?: "",
            vipUntil = sp.getLong(KEY_VIP_UNTIL, 0L),
            createdAt = sp.getLong(KEY_CREATED_AT, 0L)
        )
    }

    fun registerOrLogin(context: Context, username: String, password: String, nickname: String = ""): Result<AccountState> {
        val u = username.trim()
        val p = password.trim()
        if (u.length < 2) return Result.failure(IllegalArgumentException("账号至少输入 2 个字符"))
        if (p.length < 3) return Result.failure(IllegalArgumentException("密码至少输入 3 个字符"))
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldUser = sp.getString(KEY_USERNAME, "") ?: ""
        val oldPass = sp.getString(KEY_PASSWORD, "") ?: ""
        val now = System.currentTimeMillis()
        val firstCreate = oldUser.isBlank() || oldUser != u
        if (oldUser.isNotBlank() && oldUser == u && oldPass.isNotBlank() && oldPass != p) {
            return Result.failure(IllegalArgumentException("本地已有该账号，密码不一致"))
        }
        val display = nickname.trim().ifBlank { u }
        sp.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_USERNAME, u)
            .putString(KEY_PASSWORD, p)
            .putString(KEY_NICKNAME, display)
            .putLong(KEY_CREATED_AT, if (firstCreate) now else sp.getLong(KEY_CREATED_AT, now))
            .putInt(KEY_POINTS, if (firstCreate) max(sp.getInt(KEY_POINTS, 0), 30) else sp.getInt(KEY_POINTS, 0))
            .apply()
        return Result.success(state(context))
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOGGED_IN, false).apply()
    }

    fun updateNickname(context: Context, nickname: String): AccountState {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NICKNAME, nickname.trim()).apply()
        return state(context)
    }

    fun checkIn(context: Context): CheckInResult {
        val st = state(context)
        if (!st.loggedIn) return CheckInResult(false, "请先登录", 0, st)
        val today = today()
        if (st.lastCheckIn == today) return CheckInResult(false, "今天已经签到过了", 0, st)
        val yesterday = dateOffset(-1)
        val newStreak = if (st.lastCheckIn == yesterday) st.streak + 1 else 1
        val gain = 10 + minOf(newStreak, 7) * 2 + if (st.isVip) 8 else 0
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_LAST_CHECKIN, today)
            .putInt(KEY_STREAK, newStreak)
            .putInt(KEY_TOTAL_CHECKIN, st.totalCheckIn + 1)
            .putInt(KEY_POINTS, st.points + gain)
            .apply()
        return CheckInResult(true, "签到成功，获得 $gain 积分", gain, state(context))
    }

    fun addPoints(context: Context, delta: Int): AccountState {
        val st = state(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_POINTS, max(0, st.points + delta)).apply()
        return state(context)
    }

    fun redeemVip(context: Context, days: Int, cost: Int): Result<AccountState> {
        val st = state(context)
        if (!st.loggedIn) return Result.failure(IllegalStateException("请先登录"))
        if (st.points < cost) return Result.failure(IllegalStateException("积分不足，还差 ${cost - st.points} 积分"))
        val base = max(st.vipUntil, System.currentTimeMillis())
        val vipUntil = base + days * 24L * 60L * 60L * 1000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_POINTS, st.points - cost)
            .putLong(KEY_VIP_UNTIL, vipUntil)
            .apply()
        return Result.success(state(context))
    }


    fun redeemCode(context: Context, code: String): Result<AccountState> {
        val st = state(context)
        if (!st.loggedIn) return Result.failure(IllegalStateException("请先登录"))
        if (code.trim() != "蓝鸢") return Result.failure(IllegalArgumentException("兑换码无效"))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_VIP_UNTIL, PERMANENT_VIP_UNTIL)
            .apply()
        return Result.success(state(context))
    }

    fun hasVipAccess(context: Context): Boolean = state(context).isVip

    fun vipText(state: AccountState): String = if (state.isVip) {
        if (state.vipUntil >= PERMANENT_VIP_UNTIL) "永久会员" else "会员有效期至 ${dateTime(state.vipUntil)}"
    } else "普通用户"

    fun todayChecked(context: Context): Boolean = state(context).lastCheckIn == today()

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    private fun dateOffset(offset: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(System.currentTimeMillis() + offset * 24L * 60L * 60L * 1000L))
    fun dateTime(ts: Long): String = if (ts <= 0L) "未开通" else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts))
}
