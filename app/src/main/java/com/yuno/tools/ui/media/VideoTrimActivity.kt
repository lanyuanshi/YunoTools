package com.yuno.tools.ui.media

import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.yuno.tools.databinding.ActivityVideoTrimBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

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
        binding.playerView.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (selectedUri == null) {
                pickVideo.launch("video/*")
            } else if (p.isPlaying) {
                p.pause()
            } else {
                if (p.currentPosition < startMs || (endMs > 0 && p.currentPosition >= endMs)) p.seekTo(startMs)
                p.play()
            }
        }
        handler.post(tick)
    }

    private fun initControls() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickVideo.setOnClickListener { pickVideo.launch("video/*") }
        binding.fabTrim.setOnClickListener { trimSelectedVideo() }

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
        binding.fabTrim.isEnabled = false
        Toast.makeText(this, "正在剪切视频...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { cutVideo(uri, outputName, startMs * 1000L, endMs * 1000L) } }
            binding.fabTrim.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@VideoTrimActivity, "视频已保存：$it", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@VideoTrimActivity, "剪切失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cutVideo(inputUri: Uri, outputName: String, startUs: Long, endUs: Long): String {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var outUri: Uri? = null
        var outFile: File? = null
        try {
            contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("无法读取视频文件")

            val output = createOutput("$outputName.mp4", "video/mp4", Environment.DIRECTORY_MOVIES, "YunoTools/Trim")
            outUri = output.uri
            outFile = output.file
            muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }
            if (trackMap.isEmpty()) error("没有可剪切的音视频轨道")
            muxer.start()

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var wroteSample = false

            for (srcTrack in trackMap.keys) {
                extractor.unselectTrack(srcTrack)
            }
            for (srcTrack in trackMap.keys) {
                extractor.selectTrack(srcTrack)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var firstPts = -1L
                while (true) {
                    val sampleTrack = extractor.sampleTrackIndex
                    if (sampleTrack != srcTrack) break
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0 || sampleTime > endUs) break
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    if (sampleTime >= startUs) {
                        if (firstPts < 0) firstPts = sampleTime
                        info.set(0, size, sampleTime - firstPts, extractor.sampleFlags)
                        val dstTrack = trackMap[srcTrack] ?: break
                        muxer.writeSampleData(dstTrack, buffer, info)
                        wroteSample = true
                    }
                    extractor.advance()
                }
                extractor.unselectTrack(srcTrack)
            }
            if (!wroteSample) error("剪切区间内没有可写入的视频数据")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outUri != null) {
                contentResolver.update(outUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            return output.display
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outUri != null) contentResolver.delete(outUri, null, null)
            outFile?.delete()
            throw e
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private data class OutputInfo(val path: String, val display: String, val uri: Uri? = null, val file: File? = null)

    private fun createOutput(fileName: String, mime: String, directory: String, subDir: String): OutputInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$subDir")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建输出文件")
            val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: error("无法打开输出文件")
            OutputInfo("/proc/self/fd/${pfd.detachFd()}", "$directory/$subDir/$fileName", uri, null)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(directory), subDir).apply { mkdirs() }
            val file = uniqueFile(dir, fileName)
            OutputInfo(file.absolutePath, file.absolutePath, null, file)
        }
    }

    private fun readDuration(uri: Uri): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> retriever.setDataSource(pfd.fileDescriptor) }
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            d
        }.getOrDefault(0L)
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