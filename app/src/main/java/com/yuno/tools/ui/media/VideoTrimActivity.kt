package com.yuno.tools.ui.media

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.yuno.tools.databinding.ActivityVideoTrimBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class VideoTrimActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoTrimBinding
    private var player: ExoPlayer? = null
    private var selectedUri: Uri? = null
    private var durationMs: Long = 0L
    private var startMs: Long = 0L
    private var endMs: Long = 0L
    private var isAdjustingSlider = false
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                binding.tvCurrentTime.text = shortTime(p.currentPosition)
                if (endMs > 0 && p.currentPosition >= endMs) {
                    p.pause()
                    p.seekTo(startMs)
                }
                binding.btnPlayOverlay.visibility = if (p.isPlaying) View.GONE else View.VISIBLE
            }
            handler.postDelayed(this, 250)
        }
    }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        loadVideo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoTrimBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPlayer()
        initControls()
        updateTimeViews()
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().also { binding.playerView.player = it }
        binding.playerView.setOnClickListener { togglePlayback() }
        binding.btnPlayOverlay.setOnClickListener { togglePlayback() }
        handler.post(tick)
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (selectedUri == null) {
            pickVideo.launch("video/*")
            return
        }
        if (p.isPlaying) {
            p.pause()
            binding.btnPlayOverlay.visibility = View.VISIBLE
        } else {
            if (p.currentPosition < startMs || (endMs > 0 && p.currentPosition >= endMs)) p.seekTo(startMs)
            p.play()
            binding.btnPlayOverlay.visibility = View.GONE
        }
    }

    private fun initControls() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickVideo.setOnClickListener { pickVideo.launch("video/*") }
        binding.fabTrim.setOnClickListener { onTrimGenerateClicked() }

        binding.rangeSlider.addOnChangeListener { slider, _, _ ->
            if (isAdjustingSlider || slider.values.size < 2) return@addOnChangeListener
            startMs = (slider.values[0] * 1000).roundToLong().coerceIn(0, durationMs)
            endMs = (slider.values[1] * 1000).roundToLong().coerceIn(startMs, durationMs)
            updateTimeViews()
            player?.seekTo(startMs)
        }

        binding.btnStartMinus.setOnClickListener { adjustStart(-1000) }
        binding.btnStartPlus.setOnClickListener { adjustStart(1000) }
        binding.btnEndMinus.setOnClickListener { adjustEnd(-1000) }
        binding.btnEndPlus.setOnClickListener { adjustEnd(1000) }
    }

    private fun loadVideo(uri: Uri) {
        selectedUri = uri
        val displayName = queryDisplayName(uri)
        val baseName = displayName.substringBeforeLast('.', displayName)
        durationMs = readDuration(uri).coerceAtLeast(1000L)
        startMs = 0L
        endMs = durationMs
        if (binding.etVideoName.text.isNullOrBlank()) binding.etVideoName.setText("${baseName}_trim")

        isAdjustingSlider = true
        binding.rangeSlider.valueFrom = 0f
        binding.rangeSlider.valueTo = max(1f, durationMs / 1000f)
        binding.rangeSlider.values = listOf(0f, durationMs / 1000f)
        isAdjustingSlider = false

        updateTimeViews()
        player?.setMediaItem(MediaItem.fromUri(uri))
        player?.prepare()
        player?.seekTo(0)
        binding.btnPlayOverlay.visibility = View.VISIBLE
        Toast.makeText(this, "已选择：$displayName", Toast.LENGTH_SHORT).show()
    }

    private fun adjustStart(deltaMs: Long) {
        if (durationMs <= 0) return
        startMs = (startMs + deltaMs).coerceIn(0, max(0L, endMs - 1000L))
        player?.seekTo(startMs)
        syncSlider()
    }

    private fun adjustEnd(deltaMs: Long) {
        if (durationMs <= 0) return
        endMs = (endMs + deltaMs).coerceIn(min(durationMs, startMs + 1000L), durationMs)
        syncSlider()
    }

    private fun syncSlider() {
        isAdjustingSlider = true
        binding.rangeSlider.values = listOf(startMs / 1000f, endMs / 1000f)
        isAdjustingSlider = false
        updateTimeViews()
    }

    private fun updateTimeViews() {
        binding.tvStart.text = fullTime(startMs)
        binding.tvEnd.text = fullTime(endMs)
        binding.tvCurrentTime.text = shortTime(player?.currentPosition ?: startMs)
    }

    private fun onTrimGenerateClicked() {
        playTrimClickFeedback()
        trimSelectedVideo()
    }

    private fun playTrimClickFeedback() {
        binding.fabTrim.animate().cancel()
        binding.fabTrim.scaleX = 0.86f
        binding.fabTrim.scaleY = 0.86f
        binding.fabTrim.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotationBy(12f)
            .setDuration(180L)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun setTrimProcessing(processing: Boolean) {
        binding.fabTrim.isEnabled = !processing
        binding.progressTrim.visibility = if (processing) View.VISIBLE else View.GONE
        binding.fabTrim.alpha = if (processing) 0.55f else 1f
    }

    private fun trimSelectedVideo() {
        val uri = selectedUri ?: run {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show()
            return
        }
        if (endMs - startMs < 1000L) {
            Toast.makeText(this, "剪切时长至少 1 秒", Toast.LENGTH_SHORT).show()
            return
        }
        val outputName = sanitizeName(binding.etVideoName.text?.toString()).ifBlank { "Yuno_trim_${System.currentTimeMillis()}" }
        setTrimProcessing(true)
        Toast.makeText(this, "正在生成剪切视频...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { cutVideo(uri, outputName, startMs * 1000L, endMs * 1000L) } }
            setTrimProcessing(false)
            result.onSuccess {
                Toast.makeText(this@VideoTrimActivity, "视频已保存：$it", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@VideoTrimActivity, "剪切失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cutVideo(inputUri: Uri, outputName: String, startUs: Long, endUs: Long): String {
        var outUri: Uri? = null
        var outFile: File? = null
        val tempOutput = File(cacheDir, "trim_transformer_${System.currentTimeMillis()}.mp4")
        try {
            val startMsLocal = (startUs / 1000L).coerceAtLeast(0L)
            val endMsLocal = (endUs / 1000L).coerceAtLeast(startMsLocal + 1000L)
            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMsLocal)
                        .setEndPositionMs(endMsLocal)
                        .build()
                )
                .build()

            val latch = CountDownLatch(1)
            val errorRef = AtomicReference<Throwable?>(null)
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        latch.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        errorRef.set(exportException)
                        latch.countDown()
                    }
                })
                .build()

            Handler(Looper.getMainLooper()).post {
                runCatching { transformer.start(mediaItem, tempOutput.absolutePath) }
                    .onFailure {
                        errorRef.set(it)
                        latch.countDown()
                    }
            }

            if (!latch.await(20, TimeUnit.MINUTES)) {
                transformer.cancel()
                error("剪切超时，请换一个更短区间重试")
            }
            errorRef.get()?.let { throw it }
            if (!tempOutput.exists() || tempOutput.length() <= 1024L) error("未生成有效视频文件")

            val name = if (outputName.endsWith(".mp4", true)) outputName else "$outputName.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/YunoTools")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建输出文件")
                contentResolver.openOutputStream(outUri!!)?.use { output ->
                    tempOutput.inputStream().use { input -> input.copyTo(output) }
                } ?: error("无法写入输出文件")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(outUri!!, values, null, null)
                return "Movies/YunoTools/$name"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "YunoTools").apply { mkdirs() }
                outFile = File(dir, name)
                tempOutput.inputStream().use { input -> FileOutputStream(outFile!!).use { output -> input.copyTo(output) } }
                return outFile!!.absolutePath
            }
        } catch (e: Throwable) {
            outUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            outFile?.takeIf { it.exists() }?.delete()
            throw IllegalStateException(e.message ?: "剪切失败", e)
        } finally {
            tempOutput.delete()
        }
    }


    private fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "video"
        }
        return "video"
    }

    private fun sanitizeName(raw: String?): String = raw.orEmpty().trim().replace(Regex("[\\/:*?\"<>|]"), "_")

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (file.exists()) {
            file = File(dir, if (ext.isBlank()) "${base}_$i" else "${base}_$i.$ext")
            i++
        }
        return file
    }

    private fun fullTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3_600_000
        val m = (total / 60_000) % 60
        val s = (total / 1000) % 60
        val milli = total % 1000
        return "%02d:%02d:%02d.%03d".format(h, m, s, milli)
    }

    private fun shortTime(ms: Long): String {
        val totalSeconds = (ms / 1000.0).roundToLong().coerceAtLeast(0)
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        binding.playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}