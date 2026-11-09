package com.yuno.tools.ui.media

import android.content.ContentValues
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuno.tools.R
import com.yuno.tools.databinding.ActivityAudioSeparateBinding
import com.yuno.tools.util.Mp3Encoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class AudioSeparateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAudioSeparateBinding
    private var selectedUri: Uri? = null
    private var selectedDurationMs: Long = 0L
    private var workJob: Job? = null
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var lastPercent = -1

    private var formatM4a = true

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        selectedUri = uri
        val displayName = queryDisplayName(uri)
        val baseName = displayName.substringBeforeLast('.', displayName)
        selectedDurationMs = queryDurationMs(uri)
        binding.tvVideoInfo.text = "已选择：$displayName"
        binding.tvDuration.text = if (selectedDurationMs > 0) "时长：${formatDuration(selectedDurationMs)}" else ""
        if (binding.etAudioName.text.isNullOrBlank()) binding.etAudioName.setText("${baseName}_audio")
        binding.btnExtract.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioSeparateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickVideo.setOnClickListener { pickVideo.launch("video/*") }
        binding.btnExtract.setOnClickListener { extractSelectedAudio() }
        binding.btnCancel.setOnClickListener { cancelWork() }
        binding.rgFormat.setOnCheckedChangeListener { _, checkedId ->
            formatM4a = checkedId == R.id.rbM4a
            updateFormatHint()
        }
        updateFormatHint()
    }

    override fun onDestroy() {
        cancelled.set(true)
        workJob?.cancel()
        super.onDestroy()
    }

    private fun updateFormatHint() {
        binding.tvFormatHint.text = if (formatM4a) {
            "M4A：直接拷贝原始音频轨道，速度最快，无损保留原音质。"
        } else {
            "MP3：重新编码为 192kbps，兼容性最好，耗时随视频时长增加。"
        }
    }

    private fun extractSelectedAudio() {
        val uri = selectedUri ?: run {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show()
            return
        }
        if (workJob?.isActive == true) return

        val rawName = sanitizeName(binding.etAudioName.text?.toString())
        val outputName = rawName.ifBlank { "Yuno_audio_${System.currentTimeMillis()}" }
        val toMp3 = !formatM4a

        cancelled.set(false)
        lastPercent = -1
        setBusy(true)
        binding.progressBar.progress = 0
        binding.tvProgress.text = "0%"
        binding.tvStatus.text = if (toMp3) "正在解码并编码为 MP3..." else "正在提取音频轨道..."

        workJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (toMp3) extractToMp3(uri, outputName) else extractToM4a(uri, outputName)
                }
            }
            setBusy(false)
            result.onSuccess { info ->
                binding.progressBar.progress = 100
                binding.tvProgress.text = "100%"
                binding.tvStatus.text = "已完成：${info.display}"
                Toast.makeText(this@AudioSeparateActivity, "音频已保存：${info.display}", Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                if (cancelled.get()) {
                    binding.progressBar.progress = 0
                    binding.tvProgress.text = "0%"
                    binding.tvStatus.text = "已取消"
                } else {
                    binding.tvStatus.text = "分离失败：${e.message ?: "未知错误"}"
                    Toast.makeText(this@AudioSeparateActivity, "分离失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun cancelWork() {
        if (workJob?.isActive != true) return
        cancelled.set(true)
        binding.tvStatus.text = "正在取消..."
    }

    private fun setBusy(busy: Boolean) {
        binding.btnExtract.isEnabled = !busy && selectedUri != null
        binding.btnPickVideo.isEnabled = !busy
        binding.btnExtract.text = if (busy) "正在分离..." else "开始分离音频"
        binding.rbM4a.isEnabled = !busy
        binding.rbMp3.isEnabled = !busy
        binding.btnCancel.visibility = if (busy) View.VISIBLE else View.GONE
        binding.progressBox.visibility = if (busy) View.VISIBLE else View.GONE
    }

    /** 直接拷贝压缩音频轨道为 m4a，无损且速度最快。 */
    private fun extractToM4a(inputUri: Uri, outputName: String): OutputInfo {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var outUri: Uri? = null
        var outFile: File? = null
        try {
            contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("无法读取视频文件")

            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) error("该视频没有可提取的音频轨道")

            val output = createOutput("$outputName.m4a", "audio/mp4", Environment.DIRECTORY_MUSIC, "YunoTools/Audio", true)
            outUri = output.uri
            outFile = output.file
            muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(format)
            muxer.start()

            extractor.selectTrack(audioTrack)
            val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1024 * 1024
            val buffer = ByteBuffer.allocate(maxSize.coerceAtLeast(256 * 1024))
            val info = MediaCodec.BufferInfo()

            while (true) {
                if (cancelled.get()) error("已取消")
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(dstTrack, buffer, info)
                publishProgress(extractor.sampleTime)
                extractor.advance()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outUri != null) {
                contentResolver.update(outUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            return output
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outUri != null) runCatching { contentResolver.delete(outUri, null, null) }
            outFile?.delete()
            throw e
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    /** 用 MediaCodec 解码音频为 PCM，再用 LAME 编码成 MP3。 */
    private fun extractToMp3(inputUri: Uri, outputName: String): OutputInfo {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var outUri: Uri? = null
        var outFile: File? = null
        var output: OutputStream? = null
        var encoder: Mp3Encoder? = null
        try {
            contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("无法读取视频文件")

            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) error("该视频没有可提取的音频轨道")

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("不支持的音频格式")
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channelCount = (if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2).coerceIn(1, 2)
            if (sampleRate <= 0) error("音频采样率无效")

            extractor.selectTrack(audioTrack)
            codec = MediaCodec.createDecoderByType(mime)
            // 显式要求解码器输出 16bit 整型 PCM。若不指定，部分解码器会给 float PCM，
            // 直接按 short 读取就会变成满幅白噪声（听感即「全是杂音」）。
            val decodeFormat = MediaFormat.createAudioFormat(
                mime,
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            ).apply {
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                setString(MediaFormat.KEY_MIME, mime)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                // 保留原始 csd-0（如 AAC 的 AudioSpecificConfig），否则部分解码器无法初始化解码。
                if (format.containsKey("csd-0")) setByteBuffer("csd-0", format.getByteBuffer("csd-0"))
            }
            codec.configure(decodeFormat, null, null, 0)
            codec.start()

            val created = createOutput("$outputName.mp3", "audio/mpeg", Environment.DIRECTORY_MUSIC, "YunoTools/Audio", true)
            outUri = created.uri
            outFile = created.file
            output = if (created.uri != null) {
                contentResolver.openOutputStream(created.uri) ?: error("无法写入输出文件")
            } else {
                FileOutputStream(created.path)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var pcmTemp = ShortArray(0)
            var floatTemp = FloatArray(0)
            var pending = ByteArray(0)
            var mp3: Mp3Encoder? = null
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!sawOutputEos) {
                if (cancelled.get()) error("已取消")

                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: error("解码输入缓冲不可用")
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            publishProgress(extractor.sampleTime)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 以解码器真实输出格式为准，容器声明可能与实际不一致。
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        val sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (sr > 0 && sr != sampleRate && mp3 == null) sampleRate = sr
                    }
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        val ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (ch > 0 && mp3 == null) channelCount = ch.coerceIn(1, 2)
                    }
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } else if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        if (mp3 == null) {
                            // 首个输出缓冲到达，此时格式已确定，再创建编码器。
                            val of = codec.outputFormat
                            if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                val sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                if (sr > 0) sampleRate = sr
                            }
                            if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                val ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                if (ch > 0) channelCount = ch.coerceIn(1, 2)
                            }
                            if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                            mp3 = Mp3Encoder(sampleRate, channelCount, 192)
                            encoder = mp3
                        }
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.get(chunk)

                        val bytesPerSample: Int
                        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            // float PCM：按 4 字节一个小端 float 解析，再换算回 16bit 整型。
                            // 若把 float 字节直接当 short 读，每个采样都会被重新解释成噪声，
                            // 听感就是「全是杂音」。
                            bytesPerSample = 4
                        } else if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
                            bytesPerSample = 1
                        } else {
                            bytesPerSample = 2
                        }
                        // 合并上次残留的字节，并只编码完整帧：块尾不足一帧的字节留到下一块。
                        // 直接丢掉尾部会让左右声道整体错位，同样会听成杂音。
                        val bytes = if (pending.isNotEmpty()) pending + chunk else chunk
                        val bytesPerFrame = bytesPerSample * channelCount
                        val frameCount = if (bytesPerFrame > 0) bytes.size / bytesPerFrame else 0
                        val usable = frameCount * bytesPerFrame
                        if (frameCount > 0) {
                            val sampleCount = frameCount * channelCount
                            if (pcmTemp.size < sampleCount) pcmTemp = ShortArray(sampleCount)
                            if (bytesPerSample == 4) {
                                if (floatTemp.size < sampleCount) floatTemp = FloatArray(sampleCount)
                                ByteBuffer.wrap(bytes, 0, usable).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floatTemp, 0, sampleCount)
                                for (i in 0 until sampleCount) {
                                    val v = floatTemp[i]
                                    pcmTemp[i] = when {
                                        v >= 1f -> Short.MAX_VALUE
                                        v <= -1f -> Short.MIN_VALUE
                                        else -> (v * 32767f).toInt().toShort()
                                    }
                                }
                            } else if (bytesPerSample == 1) {
                                // 8bit PCM 是无符号的，中心点 128。
                                for (i in 0 until sampleCount) {
                                    pcmTemp[i] = (((bytes[i].toInt() and 0xFF) - 128) shl 8).toShort()
                                }
                            } else {
                                ByteBuffer.wrap(bytes, 0, usable).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmTemp, 0, sampleCount)
                            }
                            mp3.encode(pcmTemp, frameCount, output)
                        }
                        pending = if (usable < bytes.size) bytes.copyOfRange(usable, bytes.size) else ByteArray(0)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            if (mp3 == null) error("解码器没有输出任何音频数据")
            mp3.flush(output)
            output.flush()
            output.close()
            output = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outUri != null) {
                contentResolver.update(outUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            return created
        } catch (e: Exception) {
            runCatching { output?.close() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outUri != null) runCatching { contentResolver.delete(outUri, null, null) }
            outFile?.delete()
            throw e
        } finally {
            runCatching { encoder?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun publishProgress(sampleTimeUs: Long) {
        val total = selectedDurationMs
        if (total <= 0) return
        val percent = ((sampleTimeUs / 1000.0) / total * 100).toInt().coerceIn(0, 99)
        if (percent != lastPercent) {
            lastPercent = percent
            runOnUiThread {
                binding.progressBar.progress = percent
                binding.tvProgress.text = "$percent%"
            }
        }
    }

    private data class OutputInfo(val path: String, val display: String, val uri: Uri? = null, val file: File? = null)

    private fun createOutput(fileName: String, mime: String, directory: String, subDir: String, isAudio: Boolean): OutputInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$subDir")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uri = contentResolver.insert(collection, values) ?: error("无法创建输出文件")
            val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: error("无法打开输出文件")
            OutputInfo("/proc/self/fd/${pfd.detachFd()}", "$directory/$subDir/$fileName", uri, null)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(directory), subDir).apply { mkdirs() }
            val file = uniqueFile(dir, fileName)
            OutputInfo(file.absolutePath, file.absolutePath, null, file)
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "video"
        }
        return "video"
    }

    private fun queryDurationMs(uri: Uri): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(0L)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun sanitizeName(raw: String?): String = raw.orEmpty().trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

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
}