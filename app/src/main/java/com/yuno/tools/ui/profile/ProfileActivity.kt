package com.yuno.tools.ui.profile
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.data.AccountStore
import com.yuno.tools.util.ThemeApplier
class ProfileActivity : AppCompatActivity() {
    private var avatarPlayer: ExoPlayer? = null
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        UserSettingsStore.persistUriPermission(this, uri)
        UserSettingsStore.setAvatarUri(this, uri.toString())
        loadAvatar()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal)
        ThemeApplier.apply(this)
        playEntranceBounce()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finishWithAnim() }
        findViewById<MaterialCardView>(R.id.cardAvatarHeader).setOnClickListener { chooseAvatar() }
        bindAccountPanel()
        findViewById<MaterialCardView>(R.id.cardPointsMall).setOnClickListener {
            startActivity(Intent(this, PointsMallActivity::class.java))
        }
        loadAvatar()
        refreshAccountPanel()
    }
    override fun onResume() { super.onResume(); ThemeApplier.apply(this); loadAvatar(); refreshAccountPanel() }

    private fun bindAccountPanel() {
        findViewById<Button>(R.id.btnProfileLogin).setOnClickListener {
            val user = findViewById<EditText>(R.id.etProfileAccount).text.toString()
            val pass = findViewById<EditText>(R.id.etProfilePassword).text.toString()
            AccountStore.registerOrLogin(this, user, pass)
                .onSuccess { toast("登录成功"); refreshAccountPanel() }
                .onFailure { toast(it.message ?: "登录失败") }
        }
        findViewById<Button>(R.id.btnProfileCheckIn).setOnClickListener {
            val r = AccountStore.checkIn(this)
            toast(r.message)
            refreshAccountPanel()
        }
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            AccountStore.logout(this)
            toast("已退出登录")
            refreshAccountPanel()
        }
    }

    private fun refreshAccountPanel() {
        val state = AccountStore.state(this)
        runCatching {
            findViewById<TextView>(R.id.tvProfileName).text = if (state.loggedIn) state.nickname.ifBlank { state.username } else "YunoTools"
            findViewById<TextView>(R.id.tvAvatarHint).text = if (state.loggedIn) {
                if (state.isVip) "VIP会员 · ${AccountStore.vipText(state)} · ${state.points}积分" else "普通用户 · ${state.points}积分 · 点击头像可更换"
            } else "未登录 · 点击下方账号卡片可注册/登录，点击头像可更换"
            findViewById<TextView>(R.id.tvMemberSummary).text = if (state.loggedIn) {
                "${AccountStore.vipText(state)} · ${state.points}积分 · 连续签到${state.streak}天"
            } else "未登录 · 登录后可签到获得积分"
        }
        runCatching {
            findViewById<View>(R.id.loginInlinePanel).visibility = if (state.loggedIn) View.GONE else View.VISIBLE
            findViewById<View>(R.id.memberInlinePanel).visibility = if (state.loggedIn) View.VISIBLE else View.GONE
            if (state.loggedIn) {
                findViewById<TextView>(R.id.tvProfileVip).text = "${AccountStore.vipText(state)} · ${state.points}积分 · 累计签到${state.totalCheckIn}天"
                val checked = AccountStore.todayChecked(this)
                findViewById<Button>(R.id.btnProfileCheckIn).apply {
                    text = if (checked) "今日已签到 · 连续${state.streak}天" else "立即签到 · 连续${state.streak}天"
                    isEnabled = !checked
                    alpha = if (checked) 0.65f else 1f
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun chooseAvatar() {
        pickAvatar.launch(arrayOf("image/*", "video/*"))
    }
    private fun loadAvatar() {
        val iv = findViewById<ImageView>(R.id.ivAvatar)
        val pv = findViewById<PlayerView>(R.id.pvAvatar)
        val uriText = UserSettingsStore.getAvatarUri(this)
        iv.imageTintList = null
        iv.clearColorFilter()
        iv.setPadding(0,0,0,0)
        if (uriText.isNotBlank()) {
            val uri = Uri.parse(uriText)
            val isVideo = contentResolver.getType(uri)?.startsWith("video/") == true
            if (isVideo) {
                Glide.with(iv).clear(iv)
                iv.setImageResource(R.drawable.bg_circle_blue)
                iv.imageTintList = null
                pv.visibility = View.VISIBLE
                releaseAvatarPlayer()
                avatarPlayer = ExoPlayer.Builder(this).build().also { player ->
                    player.volume = 0f
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    player.setMediaItem(MediaItem.fromUri(uri))
                    pv.player = player
                    player.prepare()
                    player.playWhenReady = true
                }
            } else {
                releaseAvatarPlayer()
                pv.visibility = View.GONE
                Glide.with(iv)
                    .load(uri)
                    .signature(ObjectKey(uriText + "#" + System.currentTimeMillis()))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .placeholder(R.drawable.bg_circle_blue)
                    .error(R.drawable.ic_profile)
                    .into(iv)
            }
            findViewById<TextView>(R.id.tvAvatarHint).text = "点击更换头像，支持 GIF / WebP / 视频动态头像"
        } else {
            releaseAvatarPlayer()
            pv.visibility = View.GONE
            Glide.with(iv).clear(iv)
            iv.setImageResource(R.drawable.ic_profile)
            iv.imageTintList = ColorStateList.valueOf(Color.WHITE)
            findViewById<TextView>(R.id.tvAvatarHint).text = "点击选择头像，支持 GIF / WebP / 视频动态头像"
        }
    }
    private fun releaseAvatarPlayer() {
        val pv = runCatching { findViewById<PlayerView>(R.id.pvAvatar) }.getOrNull()
        pv?.player = null
        avatarPlayer?.release()
        avatarPlayer = null
    }
    private fun playEntranceBounce() {
        val root = findViewById<View>(R.id.personalRoot)
        root.alpha = 0f
        root.translationY = resources.displayMetrics.density * 48f
        root.animate().alpha(1f).translationY(0f).setDuration(360L).setInterpolator(OvershootInterpolator(0.55f)).start()
    }
    override fun onBackPressed() { finishWithAnim() }
    override fun onStop() { super.onStop(); releaseAvatarPlayer() }
    override fun onDestroy() { releaseAvatarPlayer(); super.onDestroy() }
    private fun finishWithAnim() { finish(); overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out) }
}
