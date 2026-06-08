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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
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
        setContentView(R.layout.activity_profile)
        ThemeApplier.apply(this)
        playEntranceBounce()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finishWithAnim() }
        findViewById<MaterialCardView>(R.id.cardAvatarHeader).setOnClickListener { chooseAvatar() }
        findViewById<MaterialCardView>(R.id.cardChooseAvatar).setOnClickListener { chooseAvatar() }
        findViewById<MaterialCardView>(R.id.cardParseHistory).setOnClickListener { startActivity(Intent(this, ParseHistoryActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        loadAvatar()
    }
    override fun onResume() { super.onResume(); ThemeApplier.apply(this); loadAvatar() }
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
        val root = findViewById<View>(R.id.profileRoot)
        root.alpha = 0f
        root.translationY = resources.displayMetrics.density * 48f
        root.animate().alpha(1f).translationY(0f).setDuration(360L).setInterpolator(OvershootInterpolator(0.55f)).start()
    }
    override fun onBackPressed() { finishWithAnim() }
    override fun onStop() { super.onStop(); releaseAvatarPlayer() }
    override fun onDestroy() { releaseAvatarPlayer(); super.onDestroy() }
    private fun finishWithAnim() { finish(); overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out) }
}
