package com.yuno.tools.ui.video

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yuno.tools.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var binding: ActivityPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUrl = intent.getStringExtra("video_url") ?: ""
        val title = intent.getStringExtra("title") ?: ""

        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "视频地址无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        initPlayer(videoUrl)
    }

    private fun initPlayer(videoUrl: String) {
        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
        binding.playerView.player = player
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
