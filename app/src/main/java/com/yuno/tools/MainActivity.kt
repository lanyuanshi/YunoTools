package com.yuno.tools

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.ui.video.VideoParseActivity
import com.yuno.tools.ui.tools.RandomQuoteActivity
import com.yuno.tools.ui.image.ImageWatermarkActivity
import com.yuno.tools.ui.image.ImageCompressActivity
import com.yuno.tools.ui.image.QRCodeActivity
import com.yuno.tools.ui.image.GridCropActivity
import com.yuno.tools.ui.media.AudioSeparateActivity
import com.yuno.tools.ui.media.VideoMD5Activity
import com.yuno.tools.ui.tools.AnswerBookActivity
import com.yuno.tools.ui.tools.BarrageActivity
import com.yuno.tools.ui.tools.ClockActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialCardView>(R.id.cardVideoParse).setOnClickListener {
            startActivity(Intent(this, VideoParseActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardLiveParse).setOnClickListener {
            startActivity(Intent(this, RandomQuoteActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardImageWatermark).setOnClickListener {
            startActivity(Intent(this, ImageWatermarkActivity::class.java))
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
            startActivity(Intent(this, VideoMD5Activity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardBarrage).setOnClickListener {
            startActivity(Intent(this, BarrageActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardClock).setOnClickListener {
            startActivity(Intent(this, ClockActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardAnswerBook).setOnClickListener {
            startActivity(Intent(this, AnswerBookActivity::class.java))
        }
    }
}
