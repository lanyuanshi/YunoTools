package com.yuno.tools

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.DragEvent
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
import android.widget.ViewFlipper
import android.widget.Toast
import android.widget.EditText
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import androidx.core.view.children
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.session.MediaSession
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.data.AccountStore
import com.yuno.tools.ui.video.VideoParseActivity
import com.yuno.tools.ui.tools.AIChatActivity
import com.yuno.tools.ui.image.ImageCompressActivity
import com.yuno.tools.ui.image.QRCodeActivity
import com.yuno.tools.ui.image.GridCropActivity
import com.yuno.tools.ui.media.AudioSeparateActivity
import com.yuno.tools.ui.media.VideoTrimActivity
import com.yuno.tools.ui.tools.AnimeSearchActivity
import com.yuno.tools.ui.tools.BangumiWatchActivity
import com.yuno.tools.ui.tools.ExpressQueryActivity
import com.yuno.tools.ui.tools.WallpaperToolActivity
import com.yuno.tools.ui.tools.Base64ToolActivity
import com.yuno.tools.ui.tools.BarrageActivity
import com.yuno.tools.ui.tools.ClockActivity
import com.yuno.tools.ui.tools.SubscriptionActivity
import com.yuno.tools.ui.tools.CalculatorActivity
import com.yuno.tools.ui.tools.LevelToolActivity
import com.yuno.tools.ui.tools.CompassToolActivity
import com.yuno.tools.ui.tools.VibratorToolActivity
import com.yuno.tools.ui.tools.MetalDetectorActivity
import com.yuno.tools.ui.tools.WoodenFishActivity
import com.yuno.tools.ui.tools.MagicCubeActivity
import com.yuno.tools.ui.tools.DinoRunActivity
import com.yuno.tools.ui.tools.PokiGamesActivity
import com.yuno.tools.ui.tools.TranslateActivity
import com.yuno.tools.ui.tools.HeisiImageActivity
import com.yuno.tools.ui.tools.MovieWarehouseActivity
import com.yuno.tools.ui.tools.GachaAnalysisActivity
import com.yuno.tools.ui.profile.MusicDownloadsActivity
import com.yuno.tools.ui.profile.MemberCenterActivity
import com.yuno.tools.ui.profile.MemberZoneActivity
import com.yuno.tools.ui.profile.ProfileActivity
import com.yuno.tools.ui.profile.ParseHistoryActivity
import com.yuno.tools.ui.profile.SettingsActivity
import com.yuno.tools.util.ThemeApplier
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    companion object {
        private const val MUSIC_NOTIFICATION_CHANNEL_ID = "yuno_music_playback"
        private const val MUSIC_NOTIFICATION_ID = 71072
        private const val MUSIC_PREFS = "yuno_music_records"
        private const val MUSIC_FAVORITES_KEY = "online_favorites"
        private const val MUSIC_DOWNLOADS_KEY = "online_downloads"
        private const val GRID_ORDER_PREFS = "yuno_grid_order"
        private const val LYRIC_SYNC_LEAD_MS = 500L
        private const val FAVORITE_PLAY_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
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
        val songId: String = "",
        val localPath: String = "",
        val savedAt: Long = System.currentTimeMillis()
    )
    private data class TimedLyricLine(val timeMs: Long, val text: String)

    private var currentTab = MainTab.HOME
    private var avatarPlayer: ExoPlayer? = null
    private var titleAvatarPlayer: ExoPlayer? = null
    private var profileEntryAvatarPlayer: ExoPlayer? = null
    private var musicPlayer: ExoPlayer? = null
    private var musicMediaSession: MediaSession? = null
    private var musicSpinAnimator: ObjectAnimator? = null
    private var navBarsAnimator: ValueAnimator? = null
    private var cardBarsAnimator: ValueAnimator? = null
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
    private var currentLyricsKey: String? = null
    private var currentLyricsText = "歌词将在播放酷我歌曲后显示"
    private var currentTimedLyrics: List<TimedLyricLine> = emptyList()
    private var currentLyricIndex = -1
    private val lyricsHandler = Handler(Looper.getMainLooper())
    private val lyricsTicker = object : Runnable {
        override fun run() {
            updateFlowingLyrics()
            lyricsHandler.postDelayed(this, 16L)
        }
    }
    private var refreshLyricsView: ((CharSequence) -> Unit)? = null
    private var refreshLyricBeatView: ((CharSequence) -> Unit)? = null
    private var refreshKaraokeLyricsView: (() -> Unit)? = null
    private var bounceLyricsView: (() -> Unit)? = null
    private var refreshPlayerPanelState: (() -> Unit)? = null
    private var refreshMusicProgressView: (() -> Unit)? = null
    private val musicProgressHandler = Handler(Looper.getMainLooper())
    private val musicProgressTicker = object : Runnable {
        override fun run() {
            refreshMusicProgressView?.invoke()
            musicProgressHandler.postDelayed(this, 33L)
        }
    }
    private var refreshOnlineMusicList: (() -> Unit)? = null
    private var pickedLocalSongs: List<LocalSong> = emptyList()
    private var homeRandomBannerUrl: String? = null

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showMusicPanel()
    }

    private val pickMusic = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        UserSettingsStore.persistUriPermission(this, uri)
        val name = resolveAudioDisplayName(uri)
        val song = LocalSong(name, "手动添加", uri, 0L)
        pickedLocalSongs = (listOf(song) + pickedLocalSongs).distinctBy { it.uri.toString() }
        playSelectedMusic("添加歌曲 · $name", uri, null)
        showMusicPanel()
    }

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        UserSettingsStore.persistUriPermission(this, uri)
        UserSettingsStore.setAvatarUri(this, uri.toString())
        loadAvatar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullscreenRendering(lightStatusBar = true)
        setContentView(R.layout.activity_main)
        ThemeApplier.apply(this)
        setupHomeFullscreenInsets()

        bindHomeCards()
        setupHomeBannerCarousel()
        setupMoreToolsCollapse()
        setupHomeGridPersonalization()
        applyGlassHomeCards()
        bindProfilePage()
        bindBottomNav()
        lyricsHandler.post(lyricsTicker)
        musicProgressHandler.post(musicProgressTicker)
        showHome(animate = false)
    }

    private fun enableFullscreenRendering(lightStatusBar: Boolean) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (lightStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (lightStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun setupHomeFullscreenInsets() {
        val status = statusBarHeight()
        findViewById<View>(R.id.statusBarPlaceholder).layoutParams = findViewById<View>(R.id.statusBarPlaceholder).layoutParams.apply { height = 0 }
        findViewById<LinearLayout>(R.id.homeTitleBar).apply {
            layoutParams = layoutParams.apply { height = dp(68) + status }
            setPadding(paddingLeft, status + dp(6), paddingRight, paddingBottom)
        }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }


    private fun applyGlassHomeCards() {
        val root = findViewById<View>(R.id.mainRoot)
        root.setBackgroundColor(Color.parseColor("#EEF2F7"))
        applyGlassIn(root)
    }

    private fun applyGlassIn(view: View) {
        if (view is MaterialCardView) {
            view.setCardBackgroundColor(Color.parseColor("#CCFFFFFF"))
            view.cardElevation = 8f * resources.displayMetrics.density
            view.radius = 18f * resources.displayMetrics.density
            view.strokeWidth = (1f * resources.displayMetrics.density).roundToInt()
            view.strokeColor = Color.parseColor("#88FFFFFF")
            view.alpha = 0.96f
        }
        if (view is android.view.ViewGroup) {
            view.children.forEach { applyGlassIn(it) }
        }
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
        findViewById<MaterialCardView>(R.id.cardBase64Tool).setOnClickListener {
            startActivity(Intent(this, Base64ToolActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardWallpaperTool).setOnClickListener {
            startActivity(Intent(this, WallpaperToolActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMemberZone).setOnClickListener {
            startActivity(Intent(this, MemberZoneActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardCalculatorTool).setOnClickListener {
            startActivity(Intent(this, CalculatorActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardLevelTool).setOnClickListener {
            startActivity(Intent(this, LevelToolActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardCompassTool).setOnClickListener {
            startActivity(Intent(this, CompassToolActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardVibratorTool).setOnClickListener {
            startActivity(Intent(this, VibratorToolActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMetalDetector).setOnClickListener {
            startActivity(Intent(this, MetalDetectorActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardWoodenFish).setOnClickListener {
            startActivity(Intent(this, WoodenFishActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardHeisiTool).setOnClickListener {
            startActivity(Intent(this, HeisiImageActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardTranslateTool).setOnClickListener {
            startActivity(Intent(this, TranslateActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMovieWarehouse).setOnClickListener {
            startActivity(Intent(this, MovieWarehouseActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardGachaAnalysis).setOnClickListener {
            startActivity(Intent(this, GachaAnalysisActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMagicCube).setOnClickListener {
            startActivity(Intent(this, MagicCubeActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardDinoRun).setOnClickListener {
            startActivity(Intent(this, DinoRunActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardPokiGames).setOnClickListener {
            startActivity(Intent(this, PokiGamesActivity::class.java))
        }
    }

    private fun setupMoreToolsCollapse() {
        val header = findViewById<LinearLayout>(R.id.moreToolsHeader)
        val grid = findViewById<GridLayout>(R.id.gridMoreTools)
        val arrow = findViewById<TextView>(R.id.tvMoreToolsArrow)
        var expanded = true
        header.setOnClickListener {
            expanded = !expanded
            arrow.text = if (expanded) "⌄" else "›"
            if (expanded) {
                grid.visibility = View.VISIBLE
                grid.alpha = 0f
                grid.translationY = -8f * resources.displayMetrics.density
                grid.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            } else {
                grid.animate().alpha(0f).translationY(-8f * resources.displayMetrics.density).setDuration(140L).withEndAction {
                    if (!expanded) grid.visibility = View.GONE
                }.start()
            }
        }
    }


    private fun setupHomeGridPersonalization() {
        setupGridDragSort("image", findViewById(R.id.gridImageTools))
        setupGridDragSort("media", findViewById(R.id.gridMediaTools))
        setupGridDragSort("more", findViewById(R.id.gridMoreTools))
        setupGridDragSort("daily", findViewById(R.id.gridDailyTools))
        setupGridDragSort("games", findViewById(R.id.gridMiniGames))
    }

    private fun setupGridDragSort(key: String, grid: GridLayout) {
        restoreGridOrder(key, grid)
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            child.tag = child.id
            child.setOnLongClickListener {
                it.animate().scaleX(1.08f).scaleY(1.08f).alpha(0.82f).setDuration(120L).start()
                val label = resources.getResourceEntryName(it.id)
                val data = ClipData.newPlainText("grid_card", label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    it.startDragAndDrop(data, View.DragShadowBuilder(it), it, 0)
                } else {
                    @Suppress("DEPRECATION")
                    it.startDrag(data, View.DragShadowBuilder(it), it, 0)
                }
                Toast.makeText(this, "拖动到目标位置即可交换", Toast.LENGTH_SHORT).show()
                true
            }
            child.setOnDragListener { target, event ->
                val dragged = event.localState as? View ?: return@setOnDragListener true
                when (event.action) {
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        if (target !== dragged) target.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        target.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
                    }
                    DragEvent.ACTION_DROP -> {
                        if (target !== dragged && target.parent === grid && dragged.parent === grid) {
                            swapGridChildren(grid, dragged, target)
                            saveGridOrder(key, grid)
                        }
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        dragged.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120L).start()
                        for (j in 0 until grid.childCount) grid.getChildAt(j).animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120L).start()
                    }
                }
                true
            }
        }
    }

    private fun swapGridChildren(grid: GridLayout, first: View, second: View) {
        val firstIndex = grid.indexOfChild(first)
        val secondIndex = grid.indexOfChild(second)
        if (firstIndex < 0 || secondIndex < 0 || firstIndex == secondIndex) return
        grid.removeView(first)
        grid.removeView(second)
        if (firstIndex < secondIndex) {
            grid.addView(second, firstIndex)
            grid.addView(first, secondIndex)
        } else {
            grid.addView(first, secondIndex)
            grid.addView(second, firstIndex)
        }
    }

    private fun saveGridOrder(key: String, grid: GridLayout) {
        val order = (0 until grid.childCount).joinToString(",") { resources.getResourceEntryName(grid.getChildAt(it).id) }
        getSharedPreferences(GRID_ORDER_PREFS, Context.MODE_PRIVATE).edit().putString(key, order).apply()
        Toast.makeText(this, "排序已保存", Toast.LENGTH_SHORT).show()
    }

    private fun restoreGridOrder(key: String, grid: GridLayout) {
        val order = getSharedPreferences(GRID_ORDER_PREFS, Context.MODE_PRIVATE).getString(key, "").orEmpty()
        if (order.isBlank()) return
        val wanted = order.split(",").filter { it.isNotBlank() }
        val views = mutableMapOf<String, View>()
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            views[resources.getResourceEntryName(child.id)] = child
        }
        val sorted = mutableListOf<View>()
        wanted.forEach { views.remove(it)?.let(sorted::add) }
        sorted.addAll(views.values)
        grid.removeAllViews()
        sorted.forEach { grid.addView(it) }
    }

    private fun bindProfilePage() {
        val profileHeader = runCatching { findViewById<MaterialCardView>(R.id.cardAvatarHeader) }.getOrNull()
        profileHeader?.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardParseHistory).setOnClickListener {
            startActivity(Intent(this, ParseHistoryActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMusicDownloads).setOnClickListener {
            startActivity(Intent(this, MusicDownloadsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardTitleProfile).setOnClickListener {
            showProfile()
        }
        updateHomeProfileEntry()
    }

    private fun setupHomeBannerCarousel() {
        val flipper = findViewById<ViewFlipper>(R.id.homeBannerFlipper)
        flipper.displayedChild = 0
        flipper.stopFlipping()
        findViewById<MaterialCardView>(R.id.bannerRandomImage).setOnClickListener {
            loadHomeRandomBanner()
            toast("正在换一张图片")
        }
        findViewById<MaterialCardView>(R.id.bannerRandomImage).setOnLongClickListener {
            showHomeBannerActions()
            true
        }
        if (homeRandomBannerUrl.isNullOrBlank()) {
            loadHomeRandomBanner()
        }
    }

    private fun showHomeBannerActions() {
        val url = homeRandomBannerUrl.orEmpty()
        if (url.isBlank()) {
            toast("当前没有可操作的原图")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("轮播图操作")
            .setItems(arrayOf("保存图片", "查看原图")) { _, which ->
                when (which) {
                    0 -> saveHomeBannerImage(url)
                    1 -> openHomeBannerOriginal(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openHomeBannerOriginal(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            toast("无法打开原图")
        }
    }

    private fun saveHomeBannerImage(url: String) {
        toast("正在保存轮播图")
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools")
                    setRequestProperty("Accept", "image/*,*/*")
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                val mime = conn.contentType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                conn.disconnect()
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "YunoTools_Banner_${System.currentTimeMillis()}.$ext")
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YunoTools")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("创建媒体文件失败")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("写入失败")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
            }
            withContext(Dispatchers.Main) {
                toast(if (result.isSuccess) "轮播图已保存到相册" else "保存失败")
            }
        }
    }

    private fun loadHomeRandomBanner() {
        val banner = findViewById<ImageView>(R.id.ivHomeRandomBanner)
        if (banner == null) return
        banner.post {
            banner.imageTintList = null
            banner.clearColorFilter()
            Glide.with(banner).clear(banner)
            banner.setImageDrawable(null)
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = (URL("https://t.alcy.cc/json?pc").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools")
                    setRequestProperty("Accept", "application/json,*/*")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val data = json.optJSONObject("data")
                val link = data?.optString("link").orEmpty().trim()
                if (link.isNotBlank()) {
                    homeRandomBannerUrl = link
                    withContext(Dispatchers.Main) {
                        val iv = findViewById<ImageView>(R.id.ivHomeRandomBanner)
                        if (iv != null) {
                            iv.imageTintList = null
                            iv.clearColorFilter()
                            Glide.with(iv)
                                .load(link)
                                .signature(ObjectKey(link + "#banner#" + System.currentTimeMillis()))
                                .skipMemoryCache(true)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .dontAnimate()
                                .centerCrop()
                                .placeholder(R.drawable.bg_banner_random)
                                .error(R.drawable.bg_banner_random)
                                .into(iv)
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    val iv = findViewById<ImageView>(R.id.ivHomeRandomBanner)
                    iv?.setImageResource(R.drawable.bg_banner_random)
                }
            }
        }
    }

    private fun updateProfileEntry() {
        val state = AccountStore.state(this)
        runCatching {
            findViewById<TextView>(R.id.tvProfileEntryTitle).text = if (state.loggedIn) state.nickname.ifBlank { state.username } else "个人页"
            findViewById<TextView>(R.id.tvProfileEntryHint).text = if (state.loggedIn) {
                if (state.isVip) "VIP会员 · ${AccountStore.vipText(state)} · ${state.points}积分" else "普通用户 · ${state.points}积分 · 进入管理账号"
            } else "进入后管理头像、登录、会员、签到"
            val iv = findViewById<ImageView>(R.id.ivProfileEntryAvatar)
            val pv = findViewById<PlayerView>(R.id.pvProfileEntryAvatar)
            iv.post { renderAvatarInto(iv, pv, 18, AvatarSlot.PROFILE_ENTRY) }
        }
    }

    private fun updateHomeProfileEntry() {
        runCatching {
            val iv = findViewById<ImageView>(R.id.ivTitleAvatar)
            val pv = findViewById<PlayerView>(R.id.pvTitleAvatar)
            iv.post { renderAvatarInto(iv, pv, 14, AvatarSlot.TITLE) }
        }
    }

    private enum class AvatarSlot { TITLE, PROFILE_ENTRY, PERSONAL }

    private fun renderAvatarInto(iv: ImageView, pv: PlayerView?, defaultPaddingDp: Int, slot: AvatarSlot) {
        val uriText = UserSettingsStore.getAvatarUri(this)
        iv.imageTintList = null
        iv.clearColorFilter()
        Glide.with(iv).clear(iv)
        if (uriText.isBlank()) {
            releaseAvatarPlayer(slot)
            pv?.visibility = View.GONE
            iv.visibility = View.VISIBLE
            iv.setImageResource(R.drawable.ic_profile)
            iv.imageTintList = ColorStateList.valueOf(Color.WHITE)
            iv.setPadding(dp(defaultPaddingDp), dp(defaultPaddingDp), dp(defaultPaddingDp), dp(defaultPaddingDp))
            iv.scaleType = ImageView.ScaleType.CENTER
            return
        }
        val uri = Uri.parse(uriText)
        val isVideo = runCatching { contentResolver.getType(uri)?.startsWith("video/") == true }.getOrDefault(false)
        if (isVideo && pv != null) {
            releaseAvatarPlayer(slot)
            iv.visibility = View.GONE
            pv.visibility = View.VISIBLE
            val player = ExoPlayer.Builder(this).build().also { player ->
                player.volume = 0f
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.setMediaItem(MediaItem.fromUri(uri))
                pv.player = player
                player.prepare()
                player.playWhenReady = true
            }
            setAvatarPlayer(slot, player)
        } else {
            releaseAvatarPlayer(slot)
            pv?.visibility = View.GONE
            iv.visibility = View.VISIBLE
            iv.setPadding(0, 0, 0, 0)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(iv)
                .load(uri)
                .signature(ObjectKey(uriText + "#" + slot.name + "#" + System.currentTimeMillis()))
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .circleCrop()
                .dontAnimate()
                .placeholder(R.drawable.bg_circle_blue)
                .error(R.drawable.ic_profile)
                .into(iv)
        }
    }

    private fun setAvatarPlayer(slot: AvatarSlot, player: ExoPlayer?) {
        when (slot) {
            AvatarSlot.TITLE -> titleAvatarPlayer = player
            AvatarSlot.PROFILE_ENTRY -> profileEntryAvatarPlayer = player
            AvatarSlot.PERSONAL -> avatarPlayer = player
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
        findViewById<View>(R.id.cardTitleProfile).visibility = View.VISIBLE
        updateHomeProfileEntry()
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
        releaseAvatarPlayer()
        val home = findViewById<View>(R.id.scrollView)
        val profile = findViewById<View>(R.id.profilePage)
        home.animate().cancel()
        profile.animate().cancel()
        home.visibility = View.GONE
        profile.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvMainTitle).text = "个人资料"
        // 个人资料页不显示右上角头像入口，避免和页面内“进入个人页”入口重复
        findViewById<View>(R.id.cardTitleProfile).visibility = View.GONE
        profile.alpha = 0f
        profile.translationY = resources.displayMetrics.density * 16f
        profile.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        updateNavSelection(MainTab.PROFILE, animate = true)
        updateProfileEntry()
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
        updateMusicPlaylist()
        if (musicPlaylist.isEmpty()) return
        val nextIndex = if (musicShuffleEnabled) {
            (0 until musicPlaylist.size).random()
        } else {
            val current = currentMusicIndex.takeIf { it >= 0 } ?: musicPlaylist.indexOfFirst { musicRecordKey(it) == currentOnlinePlayKey }
            if (current < 0) 0 else (current + 1) % musicPlaylist.size
        }
        currentMusicIndex = nextIndex
        playOnlineRecord(musicPlaylist[nextIndex])
    }

    private fun playPreviousMusic() {
        updateMusicPlaylist()
        if (musicPlaylist.isEmpty()) return
        val current = currentMusicIndex.takeIf { it >= 0 } ?: musicPlaylist.indexOfFirst { musicRecordKey(it) == currentOnlinePlayKey }
        val prevIndex = if (current <= 0) musicPlaylist.size - 1 else current - 1
        currentMusicIndex = prevIndex
        playOnlineRecord(musicPlaylist[prevIndex])
    }

    private fun toggleCurrentMusicPlayback() {
        val player = musicPlayer ?: return
        if (player.mediaItemCount == 0 || currentMusicUri == null) {
            playLocalMusicFromPanel()
            return
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            player.playWhenReady = true
            player.play()
        }
        updateMusicNavState(player.isPlaying)
        updateMusicNotification(player.isPlaying)
    }

    private fun updateMusicPlaylist() {
        musicPlaylist = when (musicPanelLastTab) {
            MusicPanelTab.FAVORITE -> loadMusicRecords(MUSIC_FAVORITES_KEY)
            MusicPanelTab.LOCAL -> pickedLocalSongs.map { song ->
                OnlineMusicRecord(
                    title = song.title,
                    artist = song.artist,
                    sourceLabel = "本地添加",
                    pageUrl = song.uri.toString(),
                    playUrl = song.uri.toString(),
                    songId = ""
                )
            }
            MusicPanelTab.ONLINE -> onlineCachedSongs.map { song ->
                OnlineMusicRecord(
                    title = song.title,
                    artist = song.artist,
                    sourceLabel = song.source.label,
                    pageUrl = song.pageUrl,
                    playUrl = song.playUrl.orEmpty(),
                    songId = song.songId,
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
            ensureMusicMediaSession(created)
            created.repeatMode = musicRepeatMode
            created.shuffleModeEnabled = musicShuffleEnabled
            val songUri = currentMusicUri ?: defaultLocalSongUri()
            created.setMediaItem(buildMusicMediaItem(currentMusicTitle, songUri))
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateMusicNavState(isPlaying)
                    refreshPlayerPanelState?.invoke()
                    updateMusicNotification(isPlaying)
                    if (isPlaying && loadingOnlinePlayKey != null) {
                        loadingOnlinePlayKey = null
                        refreshOnlineMusicList?.invoke()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    refreshPlayerPanelState?.invoke()
                    updateMusicNotification(musicPlayer?.isPlaying == true)
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
                    updateMusicNotification(false)
                    refreshOnlineMusicList?.invoke()
                    Toast.makeText(this@MainActivity, "播放失败：$failedTitle，可能是版权限制或临时链接失效", Toast.LENGTH_LONG).show()
                }
            })
            created.prepare()
        }
    }

    private fun ensureMusicMediaSession(player: ExoPlayer): MediaSession {
        return musicMediaSession ?: MediaSession.Builder(this, player)
            .setId("YunoToolsMusicSession")
            .build()
            .also { musicMediaSession = it }
    }

    private fun buildMusicMediaItem(title: String, uri: Uri): MediaItem {
        val songTitle = title.substringAfter(" · ", title).ifBlank { title }
        val artist = title.substringBefore(" · ", "YunoTools").ifBlank { "YunoTools" }
        val metadata = MediaMetadata.Builder()
            .setTitle(songTitle)
            .setArtist(artist)
            .setAlbumTitle("YunoTools 音乐")
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(metadata)
            .build()
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
        if (onlineKey == null) {
            currentLyricsKey = null
            currentTimedLyrics = emptyList()
            currentLyricIndex = -1
            currentLyricsText = "本地歌曲暂无在线歌词"
            refreshLyricsView?.invoke(currentLyricsText)
        }
        val player = ensureMusicPlayer()
        player.stop()
        player.clearMediaItems()
        player.repeatMode = musicRepeatMode
        player.shuffleModeEnabled = musicShuffleEnabled
        player.setMediaItem(buildMusicMediaItem(title, uri))
        ensureMusicMediaSession(player)
        updateMusicNotification(false)
        player.prepare()
        player.playWhenReady = true
        player.play()
        updateMusicNavState(true)
        updateMusicNotification(true)
        refreshPlayerPanelState?.invoke()
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

    private fun addLocalMusicFromPicker() {
        pickMusic.launch(arrayOf("audio/*"))
    }

    private fun resolveAudioDisplayName(uri: Uri): String {
        val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
        return runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()?.substringBeforeLast('.')?.ifBlank { null } ?: "手动添加歌曲"
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
            setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
            setBackgroundColor(Color.argb(72, 0, 0, 0))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 32f * density
                setColor(Color.argb(244, 250, 250, 252))
                setStroke((1f * density).toInt(), Color.argb(210, 255, 255, 255))
            }
        }
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "音乐"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        }
        val subTitle = TextView(this).apply { tag = "music_sub_title"
            text = currentMusicTitle
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
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
            setTextColor(Color.parseColor("#8E8E93"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(46, 118, 118, 128))
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
        val contentHeight = (resources.displayMetrics.heightPixels * 0.43f).toInt().coerceAtLeast((320 * density).toInt())
        panel.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, contentHeight).apply {
            topMargin = (12 * density).toInt()
        })

        fun showLocalTab() {
            musicPanelLastTab = MusicPanelTab.LOCAL
            content.removeAllViews()
            if (!hasAudioPermission()) {
                val items = listOf(
                    Triple("添加歌曲", "选择音频文件") { addLocalMusicFromPicker() },
                    Triple("授权音乐", "读取手机歌曲") { requestAudioPermissionIfNeeded() }
                )
                content.addView(musicCardGrid(items), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val songs = pickedLocalSongs + loadLocalSongs().filterNot { scanned -> pickedLocalSongs.any { it.uri == scanned.uri } }
                val baseItems = listOf(
                    Triple("添加歌曲", "选择音频文件") { addLocalMusicFromPicker() }
                )
                val items = baseItems + songs.map { song ->
                    Triple(song.title, "${song.artist} · ${formatDuration(song.durationMs)}") {
                        playSelectedMusic("本地音乐 · ${song.title}", song.uri)
                        subTitle.text = currentMusicTitle
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
                list.addView(makeHintText("暂无酷我音乐收藏。搜索歌曲或歌手后，点击“收藏”即可保存到这里。"))
                content.addView(ScrollView(this).apply { isFillViewport = true; addView(list) }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val cacheCount = favorites.count { it.playUrl.isNotBlank() && !isFavoritePlayCacheExpired(it) }
                val cacheAction = Triple("清除播放缓存", "已缓存 $cacheCount/${favorites.size} 首 · 点此清除临时链接") {
                    clearFavoritePlayCache()
                    Toast.makeText(this@MainActivity, "已清除收藏歌曲播放缓存", Toast.LENGTH_SHORT).show()
                    showFavoriteTab()
                }
                val items = listOf(cacheAction) + favorites.map { record ->
                    Triple(record.title, favoriteRecordSubtitle(record)) {
                        playOnlineRecord(record)
                        subTitle.text = currentMusicTitle
                    }
                }
                val loading = listOf("0") + favorites.map { if (loadingOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                val current = listOf("0") + favorites.map { if (currentOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                val favoriteActions = listOf<(() -> Unit)?>(null) + favorites.map { record ->
                    {
                        removeMusicRecord(MUSIC_FAVORITES_KEY, record)
                        Toast.makeText(this@MainActivity, "已取消收藏：${record.title}", Toast.LENGTH_SHORT).show()
                        showFavoriteTab()
                    }
                }
                content.addView(musicCardGrid(items, loading, current, favoriteStates = listOf(false) + favorites.map { true }, favoriteActions = favoriteActions), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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

            fun replaceOnlineList(view: View, keepScroll: Boolean = false) {
                val previousScroll = if (keepScroll) (listContainer.getChildAt(0) as? ScrollView)?.scrollY ?: 0 else 0
                listContainer.removeAllViews()
                listContainer.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                if (keepScroll && previousScroll > 0) {
                    view.post { (view as? ScrollView)?.scrollTo(0, previousScroll) }
                }
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
                hint = "搜索酷我音乐，输入歌曲或歌手..."
                setText(onlineLastKeyword)
                textSize = 14f
                isSingleLine = true
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                setTextColor(Color.parseColor("#111827"))
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

            fun renderOnlineSongs(songs: List<com.yuno.tools.util.MusicSearchHelper.OnlineSong>, keepScroll: Boolean = false) {
                if (songs.isEmpty()) {
                    val listArea = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                    }
                    listArea.addView(makeMusicRow("未搜索到结果", "可尝试输入完整歌名或歌手名；接口限流时请稍后重试", "", {}))
                    replaceOnlineList(ScrollView(this).apply {
                        isFillViewport = true
                        addView(listArea)
                    })
                    return
                }
                val records = songs.map { song ->
                    OnlineMusicRecord(
                        title = song.title,
                        artist = song.artist,
                        sourceLabel = song.source.label,
                        pageUrl = song.pageUrl,
                        playUrl = song.playUrl.orEmpty(),
                        songId = song.songId
                    )
                }
                val items = records.map { record ->
                    Triple(record.title, record.artist.ifBlank { record.sourceLabel }) {
                        if (record.playUrl.isBlank()) {
                            Toast.makeText(this, "该歌曲暂无可用播放链接", Toast.LENGTH_SHORT).show()
                        } else {
                            loadingOnlinePlayKey = musicRecordKey(record)
                            currentOnlinePlayKey = musicRecordKey(record)
                            playOnlineRecord(record)
                            subTitle.text = currentMusicTitle
                            renderOnlineSongs(songs, keepScroll = true)
                        }
                    }
                }
                val longActions = records.map { record ->
                    buildList<Pair<String, () -> Unit>> {
                        add((if (isMusicFavorite(record)) "取消收藏" else "收藏") to {
                            toggleMusicFavorite(record)
                            Toast.makeText(this@MainActivity, if (isMusicFavorite(record)) "已收藏：${record.title}" else "已取消收藏：${record.title}", Toast.LENGTH_SHORT).show()
                            renderOnlineSongs(songs, keepScroll = true)
                        })
                        if (record.playUrl.isNotBlank()) add("下载" to { downloadOnlineSong(record) })
                    }
                }
                val favoriteActions = records.map { record ->
                    {
                        toggleMusicFavorite(record)
                        Toast.makeText(this@MainActivity, if (isMusicFavorite(record)) "已收藏：${record.title}" else "已取消收藏：${record.title}", Toast.LENGTH_SHORT).show()
                        renderOnlineSongs(songs, keepScroll = true)
                    }
                }
                val loading = records.map { if (loadingOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                val current = records.map { if (currentOnlinePlayKey == musicRecordKey(it)) "1" else "0" }
                val favorites = records.map { isMusicFavorite(it) }
                replaceOnlineList(musicCardGrid(items, loading, current, longActions, favorites, favoriteActions), keepScroll = keepScroll)
            }

            refreshOnlineMusicList = { renderOnlineSongs(onlineCachedSongs, keepScroll = true) }

            val searchButton = makeControlButton("搜索") { _ ->
                val keyword = input.text.toString().trim()
                onlineLastKeyword = keyword
                if (keyword.isBlank()) {
                    onlineCachedSongs = emptyList()
                    showOnlineHint("输入歌曲名或歌手名搜索酷我音乐")
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
                        renderOnlineSongs(songs, keepScroll = true)
                    }
                }
            }
            searchRow.addView(searchButton)
            input.setOnEditorActionListener { _, actionId, event ->
                val enterPressed = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                    searchButton.performClick()
                    true
                } else {
                    false
                }
            }

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
                showOnlineHint("输入歌曲名或歌手名搜索酷我音乐")
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

        val nowPlayingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 26f * density
                setColor(Color.argb(190, 255, 255, 255))
                setStroke((1f * density).toInt(), Color.argb(115, 209, 213, 219))
            }
        }
        val playingTitle = TextView(this).apply {
            text = currentMusicTitle
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#111827"))
        }
        nowPlayingCard.addView(playingTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (9 * density).toInt(), 0, (2 * density).toInt())
        }
        val currentTimeText = TextView(this).apply {
            text = "0:00"
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
        }
        val totalTimeText = TextView(this).apply {
            text = "--:--"
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
        }
        val progressView = AvatarMusicProgressView(this).apply {
            setAvatarUriText(UserSettingsStore.getAvatarUri(this@MainActivity))
            setOnSeek { fraction ->
                val player = musicPlayer ?: return@setOnSeek
                val duration = player.duration.takeIf { it > 0 } ?: return@setOnSeek
                player.seekTo((duration * fraction).toLong().coerceIn(0L, duration))
                updateFlowingLyrics()
            }
        }
        progressRow.addView(currentTimeText, LinearLayout.LayoutParams((42 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        progressRow.addView(progressView, LinearLayout.LayoutParams(0, (34 * density).toInt(), 1f))
        progressRow.addView(totalTimeText, LinearLayout.LayoutParams((42 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        nowPlayingCard.addView(progressRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        val previousBtn = makeCircleControlButton("‹‹", false) { playPreviousMusic() }
        val playPauseBtn = makeCircleControlButton(if (musicPlayer?.isPlaying == true) "Ⅱ" else "▶", true) { toggleCurrentMusicPlayback() }
        val nextBtn = makeCircleControlButton("››", false) { playNextMusic() }
        transportRow.addView(previousBtn)
        transportRow.addView(playPauseBtn)
        transportRow.addView(nextBtn)
        nowPlayingCard.addView(transportRow)
        val optionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val randomBtn = makeIconPillButton("⤨", musicShuffleEnabled) {
            musicShuffleEnabled = !musicShuffleEnabled
            musicPlayer?.shuffleModeEnabled = musicShuffleEnabled
            refreshPlayerPanelState?.invoke()
        }
        val loopBtn = makeIconPillButton("↻", musicRepeatMode == Player.REPEAT_MODE_ONE) {
            musicRepeatMode = if (musicRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            musicPlayer?.repeatMode = musicRepeatMode
            refreshPlayerPanelState?.invoke()
        }
        optionRow.addView(randomBtn)
        optionRow.addView(loopBtn)
        nowPlayingCard.addView(optionRow)
        val karaokeLyricsView = KaraokeLyricsView(this).apply {
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), 0)
            updateLyrics(currentTimedLyrics, currentLyricIndex, (musicPlayer?.currentPosition ?: 0L) + LYRIC_SYNC_LEAD_MS, lyricHighlightColor(), musicPlayer?.isPlaying == true, currentLyricsText)
        }
        nowPlayingCard.addView(karaokeLyricsView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (136 * density).toInt()))
        fun syncKaraokeLyrics() {
            karaokeLyricsView.updateLyrics(currentTimedLyrics, currentLyricIndex, (musicPlayer?.currentPosition ?: 0L) + LYRIC_SYNC_LEAD_MS, lyricHighlightColor(), musicPlayer?.isPlaying == true, currentLyricsText)
        }
        fun syncPanelState() {
            subTitle.text = currentMusicTitle
            playingTitle.text = currentMusicTitle
            playPauseBtn.text = if (musicPlayer?.isPlaying == true) "Ⅱ" else "▶"
            randomBtn.isSelected = musicShuffleEnabled
            randomBtn.background = pillBackground(musicShuffleEnabled)
            loopBtn.isSelected = musicRepeatMode == Player.REPEAT_MODE_ONE
            loopBtn.background = pillBackground(musicRepeatMode == Player.REPEAT_MODE_ONE)
            syncKaraokeLyrics()
            syncMusicProgress(progressView, currentTimeText, totalTimeText)
            updateMusicNotification(musicPlayer?.isPlaying == true)
        }
        refreshMusicProgressView = {
            syncMusicProgress(progressView, currentTimeText, totalTimeText)
            syncKaraokeLyrics()
        }
        refreshLyricsView = { syncKaraokeLyrics() }
        refreshLyricBeatView = { syncKaraokeLyrics() }
        refreshKaraokeLyricsView = { syncKaraokeLyrics() }
        bounceLyricsView = {
            karaokeLyricsView.animate().cancel()
            karaokeLyricsView.translationY = 3f * density
            karaokeLyricsView.animate()
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(1.45f))
                .start()
        }
        refreshPlayerPanelState = { syncPanelState() }
        panel.addView(nowPlayingCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10 * density).toInt()
        })

        dialog.setOnDismissListener {
            if (musicDialog === dialog) {
                refreshMusicProgressView = null
                refreshPlayerPanelState = null
                refreshLyricsView = null
                refreshLyricBeatView = null
                refreshKaraokeLyricsView = null
                bounceLyricsView = null
            }
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
    }


    private fun syncMusicProgress(progressView: AvatarMusicProgressView, currentTimeText: TextView, totalTimeText: TextView) {
        val player = musicPlayer
        val duration = player?.duration?.takeIf { it > 0 } ?: 0L
        val position = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val safePosition = if (duration > 0) position.coerceAtMost(duration) else 0L
        progressView.setProgress(if (duration > 0) safePosition.toFloat() / duration.toFloat() else 0f)
        currentTimeText.text = formatDuration(safePosition)
        totalTimeText.text = formatDuration(duration)
    }

    private fun musicCardGrid(
        items: List<Triple<String, String, () -> Unit>>,
        loadingKeys: List<String> = emptyList(),
        currentKeys: List<String> = emptyList(),
        longActions: List<List<Pair<String, () -> Unit>>> = emptyList(),
        favoriteStates: List<Boolean> = emptyList(),
        favoriteActions: List<(() -> Unit)?> = emptyList()
    ): ScrollView {
        val density = resources.displayMetrics.density
    val grid = GridLayout(this).apply {
        columnCount = 2
        setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
    }
    val dialogSafeWidth = (resources.displayMetrics.widthPixels - 64 * density).toInt().coerceAtLeast((300 * density).toInt())
    val gap = (4 * density).toInt()
    val cardSize = ((dialogSafeWidth - gap * 4) / 2).coerceAtMost((156 * density).toInt()).coerceAtLeast((132 * density).toInt())
    val coverSize = (cardSize * 0.54f).toInt()
    val playSize = (30 * density).toInt()
    items.forEachIndexed { index, item ->
        val titleText = item.first
        val subText = item.second
        val action = item.third
        val isLoading = loadingKeys.getOrNull(index) == "1"
        val isCurrent = currentKeys.getOrNull(index) == "1"
        val isFavorite = favoriteStates.getOrNull(index) == true
        val favoriteAction = favoriteActions.getOrNull(index)
        val card = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cardSize
                height = (cardSize * 1.04f).toInt()
                setMargins(gap, gap, gap, gap)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(Color.argb(if (isCurrent) 255 else 238, 255, 255, 255))
                if (isCurrent) setStroke((1.2f * density).toInt(), Color.parseColor("#007AFF"))
            }
            setOnClickListener { action() }
            setOnLongClickListener {
                val actions = longActions.getOrNull(index).orEmpty()
                if (actions.isEmpty()) {
                    false
                } else {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions.getOrNull(which)?.second?.invoke() }
                        .show()
                    true
                }
            }
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
                cornerRadius = 14f * density
                setColor(Color.argb(38, 0, 122, 255))
            }
        }
        coverBox.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_nav_music_disc)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#007AFF"))
        }, FrameLayout.LayoutParams((coverSize * 0.58f).toInt(), (coverSize * 0.58f).toInt(), Gravity.CENTER))
        val playIndicator: View = if (isCurrent && musicPlayer?.isPlaying == true) {
            MiniBarsView(this).apply {
                setBarStyle(UserSettingsStore.getMusicBarStyle(this@MainActivity))
                setAmplitude(0.24f)
                setBarCount(4)
                setOnClickListener { action() }
                start()
            }
        } else {
            TextView(this).apply {
                text = if (isLoading) "…" else "▶"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#007AFF"))
                }
                setOnClickListener { action() }
            }
        }
        coverBox.addView(playIndicator, FrameLayout.LayoutParams(playSize, playSize, Gravity.CENTER))
        inner.addView(coverBox)
        inner.addView(TextView(this).apply {
            text = titleText
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, (5 * density).toInt(), 0, 0)
        })
        inner.addView(TextView(this).apply {
            text = subText
            textSize = 10f
            setTextColor(Color.parseColor("#8E8E93"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        })
        card.addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        favoriteAction?.let { favAction ->
            card.addView(TextView(this).apply {
                text = if (isFavorite) "♥" else "♡"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (isFavorite) Color.parseColor("#FF2D55") else Color.parseColor("#8E8E93"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(220, 255, 255, 255))
                }
                elevation = 3f * density
                setOnClickListener {
                    favAction.invoke()
                }
            }, FrameLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt(), Gravity.TOP or Gravity.END).apply {
                topMargin = (7 * density).toInt()
                rightMargin = (7 * density).toInt()
            })
        }
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
            setTextColor(Color.parseColor("#007AFF"))
            gravity = Gravity.CENTER
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.argb(34, 0, 122, 255))
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
            setTextColor(Color.parseColor("#111827"))
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor("#7B8494"))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (buttonText.isNotBlank()) {
            row.addView(makeControlButton(buttonText) { action() })
        }
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
            playUrl = song.playUrl.orEmpty(),
            songId = song.songId
        )
        val canPlay = record.playUrl.isNotBlank()
        val key = musicRecordKey(record)
        val isLoading = loadingOnlinePlayKey == key
        val isCurrent = currentOnlinePlayKey == key
        val status = when {
            isLoading -> " · 正在加载"
            isCurrent && musicPlayer?.isPlaying == true -> " · 正在播放"
            isCurrent -> " · 已选中"
            !canPlay -> " · 暂无可用播放链接"
            else -> ""
        }
        val desc = record.sourceLabel + " · " + record.artist + status
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (canPlay) {
            val playLabel = when {
                isLoading -> "加载中…"
                isCurrent && musicPlayer?.isPlaying == true -> "播放中"
                else -> "播放"
            }
            actions += playLabel to {
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
        if (canPlay) actions += "下载" to { downloadOnlineSong(record) }
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
        if (showDownload && record.playUrl.isNotBlank()) actions += "下载" to { downloadOnlineSong(record) }
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
            setTextColor(Color.parseColor("#111827"))
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

    private fun makeCircleControlButton(text: String, primary: Boolean, action: (View) -> Unit): TextView {
        val density = resources.displayMetrics.density
        val size = if (primary) 58 else 46
        return TextView(this).apply {
            this.text = text
            textSize = if (primary) 24f else 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (primary) Color.WHITE else Color.parseColor("#111827"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (primary) Color.parseColor("#007AFF") else Color.argb(235, 242, 242, 247))
            }
            layoutParams = LinearLayout.LayoutParams((size * density).toInt(), (size * density).toInt()).apply {
                marginStart = (10 * density).toInt()
                marginEnd = (10 * density).toInt()
            }
            setOnClickListener { action(it) }
        }
    }

    private fun makeIconPillButton(text: String, selected: Boolean, action: (View) -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else Color.parseColor("#007AFF"))
            background = pillBackground(selected)
            layoutParams = LinearLayout.LayoutParams((46 * density).toInt(), (34 * density).toInt()).apply {
                marginStart = (7 * density).toInt()
                marginEnd = (7 * density).toInt()
            }
            setOnClickListener { action(it) }
        }
    }

    private fun pillBackground(selected: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 17f * density
            setColor(if (selected) Color.parseColor("#007AFF") else Color.argb(235, 242, 242, 247))
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
                setColor(Color.parseColor("#007AFF"))
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
                    songId = obj.optString("songId"),
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
                put("songId", r.songId)
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
        return record.sourceLabel + "|" + record.songId + "|" + record.pageUrl + "|" + record.title + "|" + record.artist
    }


    private fun isFavoritePlayCacheExpired(record: OnlineMusicRecord): Boolean {
        if (record.playUrl.isBlank()) return true
        val age = System.currentTimeMillis() - record.savedAt
        return age < 0L || age > FAVORITE_PLAY_CACHE_TTL_MS
    }

    private fun favoriteRecordSubtitle(record: OnlineMusicRecord): String {
        val base = record.artist.ifBlank { record.sourceLabel }
        val cache = when {
            record.playUrl.isBlank() -> "未缓存"
            isFavoritePlayCacheExpired(record) -> "缓存已过期"
            else -> "已缓存"
        }
        return "$base · $cache"
    }

    private fun clearFavoritePlayCache() {
        val cleared = loadMusicRecords(MUSIC_FAVORITES_KEY).map {
            if (it.localPath.isNotBlank()) it else it.copy(playUrl = "", savedAt = 0L)
        }
        saveMusicRecords(MUSIC_FAVORITES_KEY, cleared)
        if (musicPanelLastTab == MusicPanelTab.FAVORITE) refreshOnlineMusicList?.invoke()
    }

    private fun playOnlineRecord(record: OnlineMusicRecord) {
        val key = musicRecordKey(record)
        updateMusicPlaylist()
        currentMusicIndex = musicPlaylist.indexOfFirst { sameMusicRecord(it, record) }
        if (record.localPath.isNotBlank()) {
            playResolvedOnlineRecord(record, Uri.fromFile(File(record.localPath)), key)
            return
        }
        val cachedFavorite = musicPanelLastTab == MusicPanelTab.FAVORITE && record.playUrl.isNotBlank() && !isFavoritePlayCacheExpired(record)
        val shouldRefresh = record.playUrl.isBlank() || (musicPanelLastTab == MusicPanelTab.FAVORITE && !cachedFavorite)
        if (!shouldRefresh && record.playUrl.isNotBlank()) {
            playResolvedOnlineRecord(record, com.yuno.tools.util.MusicSearchHelper.uriFromPublicUrl(record.playUrl), key)
            return
        }
        loadingOnlinePlayKey = key
        currentOnlinePlayKey = key
        refreshOnlineMusicList?.invoke()
        Toast.makeText(this, "正在刷新收藏歌曲播放链接…", Toast.LENGTH_SHORT).show()
        Thread {
            val refreshed = com.yuno.tools.util.MusicSearchHelper.refreshPlayableSong(record.title, record.artist, record.songId, record.pageUrl)
            runOnUiThread {
                val playable = refreshed?.let {
                    record.copy(
                        artist = it.artist.ifBlank { record.artist },
                        sourceLabel = it.source.label,
                        pageUrl = it.pageUrl.ifBlank { record.pageUrl },
                        playUrl = it.playUrl.orEmpty(),
                        songId = it.songId.ifBlank { record.songId },
                        savedAt = System.currentTimeMillis()
                    )
                } ?: record
                if (playable.playUrl.isBlank()) {
                    loadingOnlinePlayKey = null
                    currentOnlinePlayKey = null
                    refreshOnlineMusicList?.invoke()
                    Toast.makeText(this, "收藏歌曲暂时无法刷新播放链接，请重新搜索后收藏", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (musicPanelLastTab == MusicPanelTab.FAVORITE) replaceFavoriteRecord(record, playable)
                playResolvedOnlineRecord(playable, com.yuno.tools.util.MusicSearchHelper.uriFromPublicUrl(playable.playUrl), musicRecordKey(playable))
            }
        }.start()
    }

    private fun playResolvedOnlineRecord(record: OnlineMusicRecord, target: Uri, key: String) {
        currentOnlinePlayKey = key
        loadingOnlinePlayKey = key
        playSelectedMusic(record.sourceLabel + " · " + record.title, target, key)
        loadLyricsForRecord(record, key)
    }

    private fun replaceFavoriteRecord(old: OnlineMusicRecord, fresh: OnlineMusicRecord) {
        val records = loadMusicRecords(MUSIC_FAVORITES_KEY)
        val updated = records.map { if (sameMusicRecord(it, old)) fresh else it }
        saveMusicRecords(MUSIC_FAVORITES_KEY, updated)
    }

    private fun loadLyricsForRecord(record: OnlineMusicRecord, key: String = musicRecordKey(record)) {
        currentTimedLyrics = emptyList()
        currentLyricIndex = -1
        if (record.songId.isBlank()) {
            currentLyricsKey = key
            currentLyricsText = "本地歌曲暂无在线歌词"
            refreshLyricsView?.invoke(currentLyricsText)
            return
        }
        currentLyricsKey = key
        currentLyricsText = "正在加载歌词..."
        refreshLyricsView?.invoke(currentLyricsText)
        Thread {
            val lines = runCatching { com.yuno.tools.util.MusicSearchHelper.fetchKuwoTimedLyrics(record.songId) }.getOrElse { emptyList() }
            runOnUiThread {
                if (currentLyricsKey == key) {
                    currentTimedLyrics = lines.map { TimedLyricLine(it.timeMs, it.text) }
                    currentLyricIndex = -1
                    currentLyricsText = if (lines.isEmpty()) "暂无歌词，可稍后重试" else lines.first().text
                    refreshLyricsView?.invoke(currentLyricsText)
                    updateFlowingLyrics()
                }
            }
        }.start()
    }

    private fun downloadOnlineSong(record: OnlineMusicRecord) {
        if (record.playUrl.isBlank()) {
            Toast.makeText(this, "该歌曲暂无可用播放链接，不能下载", Toast.LENGTH_SHORT).show()
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

    private fun musicBarColors(style: String): IntArray = when (style) {
        UserSettingsStore.MUSIC_BAR_RED -> intArrayOf(Color.parseColor("#FF3B30"), Color.parseColor("#FF6B5F"))
        UserSettingsStore.MUSIC_BAR_GREEN -> intArrayOf(Color.parseColor("#34C759"), Color.parseColor("#77D98A"))
        UserSettingsStore.MUSIC_BAR_PURPLE -> intArrayOf(Color.parseColor("#AF52DE"), Color.parseColor("#BF7AF0"))
        UserSettingsStore.MUSIC_BAR_BLUE -> intArrayOf(Color.parseColor("#007AFF"), Color.parseColor("#64D2FF"))
        else -> intArrayOf(Color.parseColor("#FF3B30"), Color.parseColor("#FFCC00"), Color.parseColor("#34C759"), Color.parseColor("#007AFF"), Color.parseColor("#AF52DE"))
    }


    private class KaraokeLyricsView(context: Context) : View(context) {
        private val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private var lines: List<TimedLyricLine> = emptyList()
        private var lineIndex: Int = -1
        private var positionMs: Long = 0L
        private var highlightColor: Int = Color.parseColor("#007AFF")
        private var playing: Boolean = false
        private var fallbackText: String = "歌词将在播放酷我歌曲后显示"
        private var dragStartY = 0f
        private var dragStartOffset = 0f
        private var manualOffset = 0f

        init {
            isClickable = true
        }

        fun updateLyrics(lines: List<TimedLyricLine>, lineIndex: Int, positionMs: Long, highlightColor: Int, playing: Boolean, fallbackText: String) {
            this.lines = lines
            this.lineIndex = lineIndex
            this.positionMs = positionMs.coerceAtLeast(0L)
            this.highlightColor = highlightColor
            this.playing = playing
            this.fallbackText = fallbackText
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val density = resources.displayMetrics.density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragStartY = event.rawY
                    dragStartOffset = manualOffset
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val minOffset = -54f * density
                    val maxOffset = 18f * density
                    manualOffset = (dragStartOffset + event.rawY - dragStartY).coerceIn(minOffset, maxOffset)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val current = lines.getOrNull(lineIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))?.text.orEmpty()
            val text = current.ifBlank { fallbackText }
            val display = text.take(30)
            if (display.isBlank()) return

            lyricPaint.textSize = 20f * density
            notePaint.textSize = 11f * density
            highlightPaint.textSize = lyricPaint.textSize
            lyricPaint.color = Color.parseColor("#8A94A6")
            highlightPaint.color = highlightColor
            notePaint.color = if (playing) blend(highlightColor, Color.WHITE, 0.16f) else Color.parseColor("#A6B0BE")
            notePaint.setShadowLayer(4f * density, 0f, 1.5f * density, Color.argb(90, 0, 0, 0))

            val baseOffset = -16f * density
            val baseline = height * 0.58f + baseOffset + manualOffset
            val totalWidth = lyricPaint.measureText(display).coerceAtLeast(1f)
            val startX = width / 2f - totalWidth / 2f

            var progress = 0f
            var charIndex = -1
            var localProgress = 0f
            if (lines.isNotEmpty() && current.isNotBlank()) {
                val safe = lineIndex.coerceIn(0, lines.lastIndex)
                val lineStart = lines[safe].timeMs
                val lineEnd = lines.getOrNull(safe + 1)?.timeMs ?: (lineStart + 3200L)
                val duration = (lineEnd - lineStart).coerceAtLeast(900L)
                progress = ((positionMs - lineStart).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                val charSpan = (display.length - 1).coerceAtLeast(1)
                val charFloat = (progress * charSpan).coerceIn(0f, charSpan.toFloat())
                charIndex = kotlin.math.floor(charFloat).toInt().coerceIn(0, display.lastIndex)
                localProgress = (charFloat - kotlin.math.floor(charFloat)).coerceIn(0f, 1f)
            }

            val charCenters = FloatArray(display.length)
            val charWidths = FloatArray(display.length)
            var x = startX
            for (i in display.indices) {
                val charText = display[i].toString()
                val charWidth = lyricPaint.measureText(charText)
                charWidths[i] = charWidth
                charCenters[i] = x + charWidth / 2f
                x += charWidth
            }

            val impactWidth = 0.26f
            fun impactAt(distance: Float): Float {
                val t = (1f - kotlin.math.abs(distance) / impactWidth).coerceIn(0f, 1f)
                return t * t * (3f - 2f * t)
            }
            val currentImpact = if (playing && charIndex >= 0) impactAt(localProgress) else 0f
            val nextImpact = if (playing && charIndex >= 0) impactAt(localProgress - 1f) else 0f
            val hop = if (playing && charIndex >= 0) kotlin.math.sin(localProgress * Math.PI).toFloat().coerceAtLeast(0f) else 0f
            val smooth = localProgress * localProgress * (3f - 2f * localProgress)
            val currentCenter = if (charIndex >= 0) charCenters[charIndex] else width / 2f
            val nextCenter = if (charIndex >= 0) charCenters[(charIndex + 1).coerceAtMost(display.lastIndex)] else currentCenter
            val noteCenterX = currentCenter + (nextCenter - currentCenter) * smooth

            for (i in display.indices) {
                val charText = display[i].toString()
                val isCurrent = i == charIndex
                val isNext = i == (charIndex + 1).coerceAtMost(display.lastIndex)
                val isSung = charIndex >= 0 && (i < charIndex || (isCurrent && localProgress > 0.08f))
                val paint = if (isSung || isCurrent) highlightPaint else lyricPaint
                val impact = when {
                    isCurrent -> currentImpact
                    isNext -> nextImpact
                    else -> 0f
                }
                val pressDown = impact * 3.2f * density
                val scaleX = 1f + impact * 0.08f
                val scaleY = 1f - impact * 0.1f
                val drawX = charCenters[i] - charWidths[i] / 2f
                canvas.save()
                if (impact > 0f) canvas.scale(scaleX, scaleY, charCenters[i], baseline + pressDown)
                canvas.drawText(charText, drawX, baseline + pressDown, paint)
                canvas.restore()
            }

            if (charIndex >= 0) {
                val lyricTop = baseline + lyricPaint.fontMetrics.ascent
                val touchY = lyricTop - notePaint.fontMetrics.descent + 0.5f * density
                val noteHop = hop * 15f * density
                val landingImpact = currentImpact.coerceAtLeast(nextImpact)
                canvas.save()
                canvas.scale(1f + landingImpact * 0.1f, 1f - landingImpact * 0.1f, noteCenterX, touchY)
                canvas.drawText("♪", noteCenterX, touchY - noteHop, notePaint)
                canvas.restore()
                if (playing) postInvalidateOnAnimation()
            } else {
                val lyricTop = baseline + lyricPaint.fontMetrics.ascent
                val noteY = lyricTop - notePaint.fontMetrics.descent + 0.5f * density
                canvas.drawText("♪", width / 2f, noteY, notePaint)
            }
            notePaint.clearShadowLayer()
        }

        private fun blend(from: Int, to: Int, ratio: Float): Int {
            val t = ratio.coerceIn(0f, 1f)
            val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt().coerceIn(0, 255)
            val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt().coerceIn(0, 255)
            val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }
    }

    private class AvatarMusicProgressView(context: Context) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clipPath = Path()
        private var progress = 0f
        private var avatarBitmap: Bitmap? = null
        private var onSeek: ((Float) -> Unit)? = null

        init {
            trackPaint.strokeCap = Paint.Cap.ROUND
            fillPaint.strokeCap = Paint.Cap.ROUND
            ringPaint.style = Paint.Style.STROKE
            ringPaint.color = Color.WHITE
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setProgress(value: Float) {
            val next = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(next - progress) > 0.002f) {
                progress = next
                invalidate()
            }
        }

        fun setOnSeek(listener: (Float) -> Unit) { onSeek = listener }

        fun setAvatarUriText(uriText: String?) {
            avatarBitmap = runCatching {
                val stream: InputStream? = if (!uriText.isNullOrBlank()) context.contentResolver.openInputStream(Uri.parse(uriText)) else null
                stream?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: BitmapFactory.decodeResource(resources, R.drawable.ic_profile)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerY = height / 2f
            val thumbRadius = minOf(height * 0.42f, dp(13f))
            val startX = thumbRadius + dp(3f)
            val endX = width - thumbRadius - dp(3f)
            val currentX = startX + (endX - startX) * progress
            trackPaint.strokeWidth = dp(6f)
            trackPaint.color = Color.argb(80, 15, 23, 42)
            canvas.drawLine(startX, centerY, endX, centerY, trackPaint)
            fillPaint.strokeWidth = dp(6f)
            fillPaint.color = Color.parseColor("#007AFF")
            canvas.drawLine(startX, centerY, currentX, centerY, fillPaint)
            fillPaint.color = Color.argb(70, 0, 122, 255)
            canvas.drawCircle(currentX, centerY, thumbRadius + dp(4f), fillPaint)
            canvas.save()
            clipPath.reset()
            clipPath.addCircle(currentX, centerY, thumbRadius, Path.Direction.CW)
            canvas.clipPath(clipPath)
            avatarBitmap?.let { bitmap ->
                val side = thumbRadius * 2f
                val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                val dst = android.graphics.RectF(currentX - thumbRadius, centerY - thumbRadius, currentX + thumbRadius, centerY + thumbRadius)
                canvas.drawBitmap(bitmap, src, dst, null)
            }
            canvas.restore()
            ringPaint.strokeWidth = dp(2f)
            canvas.drawCircle(currentX, centerY, thumbRadius, ringPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val thumbRadius = minOf(height * 0.42f, dp(13f))
            val startX = thumbRadius + dp(3f)
            val endX = width - thumbRadius - dp(3f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val fraction = ((event.x - startX) / (endX - startX).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    progress = fraction
                    invalidate()
                    onSeek?.invoke(fraction)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            return true
        }

        private fun dp(v: Float) = v * resources.displayMetrics.density
    }

    private class MiniBarsView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        private var colors: IntArray = intArrayOf(Color.parseColor("#007AFF"))
        private var animator: ValueAnimator? = null
        private var amplitude = 0.46f
        private var barCount = 4
        private var levelProvider: (() -> Float)? = null
        private var spectrumStyle = false
        private var spectrumMode = UserSettingsStore.MUSIC_SPECTRUM_MIRROR

        fun setBarStyle(style: String) {
            colors = when (style) {
                UserSettingsStore.MUSIC_BAR_RED -> intArrayOf(Color.parseColor("#FF3B30"), Color.parseColor("#FF6B5F"))
                UserSettingsStore.MUSIC_BAR_GREEN -> intArrayOf(Color.parseColor("#34C759"), Color.parseColor("#77D98A"))
                UserSettingsStore.MUSIC_BAR_PURPLE -> intArrayOf(Color.parseColor("#AF52DE"), Color.parseColor("#BF7AF0"))
                UserSettingsStore.MUSIC_BAR_BLUE -> intArrayOf(Color.parseColor("#007AFF"), Color.parseColor("#64D2FF"))
                else -> intArrayOf(Color.parseColor("#FF3B30"), Color.parseColor("#FFCC00"), Color.parseColor("#34C759"), Color.parseColor("#007AFF"), Color.parseColor("#AF52DE"))
            }
            invalidate()
        }

        fun setAmplitude(value: Float) {
            amplitude = value.coerceIn(0.12f, 0.86f)
            invalidate()
        }

        fun setBarCount(value: Int) {
            barCount = value.coerceIn(4, 52)
            invalidate()
        }

        fun setSpectrumStyle(enabled: Boolean, mode: String = UserSettingsStore.MUSIC_SPECTRUM_MIRROR) {
            spectrumStyle = enabled
            spectrumMode = mode
            invalidate()
        }

        fun setLevelProvider(provider: (() -> Float)?) {
            levelProvider = provider
        }

        fun start() {
            if (animator?.isStarted == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 520L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { phase = it.animatedFraction; invalidate() }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val count = barCount
            if (spectrumStyle) {
                when (spectrumMode) {
                    UserSettingsStore.MUSIC_SPECTRUM_UP -> drawUpSpectrum(canvas, count)
                    UserSettingsStore.MUSIC_SPECTRUM_WAVE -> drawGlowWaveSpectrum(canvas)
                    else -> drawVinylWrapSpectrum(canvas, count)
                }
                return
            }
            val gap = width / (count * 1.18f)
            val barWidth = (gap * 0.44f).coerceAtLeast(2f)
            val base = height * 0.82f
            val dynamicLevel = (levelProvider?.invoke() ?: 0.65f).coerceIn(0.16f, 1f)
            val maxHeight = height * amplitude * dynamicLevel
            val minHeight = height * 0.16f
            for (i in 0 until count) {
                val waveA = kotlin.math.sin(((phase * 360f) + i * 42f) * Math.PI / 180f).toFloat()
                val waveB = kotlin.math.cos(((phase * 260f) + i * 73f) * Math.PI / 180f).toFloat()
                val normalized = ((waveA + waveB + 2f) / 4f).coerceIn(0f, 1f)
                val h = (minHeight + maxHeight * normalized).coerceAtMost(height * 0.86f)
                paint.color = colors[i % colors.size]
                val left = width / 2f - (count * gap) / 2f + i * gap + gap * 0.35f
                canvas.drawRoundRect(left, base - h, left + barWidth, base, barWidth, barWidth, paint)
            }
        }

        private fun drawVinylWrapSpectrum(canvas: Canvas, count: Int) {
            val cx = width / 2f
            val cy = height * 0.54f
            val frameSize = (height * 0.58f).coerceAtMost(width * 0.58f)
            val radius = frameSize / 2f
            val dynamicLevel = (levelProvider?.invoke() ?: 0.68f).coerceIn(0.18f, 1f)
            val color = colors.firstOrNull() ?: Color.WHITE
            val accent = if (colors.size == 1) Color.WHITE else color
            val oldStyle = paint.style
            val oldStroke = paint.strokeWidth

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.7f
            paint.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(cx, cy, radius, paint)

            paint.strokeWidth = 1.2f
            paint.color = Color.argb(190, 255, 255, 255)
            canvas.drawCircle(cx, cy, frameSize * 0.31f, paint)
            paint.color = Color.argb(145, 255, 255, 255)
            canvas.drawCircle(cx, cy, frameSize * 0.15f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent))
            val dot = frameSize * 0.035f
            canvas.drawCircle(cx + kotlin.math.cos(45.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, cy + kotlin.math.sin(45.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, dot, paint)
            canvas.drawCircle(cx + kotlin.math.cos(135.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, cy + kotlin.math.sin(135.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, dot, paint)
            canvas.drawCircle(cx + kotlin.math.cos(225.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, cy + kotlin.math.sin(225.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, dot, paint)
            canvas.drawCircle(cx + kotlin.math.cos(315.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, cy + kotlin.math.sin(315.0 * Math.PI / 180.0).toFloat() * radius * 0.72f, dot, paint)

            val radialBars = count.coerceAtLeast(36)
            paint.strokeWidth = 1.1f
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0 until radialBars) {
                val angle = ((i.toFloat() / radialBars) * 360f - 90f) * Math.PI.toFloat() / 180f
                drawRadialWrapBar(canvas, cx, cy, radius, angle, i, dynamicLevel, accent)
            }
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeWidth = oldStroke
            paint.style = oldStyle
        }

        private fun drawRadialWrapBar(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, index: Int, dynamicLevel: Float, accent: Int) {
            val waveA = kotlin.math.sin(((phase * 360f) + index * 31f) * Math.PI / 180f).toFloat()
            val waveB = kotlin.math.cos(((phase * 240f) + index * 57f) * Math.PI / 180f).toFloat()
            val normalized = (0.52f * kotlin.math.abs(waveA) + 0.48f * kotlin.math.abs(waveB)).coerceIn(0f, 1f)
            val length = (height * (0.035f + 0.13f * amplitude * dynamicLevel * normalized)).coerceAtLeast(2f)
            val startRadius = radius + 2f
            val endRadius = radius + 2f + length
            val cos = kotlin.math.cos(angle)
            val sin = kotlin.math.sin(angle)
            paint.color = Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawLine(cx + cos * startRadius, cy + sin * startRadius, cx + cos * endRadius, cy + sin * endRadius, paint)
        }

        private fun drawGlowWaveSpectrum(canvas: Canvas) {
            val oldStyle = paint.style
            val oldStroke = paint.strokeWidth
            val oldAlpha = paint.alpha
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            val centerY = height * 0.54f
            val dynamicLevel = (levelProvider?.invoke() ?: 0.68f).coerceIn(0.18f, 1f)
            val points = 96
            val leftPad = width * 0.04f
            val usableWidth = width - leftPad * 2f
            val colors = intArrayOf(Color.parseColor("#4B6CFF"), Color.parseColor("#7A5CFF"), Color.parseColor("#64F3FF"))
            for (layer in 0 until 5) {
                val stroke = when (layer) {
                    0 -> 7.5f
                    1 -> 5.2f
                    2 -> 3.2f
                    else -> 1.3f
                }
                paint.strokeWidth = stroke
                paint.color = colors[layer % colors.size]
                paint.alpha = when (layer) {
                    0 -> 34
                    1 -> 58
                    2 -> 105
                    else -> 210
                }
                var lastX = leftPad
                var lastY = centerY
                for (i in 0..points) {
                    val t = i.toFloat() / points.toFloat()
                    val x = leftPad + usableWidth * t
                    val edgeFade = kotlin.math.sin(t * Math.PI).toFloat().coerceAtLeast(0f)
                    val waveA = kotlin.math.sin((t * 7.0 * Math.PI + phase * Math.PI * 2.0 + layer * 0.7)).toFloat()
                    val waveB = kotlin.math.sin((t * 17.0 * Math.PI - phase * Math.PI * 3.2 + layer * 0.45)).toFloat()
                    val waveC = kotlin.math.cos((t * 11.0 * Math.PI + phase * Math.PI * 4.3)).toFloat()
                    val amp = height * (0.10f + 0.35f * dynamicLevel) * edgeFade
                    val y = centerY + (waveA * 0.58f + waveB * 0.30f + waveC * 0.12f) * amp * (1f - layer * 0.08f)
                    if (i > 0) canvas.drawLine(lastX, lastY, x, y, paint)
                    lastX = x
                    lastY = y
                }
            }
            paint.strokeWidth = 1.1f
            paint.alpha = 230
            paint.color = Color.parseColor("#D9F8FF")
            canvas.drawLine(leftPad, centerY, width - leftPad, centerY, paint)
            paint.alpha = oldAlpha
            paint.strokeWidth = oldStroke
            paint.style = oldStyle
        }

        private fun drawUpSpectrum(canvas: Canvas, count: Int) {
            val baseY = height * 0.96f
            val gap = width / (count * 1.03f)
            val barWidth = (gap * 0.48f).coerceAtLeast(2f)
            val dynamicLevel = (levelProvider?.invoke() ?: 0.68f).coerceIn(0.18f, 1f)
            val maxHeight = height * 0.78f * amplitude * dynamicLevel
            val minHeight = height * 0.10f
            val startX = width / 2f - (count * gap) / 2f
            paint.color = Color.argb(105, 255, 255, 255)
            canvas.drawRect(0f, baseY - 0.5f, width.toFloat(), baseY + 0.5f, paint)
            for (i in 0 until count) {
                val waveA = kotlin.math.sin(((phase * 360f) + i * 31f) * Math.PI / 180f).toFloat()
                val waveB = kotlin.math.cos(((phase * 230f) + i * 67f) * Math.PI / 180f).toFloat()
                val waveC = kotlin.math.sin(((phase * 520f) + i * 13f) * Math.PI / 180f).toFloat()
                val normalized = (0.42f * kotlin.math.abs(waveA) + 0.34f * kotlin.math.abs(waveB) + 0.24f * kotlin.math.abs(waveC)).coerceIn(0f, 1f)
                val envelope = (0.40f + 0.60f * kotlin.math.abs(kotlin.math.sin((i * 0.34f) + phase * Math.PI.toFloat()))).coerceIn(0f, 1f)
                val topHeight = (minHeight + maxHeight * normalized * envelope).coerceIn(minHeight, baseY - 1f)
                val left = startX + i * gap + gap * 0.28f
                val right = left + barWidth
                val color = colors[i % colors.size]
                paint.color = if (colors.size == 1) Color.WHITE else color
                canvas.drawRoundRect(left, baseY - topHeight, right, baseY, 1.2f, 1.2f, paint)
            }
        }

    }

    private fun updateMusicNavState(isPlaying: Boolean) {
        val selectedColor = Color.parseColor("#1E88E5")
        val normalColor = Color.parseColor("#A0A7B3")
        val icon = runCatching { findViewById<ImageView>(R.id.ivNavMusicDisc) }.getOrNull() ?: return
        val text = runCatching { findViewById<TextView>(R.id.tvNavMusic) }.getOrNull()
        val barSlot = runCatching { findViewById<FrameLayout>(R.id.navMusicBarsTop) }.getOrNull()
        val waveOverlay = runCatching { findViewById<FrameLayout>(R.id.navMusicWaveOverlay) }.getOrNull()
        val color = if (isPlaying) selectedColor else normalColor
        icon.imageTintList = ColorStateList.valueOf(color)
        text?.apply {
            setTextColor(color)
            this.text = if (isPlaying) currentMusicTitle.substringAfter(" · ", currentMusicTitle).take(5).ifBlank { "播放中" } else "播放"
            setTypeface(null, if (isPlaying) Typeface.BOLD else Typeface.NORMAL)
            animate().alpha(if (isPlaying) 1f else 0.72f).setDuration(160L).start()
        }
        val spectrumStyle = UserSettingsStore.getMusicSpectrumStyle(this@MainActivity)
        val isVinylWrap = spectrumStyle == UserSettingsStore.MUSIC_SPECTRUM_MIRROR
        val isWave = spectrumStyle == UserSettingsStore.MUSIC_SPECTRUM_WAVE
        if (isWave) {
            barSlot?.apply {
                removeAllViews()
                visibility = View.INVISIBLE
            }
            waveOverlay?.apply {
                removeAllViews()
                if (isPlaying) {
                    layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                        width = FrameLayout.LayoutParams.MATCH_PARENT
                        height = dp(98)
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        topMargin = -dp(56)
                        leftMargin = -dp(18)
                        rightMargin = -dp(18)
                    }
                    addView(MiniBarsView(this@MainActivity).apply {
                        setBarStyle(UserSettingsStore.getMusicBarStyle(this@MainActivity))
                        setAmplitude(0.92f)
                        setBarCount(44)
                        setSpectrumStyle(true, spectrumStyle)
                        setLevelProvider { currentMusicDynamicLevel() }
                        start()
                    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    bringToFront()
                    visibility = View.VISIBLE
                } else {
                    visibility = View.INVISIBLE
                }
            }
        } else {
            waveOverlay?.apply {
                removeAllViews()
                visibility = View.INVISIBLE
            }
            barSlot?.apply {
                removeAllViews()
                if (isPlaying) {
                    layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                        width = if (isVinylWrap) dp(92) else FrameLayout.LayoutParams.MATCH_PARENT
                        height = if (isVinylWrap) dp(78) else dp(40)
                        gravity = if (isVinylWrap) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP
                        topMargin = if (isVinylWrap) -dp(17) else -dp(26)
                        leftMargin = if (isVinylWrap) 0 else dp(10)
                        rightMargin = if (isVinylWrap) 0 else dp(10)
                    }
                    addView(MiniBarsView(this@MainActivity).apply {
                        setBarStyle(UserSettingsStore.getMusicBarStyle(this@MainActivity))
                        setAmplitude(0.86f)
                        setBarCount(44)
                        setSpectrumStyle(true, spectrumStyle)
                        setLevelProvider { currentMusicDynamicLevel() }
                        start()
                    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    visibility = View.VISIBLE
                } else {
                    visibility = View.INVISIBLE
                }
            }
        }
        if (isPlaying) startMusicSpin(icon) else stopMusicSpin(icon)
        refreshPlayerPanelState?.invoke()
    }

    private fun currentMusicDynamicLevel(): Float {
        val player = musicPlayer ?: return 0.28f
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 } ?: 180_000L
        val titleSeed = currentMusicTitle.fold(0) { acc, ch -> acc + ch.code }
        val progress = position.toFloat() / duration.toFloat()
        val beat = kotlin.math.sin((position / 95.0) + titleSeed * 0.017).toFloat()
        val tone = kotlin.math.sin((position / 430.0) + progress * 18.0 + titleSeed * 0.011).toFloat()
        val pulse = kotlin.math.cos((position / 720.0) + titleSeed * 0.007).toFloat()
        return (0.34f + 0.32f * kotlin.math.abs(beat) + 0.22f * kotlin.math.abs(tone) + 0.12f * kotlin.math.abs(pulse)).coerceIn(0.22f, 1f)
    }

    private fun updateFlowingLyrics() {
        val lines = currentTimedLyrics
        if (lines.isEmpty() || currentLyricsKey == null) return
        val player = musicPlayer ?: return
        val position = (player.currentPosition + LYRIC_SYNC_LEAD_MS).coerceAtLeast(0L)
        val index = lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
        val changedLine = index != currentLyricIndex
        if (changedLine) currentLyricIndex = index
        currentLyricsText = buildLyricsDisplay(position, index).toString()
        refreshLyricBeatView?.invoke(buildLyricBeatDisplay(position))
        refreshLyricsView?.invoke(currentLyricsText)
        refreshKaraokeLyricsView?.invoke()
        if (changedLine) bounceLyricsView?.invoke()
    }

    private fun buildLyricBeatDisplay(position: Long = ((musicPlayer?.currentPosition ?: 0L) + LYRIC_SYNC_LEAD_MS).coerceAtLeast(0L)): CharSequence {
        val lines = currentTimedLyrics
        if (lines.isEmpty() || currentLyricsKey == null) return "♪"
        val safeIndex = currentLyricIndex.coerceAtLeast(0).coerceAtMost(lines.lastIndex)
        val current = lines.getOrNull(safeIndex)?.text.orEmpty()
        if (current.isBlank()) return "♪"
        val lineStartMs = lines[safeIndex].timeMs
        val lineEndMs = lines.getOrNull(safeIndex + 1)?.timeMs ?: (lineStartMs + 3200L)
        val duration = (lineEndMs - lineStartMs).coerceAtLeast(900L)
        val progress = ((position - lineStartMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val charIndex = ((current.length - 1) * progress).roundToInt().coerceIn(0, current.lastIndex)
        val phase = ((position / 160L) % 4L).toInt()
        val note = listOf("♪", "♫", "♬", "♩")[phase]
        val full = "  ".repeat(charIndex) + note
        return SpannableString(full).apply {
            val base = lyricHighlightColor()
            val level = if (musicPlayer?.isPlaying == true) currentMusicDynamicLevel() else 0.3f
            setSpan(ForegroundColorSpan(blendColor(base, Color.WHITE, (0.18f + level * 0.38f).coerceIn(0f, 0.62f))), full.length - note.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun buildLyricsDisplay(
        position: Long = ((musicPlayer?.currentPosition ?: 0L) + LYRIC_SYNC_LEAD_MS).coerceAtLeast(0L),
        lineIndex: Int = currentLyricIndex.coerceAtLeast(0)
    ): CharSequence {
        val lines = currentTimedLyrics
        if (lines.isEmpty()) return currentLyricsText
        val safeIndex = lineIndex.coerceIn(0, lines.lastIndex)
        val previous = lines.getOrNull(safeIndex - 1)?.text
        val current = lines.getOrNull(safeIndex)?.text.orEmpty()
        val next = lines.getOrNull(safeIndex + 1)?.text
        val full = buildString {
            if (!previous.isNullOrBlank()) append(previous).append("\n")
            append("♪ ").append(current)
            if (!next.isNullOrBlank()) append("\n").append(next)
        }
        currentLyricsText = full
        val currentStart = (if (!previous.isNullOrBlank()) previous.length + 1 else 0) + 2
        val currentEnd = (currentStart + current.length).coerceAtMost(full.length)
        if (current.isBlank() || currentStart >= currentEnd) return full
        val lineStartMs = lines[safeIndex].timeMs
        val lineEndMs = lines.getOrNull(safeIndex + 1)?.timeMs ?: (lineStartMs + 3200L)
        val duration = (lineEndMs - lineStartMs).coerceAtLeast(900L)
        val progress = ((position - lineStartMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val highlightEnd = (currentStart + max(1, (current.length * progress).roundToInt())).coerceAtMost(currentEnd)
        return SpannableString(full).apply {
            applyFlowingLyricSpans(this, currentStart, highlightEnd, currentEnd, progress)
        }
    }

    private fun applyFlowingLyricSpans(text: SpannableString, start: Int, highlightEnd: Int, lineEnd: Int, progress: Float) {
        if (start >= highlightEnd) return
        val base = lyricHighlightColor()
        val bright = lightenColor(base, 0.34f)
        val dim = blendColor(base, Color.WHITE, 0.52f)
        val spanCount = (highlightEnd - start).coerceAtLeast(1)
        for (i in start until highlightEnd) {
            val local = (i - start).toFloat() / spanCount.toFloat()
            val wave = ((kotlin.math.sin((local * 8.0 + progress * 10.0).toDouble()) + 1.0) / 2.0).toFloat()
            val color = when {
                local > progress - 0.08f && local < progress + 0.08f -> bright
                wave > 0.72f -> blendColor(base, bright, 0.42f)
                else -> blendColor(dim, base, (0.45f + wave * 0.45f).coerceIn(0f, 1f))
            }
            text.setSpan(ForegroundColorSpan(color), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (highlightEnd < lineEnd) {
            text.setSpan(ForegroundColorSpan(Color.parseColor("#8A94A6")), highlightEnd, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun lightenColor(color: Int, ratio: Float): Int = blendColor(color, Color.WHITE, ratio.coerceIn(0f, 1f))

    private fun blendColor(from: Int, to: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt().coerceIn(0, 255)
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun lyricHighlightColor(): Int = when (UserSettingsStore.getLyricHighlightStyle(this)) {
        UserSettingsStore.LYRIC_HIGHLIGHT_RED -> Color.parseColor("#FF2D55")
        UserSettingsStore.LYRIC_HIGHLIGHT_GREEN -> Color.parseColor("#34C759")
        UserSettingsStore.LYRIC_HIGHLIGHT_PURPLE -> Color.parseColor("#AF52DE")
        UserSettingsStore.LYRIC_HIGHLIGHT_ORANGE -> Color.parseColor("#FF9500")
        else -> Color.parseColor("#007AFF")
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
        navBarsAnimator?.cancel()
        navBarsAnimator = null
        cardBarsAnimator?.cancel()
        cardBarsAnimator = null
        icon.animate().rotation(0f).setDuration(180L).start()
    }

    private fun releaseMusicPlayer() {
        musicDialog?.dismiss()
        musicDialog = null
        musicSpinAnimator?.cancel()
        musicSpinAnimator = null
        navBarsAnimator?.cancel()
        navBarsAnimator = null
        cardBarsAnimator?.cancel()
        cardBarsAnimator = null
        musicMediaSession?.release()
        musicMediaSession = null
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
        val session = musicPlayer?.let { ensureMusicMediaSession(it) }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                session?.sessionCompatToken?.let { setStyle(MediaStyle().setMediaSession(it).setShowActionsInCompactView()) }
            }
            .build()
        manager.notify(MUSIC_NOTIFICATION_ID, notification)
    }


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
        findViewById<Button>(R.id.btnVip7).setOnClickListener { redeemVip(7, 80) }
        findViewById<Button>(R.id.btnVip30).setOnClickListener { redeemVip(30, 260) }
        findViewById<Button>(R.id.btnVip365).setOnClickListener { redeemVip(365, 1999) }
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            AccountStore.logout(this)
            toast("已退出登录")
            refreshAccountPanel()
        }
    }

    private fun redeemVip(days: Int, cost: Int) {
        AccountStore.redeemVip(this, days, cost)
            .onSuccess { toast("兑换成功，会员已延长 ${days}天"); refreshAccountPanel() }
            .onFailure { toast(it.message ?: "兑换失败") }
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
        renderAvatarInto(iv, pv, 18, AvatarSlot.PERSONAL)
        findViewById<TextView>(R.id.tvAvatarHint).text = if (UserSettingsStore.getAvatarUri(this).isNotBlank()) {
            "点击更换头像，支持 GIF / WebP / 视频动态头像"
        } else {
            "点击选择头像，支持 GIF / WebP / 视频动态头像"
        }
    }

    private fun releaseAvatarPlayer(slot: AvatarSlot? = null) {
        fun releasePersonal() {
            val pv = runCatching { findViewById<PlayerView>(R.id.pvAvatar) }.getOrNull()
            pv?.player = null
            avatarPlayer?.release()
            avatarPlayer = null
        }
        fun releaseTitle() {
            val pv = runCatching { findViewById<PlayerView>(R.id.pvTitleAvatar) }.getOrNull()
            pv?.player = null
            titleAvatarPlayer?.release()
            titleAvatarPlayer = null
        }
        fun releaseProfileEntry() {
            val pv = runCatching { findViewById<PlayerView>(R.id.pvProfileEntryAvatar) }.getOrNull()
            pv?.player = null
            profileEntryAvatarPlayer?.release()
            profileEntryAvatarPlayer = null
        }
        when (slot) {
            AvatarSlot.TITLE -> releaseTitle()
            AvatarSlot.PROFILE_ENTRY -> releaseProfileEntry()
            AvatarSlot.PERSONAL -> releasePersonal()
            null -> { releasePersonal(); releaseTitle(); releaseProfileEntry() }
        }
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
        syncProfileAvatarEntries()
        updateNavSelection(currentTab, animate = false)
    }

    private fun syncProfileAvatarEntries() {
        updateHomeProfileEntry()
        updateProfileEntry()
        findViewById<View>(R.id.cardTitleProfile).postDelayed({ updateHomeProfileEntry() }, 120L)
        findViewById<View>(R.id.profilePage).postDelayed({ updateProfileEntry() }, 120L)
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
        lyricsHandler.removeCallbacks(lyricsTicker)
        releaseAvatarPlayer()
        releaseMusicPlayer()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()}
