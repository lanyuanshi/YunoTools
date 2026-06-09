package com.yuno.tools

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.ui.video.VideoParseActivity
import com.yuno.tools.ui.tools.AIChatActivity
import com.yuno.tools.ui.image.ImageCompressActivity
import com.yuno.tools.ui.image.QRCodeActivity
import com.yuno.tools.ui.image.GridCropActivity
import com.yuno.tools.ui.media.AudioSeparateActivity
import com.yuno.tools.ui.media.VideoTrimActivity
import com.yuno.tools.ui.tools.AnimeSearchActivity
import com.yuno.tools.ui.tools.BarrageActivity
import com.yuno.tools.ui.tools.ClockActivity
import com.yuno.tools.ui.tools.SubscriptionActivity
import com.yuno.tools.ui.tools.TinyReaderActivity
import com.yuno.tools.ui.profile.ParseHistoryActivity
import com.yuno.tools.ui.profile.SettingsActivity
import com.yuno.tools.util.ThemeApplier

class MainActivity : AppCompatActivity() {
    private enum class MainTab { HOME, PROFILE }

    private var currentTab = MainTab.HOME
    private var avatarPlayer: ExoPlayer? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        UserSettingsStore.persistUriPermission(this, uri)
        UserSettingsStore.setAvatarUri(this, uri.toString())
        loadAvatar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ThemeApplier.apply(this)

        bindHomeCards()
        bindProfilePage()
        bindBottomNav()
        showHome(animate = false)
    }

    private fun bindHomeCards() {
        findViewById<MaterialCardView>(R.id.cardVideoParse).setOnClickListener {
            startActivity(Intent(this, VideoParseActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardLiveParse).setOnClickListener {
            startActivity(Intent(this, AIChatActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardImageCompress).setOnClickListener {
            startActivity(Intent(this, ImageCompressActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardQRCode).setOnClickListener {
            startActivity(Intent(this, QRCodeActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardGridCrop).setOnClickListener {
            startActivity(Intent(this, GridCropActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardAudioSeparate).setOnClickListener {
            startActivity(Intent(this, AudioSeparateActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardVideoMD5).setOnClickListener {
            startActivity(Intent(this, VideoTrimActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardBarrage).setOnClickListener {
            startActivity(Intent(this, BarrageActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardClock).setOnClickListener {
            startActivity(Intent(this, ClockActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardAnimeSearch).setOnClickListener {
            startActivity(Intent(this, AnimeSearchActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSubscription).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardTinyReader).setOnClickListener {
            startActivity(Intent(this, TinyReaderActivity::class.java))
        }
    }

    private fun bindProfilePage() {
        findViewById<MaterialCardView>(R.id.cardAvatarHeader).setOnClickListener { chooseAvatar() }
        findViewById<MaterialCardView>(R.id.cardChooseAvatar).setOnClickListener { chooseAvatar() }
        findViewById<MaterialCardView>(R.id.cardParseHistory).setOnClickListener {
            startActivity(Intent(this, ParseHistoryActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun bindBottomNav() {
        val navHome = findViewById<LinearLayout>(R.id.navChat)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)
        installPressScale(navHome)
        installPressScale(navProfile)
        navHome.setOnClickListener { showHome(animate = true) }
        navProfile.setOnClickListener { showProfile() }
    }

    private fun showHome(animate: Boolean) {
        if (currentTab == MainTab.HOME && findViewById<View>(R.id.scrollView).isVisible) return
        currentTab = MainTab.HOME
        releaseAvatarPlayer()
        val home = findViewById<View>(R.id.scrollView)
        val profile = findViewById<View>(R.id.profilePage)
        profile.animate().cancel()
        home.visibility = View.VISIBLE
        profile.visibility = View.GONE
        findViewById<TextView>(R.id.tvMainTitle).text = "首页"
        if (animate) {
            home.alpha = 0f
            home.translationY = resources.displayMetrics.density * 12f
            home.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        } else {
            home.alpha = 1f
            home.translationY = 0f
        }
        updateNavSelection(MainTab.HOME, animate)
    }

    private fun showProfile() {
        if (currentTab == MainTab.PROFILE && findViewById<View>(R.id.profilePage).isVisible) return
        currentTab = MainTab.PROFILE
        val home = findViewById<View>(R.id.scrollView)
        val profile = findViewById<View>(R.id.profilePage)
        home.animate().cancel()
        home.visibility = View.GONE
        profile.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvMainTitle).text = "个人资料"
        loadAvatar()
        playProfileEntranceBounce()
        updateNavSelection(MainTab.PROFILE, true)
    }

    private fun updateNavSelection(tab: MainTab, animate: Boolean = true) {
        val selectedColor = Color.parseColor("#1E88E5")
        val normalColor = Color.parseColor("#A0A7B3")
        val navHome = findViewById<LinearLayout>(R.id.navChat)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)
        val homeSelected = tab == MainTab.HOME
        tintNav(R.id.icNavHome, R.id.tvNavHome, if (homeSelected) selectedColor else normalColor, homeSelected)
        tintNav(R.id.icNavProfile, R.id.tvNavProfile, if (homeSelected) normalColor else selectedColor, !homeSelected)

        animateNavItem(navHome, homeSelected)
        animateNavItem(navProfile, !homeSelected)
    }

    private fun tintNav(iconId: Int, textId: Int, color: Int, selected: Boolean) {
        findViewById<ImageView>(iconId).apply {
            imageTintList = ColorStateList.valueOf(color)
            background = null
            animate().translationY(if (selected) -5f * resources.displayMetrics.density else 0f)
                .scaleX(if (selected) 1.12f else 1f)
                .scaleY(if (selected) 1.12f else 1f)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(0.9f))
                .start()
        }
        findViewById<TextView>(textId).apply {
            setTextColor(color)
            setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            animate().alpha(if (selected) 1f else 0.72f).setDuration(160L).start()
        }
    }


    private fun animateNavItem(view: View, selected: Boolean) {
        view.animate()
            .translationY(if (selected) -2f * resources.displayMetrics.density else 0f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(0.75f))
            .start()
    }


    private fun installPressScale(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            false
        }
    }

    private fun chooseAvatar() {
        pickAvatar.launch(arrayOf("image/*", "video/*"))
    }

    private fun loadAvatar() {
        val iv = findViewById<ImageView>(R.id.ivAvatar)
        val pv = findViewById<PlayerView>(R.id.pvAvatar)
        val uriText = UserSettingsStore.getAvatarUri(this)
        iv.imageTintList = null
        iv.clearColorFilter()
        iv.setPadding(0, 0, 0, 0)
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

    private fun playProfileEntranceBounce() {
        val root = findViewById<View>(R.id.profilePage)
        root.animate().cancel()
        root.alpha = 0f
        root.translationY = resources.displayMetrics.density * 48f
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(360L)
            .setInterpolator(OvershootInterpolator(0.55f))
            .start()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
        updateNavSelection(currentTab, animate = false)
        if (currentTab == MainTab.PROFILE) loadAvatar()
    }

    override fun onBackPressed() {
        if (currentTab == MainTab.PROFILE) {
            showHome(animate = true)
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        releaseAvatarPlayer()
    }

    override fun onDestroy() {
        releaseAvatarPlayer()
        super.onDestroy()
    }
}
