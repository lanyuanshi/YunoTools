package com.yuno.tools

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import com.yuno.tools.ui.tools.BangumiWatchActivity
import com.yuno.tools.ui.tools.BarrageActivity
import com.yuno.tools.ui.tools.ClockActivity
import com.yuno.tools.ui.tools.FlashPhotoActivity
import com.yuno.tools.ui.tools.SubscriptionActivity
import com.yuno.tools.ui.tools.TinyReaderActivity
import com.yuno.tools.ui.profile.MusicDownloadsActivity
import com.yuno.tools.ui.profile.ParseHistoryActivity
import com.yuno.tools.ui.profile.SettingsActivity
import com.yuno.tools.util.ThemeApplier

class MainActivity : AppCompatActivity() {
    companion object {
        private const val MUSIC_NOTIFICATION_CHANNEL_ID = "yuno_music_playback"
        private const val MUSIC_NOTIFICATION_ID = 71072
        private const val MUSIC_PREFS = "yuno_music_records"
        private const val MUSIC_FAVORITES_KEY = "online_favorites"
        private const val MUSIC_DOWNLOADS_KEY = "online_downloads"
    }
    private enum class MainTab { HOME, PROFILE }
    private enum class MusicPanelTab { LOCAL, FAVORITE, ONLINE }
    private data class LocalSong(val title: String, val artist: String, val uri: Uri, val durationMs: Long)
    private data class OnlineMusicRecord(
        val title: String,
        val artist: String,
        val sourceLabel: String,
        val pageUrl: String,
        val playUrl: String,
        val localPath: String = "",
        val savedAt: Long = System.currentTimeMillis()
    )

    private var currentTab = MainTab.HOME
    private var avatarPlayer: ExoPlayer? = null
    private var musicPlayer: ExoPlayer? = null
    private var musicSpinAnimator: ObjectAnimator? = null
    private var musicDialog: Dialog? = null
    private var musicShuffleEnabled = false
    private var musicRepeatMode = Player.REPEAT_MODE_ONE
    private var currentMusicTitle = "本地音乐 · 用户歌曲"
    private var currentMusicUri: Uri? = null
    private var musicPanelLastTab = MusicPanelTab.LOCAL
    private var onlineLastKeyword = ""
    private var onlineCachedSongs: List<com.yuno.tools.util.MusicSearchHelper.OnlineSong> = emptyList()
    private var currentOnlinePlayKey: String? = null
    private var loadingOnlinePlayKey: String? = null
    private var refreshOnlineMusicList: (() -> Unit)? = null

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showMusicPanel()
    }

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
        findViewById<MaterialCardView>(R.id.cardBangumiWatch).setOnClickListener {
            startActivity(Intent(this, BangumiWatchActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSubscription).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardTinyReader).setOnClickListener {
            startActivity(Intent(this, TinyReaderActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardFlashPhoto).setOnClickListener {
            startActivity(Intent(this, FlashPhotoActivity::class.java))
        }
    }

    private fun bindProfilePage() {
        findViewById<MaterialCardView>(R.id.cardAvatarHeader).setOnClickListener { chooseAvatar() }
        findViewById<MaterialCardView>(R.id.cardChooseAvatar).setOnClickListener { chooseAvatar() }
        findViewById<MaterialCardView>(R.id.cardParseHistory).setOnClickListener {
            startActivity(Intent(this, ParseHistoryActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMusicDownloads).setOnClickListener {
            startActivity(Intent(this, MusicDownloadsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun bindBottomNav() {
        val navHome = findViewById<LinearLayout>(R.id.navChat)
        val navMusic = findViewById<LinearLayout>(R.id.navMusic)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)
        installPressScale(navHome)
        installPressScale(navMusic)
        installPressScale(navProfile)
        navHome.setOnClickListener { showHome(animate = true) }
        navMusic.setOnClickListener { toggleNavMusic() }
        navMusic.setOnLongClickListener {
            showMusicPanel()
            true
        }
        navProfile.setOnClickListener { showProfile() }
        updateMusicNavState(isPlaying = false)
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
        val navMusic = findViewById<LinearLayout>(R.id.navMusic)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)
        val homeSelected = tab == MainTab.HOME
        tintNav(R.id.icNavHome, R.id.tvNavHome, if (homeSelected) selectedColor else normalColor, homeSelected)
        tintNav(R.id.icNavProfile, R.id.tvNavProfile, if (homeSelected) normalColor else selectedColor, !homeSelected)
        updateMusicNavState(isPlaying = musicPlayer?.isPlaying == true)

        animateNavItem(navHome, homeSelected)
        animateNavItem(navMusic, musicPlayer?.isPlaying == true)
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


    private fun toggleNavMusic() {
        val player = ensureMusicPlayer()
        if (player.isPlaying) {
            player.pause()
            updateMusicNavState(false)
            updateMusicNotification(false)
            return
        }
        if (player.mediaItemCount == 0 || currentMusicUri == null) {
            playSelectedMusic("内置音乐 · 用户歌曲", defaultLocalSongUri(), null)
            return
        }
        player.playWhenReady = true
        player.play()
        updateMusicNavState(player.isPlaying)
        updateMusicNotification(player.isPlaying)
    }

    private var musicPlaylist: List<OnlineMusicRecord> = emptyList()
    private var currentMusicIndex = -1

    private fun playNextMusic() {
        if (musicPlaylist.isEmpty()) return
        val nextIndex = if (musicShuffleEnabled) {
            (0 until musicPlaylist.size).random()
        } else {
            (currentMusicIndex + 1).coerceIn(0, musicPlaylist.size - 1)
        }
        if (nextIndex >= 0 && nextIndex < musicPlaylist.size) {
            currentMusicIndex = nextIndex
            playOnlineRecord(musicPlaylist[nextIndex])
        }
    }

    private fun playPreviousMusic() {
        if (musicPlaylist.isEmpty()) return
        val prevIndex = (currentMusicIndex - 1).coerceIn(0, musicPlaylist.size - 1)
        if (prevIndex >= 0 && prevIndex < musicPlaylist.size) {
            currentMusicIndex = prevIndex
            playOnlineRecord(musicPlaylist[prevIndex])
        }
    }

    private fun updateMusicPlaylist() {
        musicPlaylist = when (musicPanelLastTab) {
            MusicPanelTab.FAVORITE -> loadMusicRecords(MUSIC_FAVORITES_KEY)
            MusicPanelTab.LOCAL -> emptyList()
            MusicPanelTab.ONLINE -> onlineCachedSongs.map { song ->
                OnlineMusicRecord(
                    title = song.title,
                    artist = song.artist,
                    sourceLabel = song.source.label,
                    pageUrl = song.pageUrl,
                    playUrl = song.playUrl.orEmpty(),
                    savedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private fun ensureMusicPlayer(): ExoPlayer {
        return musicPlayer ?: run {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(12000)
                .setDefaultRequestProperties(musicHttpHeaders())
            val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
        }.also { created ->
            musicPlayer = created
            created.repeatMode = musicRepeatMode
            created.shuffleModeEnabled = musicShuffleEnabled
            val songUri = currentMusicUri ?: defaultLocalSongUri()
            created.setMediaItem(MediaItem.fromUri(songUri))
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateMusicNavState(isPlaying)
                    if (isPlaying && loadingOnlinePlayKey != null) {
                        loadingOnlinePlayKey = null
                        refreshOnlineMusicList?.invoke()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        if (loadingOnlinePlayKey != null) {
                            loadingOnlinePlayKey = null
                            refreshOnlineMusicList?.invoke()
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        if (musicRepeatMode != Player.REPEAT_MODE_ONE) {
                            playNextMusic()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val failedTitle = currentMusicTitle.substringAfter(" · ", currentMusicTitle)
                    loadingOnlinePlayKey = null
                    currentOnlinePlayKey = null
                    updateMusicNavState(false)
                    refreshOnlineMusicList?.invoke()
                    Toast.makeText(this@MainActivity, "播放失败：$failedTitle，可能是版权限制或临时链接失效", Toast.LENGTH_LONG).show()
                }
            })
            created.prepare()
        }
    }

    private fun musicHttpHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
        "Accept" to "*/*",
        "Referer" to "https://music.163.com/"
    )

    private fun playSelectedMusic(title: String, uri: Uri, onlineKey: String? = null) {
        currentMusicTitle = title
        currentMusicUri = uri
        currentOnlinePlayKey = onlineKey
        loadingOnlinePlayKey = onlineKey
        val player = ensureMusicPlayer()
        player.stop()
        player.clearMediaItems()
        player.repeatMode = musicRepeatMode
        player.shuffleModeEnabled = musicShuffleEnabled
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        player.play()
        updateMusicNavState(true)
        updateMusicNotification(true)
        refreshOnlineMusicList?.invoke()
    }

    private fun playLocalMusicFromPanel() {
        currentOnlinePlayKey = null
        loadingOnlinePlayKey = null
        playSelectedMusic("内置音乐 · 用户歌曲", defaultLocalSongUri(), null)
    }

    private fun defaultLocalSongUri(): Uri {
        return Uri.parse("android.resource://$packageName/${R.raw.nav_song}")
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requestAudioPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun loadLocalSongs(): List<LocalSong> {
        if (!hasAudioPermission()) return emptyList()
        val songs = mutableListOf<LocalSong>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        runCatching {
            contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "未知歌曲"
                    val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "本地音乐"
                    val duration = cursor.getLong(durationCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    songs.add(LocalSong(title, artist, uri, duration))
                }
            }
        }
        return songs
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "--:--"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun showMusicPanel() {
        musicDialog?.dismiss()
        val dialog = Dialog(this)
        musicDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setPadding((18 * density).toInt(), (28 * density).toInt(), (18 * density).toInt(), (28 * density).toInt())
            setBackgroundColor(Color.argb(88, 12, 18, 28))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * density
                setColor(Color.argb(224, 246, 250, 255))
                setStroke((1.2f * density).toInt(), Color.argb(140, 255, 255, 255))
            }
        }
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "音乐播放器"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#182033"))
        }
        val subTitle = TextView(this).apply { tag = "music_sub_title"
            text = currentMusicTitle
            textSize = 12f
            setTextColor(Color.parseColor("#6F7A8C"))
            setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
        }
        headerTexts.addView(title)
        headerTexts.addView(subTitle)
        headerRow.addView(headerTexts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val closeButton = TextView(this).apply {
            text = "×"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5D6677"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(70, 30, 136, 229))
            }
            setOnClickListener { musicDialog?.dismiss() }
        }
        headerRow.addView(closeButton, LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()))
        panel.addView(headerRow)

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow)
        }
        panel.addView(tabScroll)

        val content = FrameLayout(this)
        val contentHeight = (resources.displayMetrics.heightPixels * 0.52f).toInt().coerceAtLeast((420 * density).toInt())
        panel.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, contentHeight).apply {
            topMargin = (12 * density).toInt()
        })

        fun showLocalTab() {
            musicPanelLastTab = MusicPanelTab.LOCAL
            content.removeAllViews()
            if (!hasAudioPermission()) {
                val items = listOf(
                    Triple("授权音乐", "读取手机歌曲") { requestAudioPermissionIfNeeded() },
                    Triple("用户歌曲", "APP内置音乐") {
                        playSelectedMusic("内置音乐 · 用户歌曲", defaultLocalSongUri())
                        subTitle.text = currentMusicTitle
                    }
                )
                content.addView(musicCardGrid(items), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val songs = loadLocalSongs()
                val items = if (songs.isEmpty()) {
                    listOf(Triple("用户歌曲", "APP内置音乐") {
                        playSelectedMusic("内置音乐 · 用户歌曲", defaultLocalSongUri())
                        subTitle.text = currentMusicTitle
                    })
                } else {
                    songs.map { song ->
                        Triple(song.title, "${song.artist} · ${formatDuration(song.durationMs)}") {
                            playSelectedMusic("本地音乐 · ${song.title}", song.uri)
                            subTitle.text = currentMusicTitle
                        }
                    }
                }
                content.addView(musicCardGrid(items), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }

        fun showFavoriteTab() {
            musicPanelLastTab = MusicPanelTab.FAVORITE
            content.removeAllViews()
            val favorites = loadMusicRecords(MUSIC_FAVORITES_KEY)
            if (favorites.isEmpty()) {
                val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                list.addView(makeHintText("暂无在线歌曲收藏。搜索网易云榜单 / 歌曲海后，点击“收藏”即可保存到这里。"))
                content.addView(ScrollView(this).apply { isFillViewport = true; addView(list) }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val items = favorites.map { record ->
                    Triple(record.title, record.artist.ifBlank { record.sourceLabel }) {
                        playOnlineRecord(record)
                        subTitle.text = currentMusicTitle
                    }
                }
                val loading = favorites.map { if (loadingOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                val current = favorites.map { if (currentOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                content.addView(musicCardGrid(items, loading, current), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }

        fun showOnlineMusicTab() {
            musicPanelLastTab = MusicPanelTab.ONLINE
            content.removeAllViews()

            val page = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
            }
            val listContainer = FrameLayout(this)

            fun replaceOnlineList(view: View) {
                listContainer.removeAllViews()
                listContainer.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            fun showOnlineHint(message: String) {
                val placeholder = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, (24 * density).toInt(), 0, 0)
                }
                placeholder.addView(TextView(this).apply {
                    text = message
                    textSize = 13f
                    setTextColor(Color.parseColor("#7B8494"))
                    gravity = Gravity.CENTER
                })
                replaceOnlineList(placeholder)
            }

            val searchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val input = android.widget.EditText(this).apply {
                hint = "搜索网易云榜单 / 歌曲海音乐..."
                setText(onlineLastKeyword)
                textSize = 14f
                isSingleLine = true
                setTextColor(Color.parseColor("#182033"))
                setHintTextColor(Color.parseColor("#A0A7B3"))
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 20f * density
                    setColor(Color.argb(120, 255, 255, 255))
                }
            }
            searchRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * density).toInt()
            })

            fun renderOnlineSongs(songs: List<com.yuno.tools.util.MusicSearchHelper.OnlineSong>) {
                if (songs.isEmpty()) {
                    val listArea = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                    }
                    listArea.addView(makeMusicRow("未搜索到结果", "可尝试更换关键词；部分结果可能暂无公开播放源", "", {}))
                    replaceOnlineList(ScrollView(this).apply {
                        isFillViewport = true
                        addView(listArea)
                    })
                    return
                }
                val records = songs.map { s ->
                    OnlineMusicRecord(
                        title = s.title,
                        artist = s.artist,
                        sourceLabel = s.source.label,
                        pageUrl = s.pageUrl,
                        playUrl = s.playUrl.orEmpty(),
                        savedAt = System.currentTimeMillis()
                    )
                }
                val items = records.map { record ->
                    Triple(record.title, record.artist.ifBlank { record.sourceLabel }) {
                        if (record.playUrl.isBlank()) {
                            Toast.makeText(this, "该歌曲暂无公开播放源", Toast.LENGTH_SHORT).show()
                        } else {
                            loadingOnlinePlayKey = musicRecordKey(record)
                            currentOnlinePlayKey = musicRecordKey(record)
                            renderOnlineSongs(songs)
                            playOnlineRecord(record)
                            subTitle.text = currentMusicTitle
                        }
                    }
                }
                val loading = records.map { if (loadingOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                val current = records.map { if (currentOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                replaceOnlineList(musicCardGrid(items, loading, current))
            }

            refreshOnlineMusicList = { renderOnlineSongs(onlineCachedSongs) }

            val searchButton = makeControlButton("搜索") { _ ->
                val keyword = input.text.toString().trim()
                onlineLastKeyword = keyword
                if (keyword.isBlank()) {
                    onlineCachedSongs = emptyList()
                    showOnlineHint("输入关键词搜索网易云榜单 / 歌曲海音乐")
                    return@makeControlButton
                }

                val loadingArea = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                }
                loadingArea.addView(makeHintText("正在搜索：$keyword"))
                replaceOnlineList(ScrollView(this).apply { addView(loadingArea) })

                com.yuno.tools.util.MusicSearchHelper.searchOnline(keyword) { songs ->
                    runOnUiThread {
                        onlineCachedSongs = songs
                        renderOnlineSongs(songs)
                    }
                }
            }
            searchRow.addView(searchButton)

            page.addView(searchRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            page.addView(listContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            content.addView(page, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            if (onlineCachedSongs.isNotEmpty()) {
                renderOnlineSongs(onlineCachedSongs)
            } else if (onlineLastKeyword.isNotBlank()) {
                searchButton.performClick()
            } else {
                showOnlineHint("输入关键词搜索网易云榜单 / 歌曲海音乐")
            }
        }

        tabRow.addView(makeMusicChip("本地音乐") { showLocalTab() })
        tabRow.addView(makeMusicChip("收藏") { showFavoriteTab() })
        tabRow.addView(makeMusicChip("在线音乐") { showOnlineMusicTab() })
        when (musicPanelLastTab) {
            MusicPanelTab.LOCAL -> showLocalTab()
            MusicPanelTab.FAVORITE -> showFavoriteTab()
            MusicPanelTab.ONLINE -> showOnlineMusicTab()
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        panel.addView(controlRow)
        val randomBtn = makeControlButton(if (musicShuffleEnabled) "随机：开" else "随机：关") {
            musicShuffleEnabled = !musicShuffleEnabled
            musicPlayer?.shuffleModeEnabled = musicShuffleEnabled
            (it as Button).text = if (musicShuffleEnabled) "随机：开" else "随机：关"
        }
        val loopBtn = makeControlButton(if (musicRepeatMode == Player.REPEAT_MODE_ONE) "循环：开" else "循环：关") {
            musicRepeatMode = if (musicRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            musicPlayer?.repeatMode = musicRepeatMode
            (it as Button).text = if (musicRepeatMode == Player.REPEAT_MODE_ONE) "循环：开" else "循环：关"
        }
        controlRow.addView(randomBtn)
        controlRow.addView(loopBtn)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
    }

    private fun musicCardGrid(items: List<Triple<String, String, () -> Unit>>, loadingKeys: List<String> = emptyList(), currentKeys: List<String> = emptyList()): ScrollView {
        val density = resources.displayMetrics.density
    val grid = GridLayout(this).apply {
        columnCount = 3
        setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
    }
    val dialogSafeWidth = (resources.displayMetrics.widthPixels - 64 * density).toInt().coerceAtLeast((300 * density).toInt())
    val gap = (4 * density).toInt()
    val cardSize = ((dialogSafeWidth - gap * 6) / 3).coerceAtMost((104 * density).toInt()).coerceAtLeast((82 * density).toInt())
    val coverSize = (cardSize * 0.58f).toInt()
    val playSize = (30 * density).toInt()
    items.forEachIndexed { index, item ->
        val titleText = item.first
        val subText = item.second
        val action = item.third
        val isLoading = loadingKeys.getOrNull(index) == "1"
        val isCurrent = currentKeys.getOrNull(index) == "1"
        val card = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cardSize
                height = (cardSize * 1.12f).toInt()
                setMargins(gap, gap, gap, gap)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(Color.argb(if (isCurrent) 220 else 185, 255, 255, 255))
                if (isCurrent) setStroke((1.4f * density).toInt(), Color.parseColor("#1E88E5"))
            }
            setOnClickListener { action() }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((5 * density).toInt(), (5 * density).toInt(), (5 * density).toInt(), (4 * density).toInt())
        }
        val coverBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(coverSize, coverSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 9f * density
                setColor(Color.argb(62, 30, 136, 229))
            }
        }
        coverBox.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_nav_music_disc)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#1E88E5"))
        }, FrameLayout.LayoutParams((coverSize * 0.58f).toInt(), (coverSize * 0.58f).toInt(), Gravity.CENTER))
        coverBox.addView(TextView(this).apply {
            text = when {
                isLoading -> "…"
                isCurrent && musicPlayer?.isPlaying == true -> "Ⅱ"
                else -> "▶"
            }
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(218, 30, 136, 229))
            }
            setOnClickListener { action() }
        }, FrameLayout.LayoutParams(playSize, playSize, Gravity.CENTER))
        inner.addView(coverBox)
        inner.addView(TextView(this).apply {
            text = titleText
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#182033"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, 0)
        })
        inner.addView(TextView(this).apply {
            text = subText
            textSize = 9f
            setTextColor(Color.parseColor("#6F7A8C"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        })
        card.addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        grid.addView(card)
    }
    return ScrollView(this).apply {
        isFillViewport = true
        addView(grid)
    }
}


    private fun makeMusicChip(text: String, action: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E88E5"))
            gravity = Gravity.CENTER
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.argb(42, 30, 136, 229))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = (8 * density).toInt()
            layoutParams = lp
            setOnClickListener { action() }
        }
    }

    private fun makeMusicRow(title: String, desc: String, buttonText: String, action: () -> Unit): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(Color.argb(180, 255, 255, 255))
            }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#182033"))
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor("#7B8494"))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(makeControlButton(buttonText) { action() })
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (10 * density).toInt()
        }
        return row
    }

    private fun makeOnlineSongRow(song: com.yuno.tools.util.MusicSearchHelper.OnlineSong, onChanged: (() -> Unit)? = null): View {
        val record = OnlineMusicRecord(
            title = song.title,
            artist = song.artist,
            sourceLabel = song.source.label,
            pageUrl = song.pageUrl,
            playUrl = song.playUrl.orEmpty()
        )
        val canPlay = record.playUrl.isNotBlank()
        val key = musicRecordKey(record)
        val isLoading = loadingOnlinePlayKey == key
        val isCurrent = currentOnlinePlayKey == key
        val status = when {
            isLoading -> " · 正在加载"
            isCurrent && musicPlayer?.isPlaying == true -> " · 正在播放"
            isCurrent -> " · 已选中"
            !canPlay -> " · 暂无公开播放源"
            else -> ""
        }
        val desc = record.sourceLabel + " · " + record.artist + status
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        val playLabel = when {
            !canPlay -> "不可播"
            isLoading -> "加载中…"
            isCurrent && musicPlayer?.isPlaying == true -> "播放中"
            else -> "播放"
        }
        actions += playLabel to {
            if (!canPlay) {
                Toast.makeText(this, "该歌曲暂无公开播放源，不能播放或下载", Toast.LENGTH_SHORT).show()
            } else {
                loadingOnlinePlayKey = key
                currentOnlinePlayKey = key
                onChanged?.invoke()
                playOnlineRecord(record)
            }
        }
        actions += (if (isMusicFavorite(record)) "已收藏" else "收藏") to {
            toggleMusicFavorite(record)
            Toast.makeText(this, if (isMusicFavorite(record)) "已收藏：${record.title}" else "已取消收藏：${record.title}", Toast.LENGTH_SHORT).show()
            onChanged?.invoke()
        }
        actions += "下载" to { downloadOnlineSong(record) }
        return makeMusicActionRow(record.title, desc, actions)
    }

    private fun makeOnlineRecordRow(record: OnlineMusicRecord, showDownload: Boolean, onChanged: () -> Unit, playAction: () -> Unit): View {
        val pathText = if (record.localPath.isNotBlank()) " · ${record.localPath}" else ""
        val desc = record.sourceLabel + " · " + record.artist + pathText
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += "播放" to playAction
        actions += "取消" to {
            removeMusicRecord(MUSIC_FAVORITES_KEY, record)
            Toast.makeText(this, "已取消收藏：${record.title}", Toast.LENGTH_SHORT).show()
            onChanged()
        }
        if (showDownload) actions += "下载" to { downloadOnlineSong(record) }
        return makeMusicActionRow(record.title, desc, actions)
    }

    private fun makeMusicActionRow(title: String, desc: String, actions: List<Pair<String, () -> Unit>>): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(Color.argb(180, 255, 255, 255))
            }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#182033"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor("#7B8494"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(texts, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        actions.forEach { (label, action) ->
            buttons.addView(makeControlButton(label) { action() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            })
        }
        row.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (10 * density).toInt()
        }
        return row
    }

    private fun makeHintText(text: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#7B8494"))
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), 0)
        }
    }

    private fun makeControlButton(text: String, action: (View) -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.parseColor("#1E88E5"))
            }
            minHeight = 0
            minimumHeight = 0
            setPadding((12 * density).toInt(), (7 * density).toInt(), (12 * density).toInt(), (7 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            }
            setOnClickListener { action(it) }
        }
    }

    private fun musicPrefs() = getSharedPreferences(MUSIC_PREFS, Context.MODE_PRIVATE)

    private fun loadMusicRecords(key: String): MutableList<OnlineMusicRecord> {
        val raw = musicPrefs().getString(key, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                OnlineMusicRecord(
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    sourceLabel = obj.optString("sourceLabel"),
                    pageUrl = obj.optString("pageUrl"),
                    playUrl = obj.optString("playUrl"),
                    localPath = obj.optString("localPath"),
                    savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                )
            }.filter { it.title.isNotBlank() }.toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun saveMusicRecords(key: String, records: List<OnlineMusicRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("title", r.title)
                put("artist", r.artist)
                put("sourceLabel", r.sourceLabel)
                put("pageUrl", r.pageUrl)
                put("playUrl", r.playUrl)
                put("localPath", r.localPath)
                put("savedAt", r.savedAt)
            })
        }
        musicPrefs().edit().putString(key, arr.toString()).apply()
    }

    private fun sameMusicRecord(a: OnlineMusicRecord, b: OnlineMusicRecord): Boolean {
        return a.pageUrl == b.pageUrl || (a.title == b.title && a.artist == b.artist && a.sourceLabel == b.sourceLabel)
    }

    private fun isMusicFavorite(record: OnlineMusicRecord): Boolean = loadMusicRecords(MUSIC_FAVORITES_KEY).any { sameMusicRecord(it, record) }

    private fun toggleMusicFavorite(record: OnlineMusicRecord) {
        val records = loadMusicRecords(MUSIC_FAVORITES_KEY)
        val existed = records.any { sameMusicRecord(it, record) }
        val updated = if (existed) records.filterNot { sameMusicRecord(it, record) } else listOf(record.copy(savedAt = System.currentTimeMillis())) + records
        saveMusicRecords(MUSIC_FAVORITES_KEY, updated)
    }

    private fun removeMusicRecord(key: String, record: OnlineMusicRecord) {
        saveMusicRecords(key, loadMusicRecords(key).filterNot { sameMusicRecord(it, record) })
    }

    private fun musicRecordKey(record: OnlineMusicRecord): String {
        return record.sourceLabel + "|" + record.pageUrl + "|" + record.title + "|" + record.artist
    }

    private fun playOnlineRecord(record: OnlineMusicRecord) {
        val target = if (record.localPath.isNotBlank()) Uri.fromFile(File(record.localPath)) else com.yuno.tools.util.MusicSearchHelper.uriFromPublicUrl(record.playUrl)
        updateMusicPlaylist()
        currentMusicIndex = musicPlaylist.indexOfFirst { sameMusicRecord(it, record) }
        playSelectedMusic(record.sourceLabel + " · " + record.title, target, musicRecordKey(record))
    }

    private fun downloadOnlineSong(record: OnlineMusicRecord) {
        if (record.playUrl.isBlank()) {
            Toast.makeText(this, "该歌曲暂无公开播放源，不能下载", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "开始下载：${record.title}", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                val dir = File(getExternalFilesDir(null), "Music").apply { mkdirs() }
                val base = safeFileName(record.title.ifBlank { "online_music" })
                val ext = record.playUrl.substringBefore('?').substringAfterLast('.', "mp3").lowercase().takeIf { it.length in 2..5 } ?: "mp3"
                var out = File(dir, "$base.$ext")
                var index = 1
                while (out.exists()) {
                    out = File(dir, "$base-$index.$ext")
                    index++
                }
                val conn = (URL(record.playUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) error("HTTP $code")
                    conn.inputStream.use { input -> FileOutputStream(out).use { output -> input.copyTo(output) } }
                } finally {
                    conn.disconnect()
                }
                if (!out.exists() || out.length() <= 0L) error("文件为空")
                val saved = record.copy(localPath = out.absolutePath, savedAt = System.currentTimeMillis())
                val downloads = loadMusicRecords(MUSIC_DOWNLOADS_KEY).filterNot { sameMusicRecord(it, saved) }.toMutableList()
                downloads.add(0, saved)
                saveMusicRecords(MUSIC_DOWNLOADS_KEY, downloads)
                out.absolutePath
            }
            runOnUiThread {
                result.onSuccess { Toast.makeText(this, "下载完成：$it", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "下载失败：${it.message ?: "网络或文件异常"}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun safeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim().take(60).ifBlank { "online_music" }
    }

    private fun miniNavLyricText(): String {
        val title = currentMusicTitle.substringAfter(" · ", currentMusicTitle).ifBlank { "正在播放" }
        return "♪ $title   ♪ $title   ♪ $title"
    }

    private fun updateMusicNavState(isPlaying: Boolean) {
        val selectedColor = Color.parseColor("#1E88E5")
        val normalColor = Color.parseColor("#A0A7B3")
        val icon = runCatching { findViewById<ImageView>(R.id.ivNavMusicDisc) }.getOrNull() ?: return
        val text = runCatching { findViewById<TextView>(R.id.tvNavMusic) }.getOrNull()
        val lyric = runCatching { findViewById<TextView>(R.id.tvNavMusicLyric) }.getOrNull()
        val color = if (isPlaying) selectedColor else normalColor
        icon.imageTintList = ColorStateList.valueOf(color)
        text?.apply {
            setTextColor(color)
            this.text = if (isPlaying) "播放中" else "播放"
            setTypeface(null, if (isPlaying) Typeface.BOLD else Typeface.NORMAL)
            animate().alpha(if (isPlaying) 1f else 0.72f).setDuration(160L).start()
        }
        lyric?.apply {
            this.text = if (isPlaying) miniNavLyricText() else ""
            isSelected = isPlaying
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            setTextColor(if (isPlaying) Color.parseColor("#6F7DFF") else Color.TRANSPARENT)
        }
        if (isPlaying) {
            startMusicSpin(icon)
        } else {
            stopMusicSpin(icon)
        }
    }

    private fun startMusicSpin(icon: ImageView) {
        if (musicSpinAnimator?.isStarted == true) return
        musicSpinAnimator?.cancel()
        musicSpinAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, icon.rotation, icon.rotation + 360f).apply {
            duration = 1400L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopMusicSpin(icon: ImageView) {
        musicSpinAnimator?.cancel()
        musicSpinAnimator = null
        icon.animate().rotation(0f).setDuration(180L).start()
    }

    private fun releaseMusicPlayer() {
        musicDialog?.dismiss()
        musicDialog = null
        musicSpinAnimator?.cancel()
        musicSpinAnimator = null
        musicPlayer?.release()
        musicPlayer = null
        runCatching {
            val icon = findViewById<ImageView>(R.id.ivNavMusicDisc)
            icon.rotation = 0f
            updateMusicNavState(isPlaying = false)
            updateMusicNotification(false)
        }
    }



    private fun ensureMusicNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                MUSIC_NOTIFICATION_CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "YunoTools 当前播放音乐"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateMusicNotification(isPlaying: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!isPlaying) {
            manager.cancel(MUSIC_NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureMusicNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = currentMusicTitle.substringBefore(" · ", currentMusicTitle)
        val text = currentMusicTitle.substringAfter(" · ", "正在播放")
        val notification = NotificationCompat.Builder(this, MUSIC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_music_disc)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(MUSIC_NOTIFICATION_ID, notification)
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
        releaseMusicPlayer()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()}
