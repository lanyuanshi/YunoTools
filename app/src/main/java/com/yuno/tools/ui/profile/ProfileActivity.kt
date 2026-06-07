package com.yuno.tools.ui.profile
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.util.ThemeApplier

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        ThemeApplier.apply(this)
        playEntranceBounce()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finishWithAnim() }
        findViewById<MaterialCardView>(R.id.cardParseHistory).setOnClickListener { startActivity(Intent(this, ParseHistoryActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        loadAvatar()
    }
    override fun onResume() { super.onResume(); ThemeApplier.apply(this); loadAvatar() }
    private fun loadAvatar() {
        val iv = findViewById<ImageView>(R.id.ivAvatar)
        val uri = UserSettingsStore.getAvatarUri(this)
        if (uri.isNotBlank()) Glide.with(iv).load(uri).circleCrop().placeholder(R.drawable.bg_circle_blue).into(iv)
        else iv.setImageResource(R.drawable.ic_profile)
    }
    private fun playEntranceBounce() {
        val root = findViewById<View>(R.id.profileRoot)
        root.alpha = 0f
        root.translationY = resources.displayMetrics.density * 48f
        root.animate().alpha(1f).translationY(0f).setDuration(360L).setInterpolator(OvershootInterpolator(0.55f)).start()
    }
    override fun onBackPressed() { finishWithAnim() }
    private fun finishWithAnim() { finish(); overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out) }
}
