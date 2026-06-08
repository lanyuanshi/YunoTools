package com.yuno.tools

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import android.widget.LinearLayout
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
import com.yuno.tools.ui.profile.ProfileActivity
import com.yuno.tools.util.ThemeApplier

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ThemeApplier.apply(this)

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

        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            overridePendingTransition(R.anim.profile_slide_up_in, R.anim.profile_stay)
        }
        findViewById<LinearLayout>(R.id.navChat).setOnClickListener {
            // 当前页就是首页，保持轻微反馈，不做跳转
        }
    }
    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }
}