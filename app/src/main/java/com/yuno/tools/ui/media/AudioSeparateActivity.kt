package com.yuno.tools.ui.media

import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuno.tools.databinding.ActivityAudioSeparateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class AudioSeparateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAudioSeparateBinding
    private var selectedUri: Uri? = null

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        selectedUri = uri
        val displayName = queryDisplayName(uri)
        val baseName = displayName.substringBeforeLast('.', displayName)
        binding.tvVideoInfo.text = "已选择：$displayName"
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
    }

    private fun extractSelectedAudio() {
        val uri = selectedUri ?: run {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show()
            return
        }
        val outputName = sanitizeName(binding.etAudioName.text?.toString()).ifBlank { "Yuno_audio_${System.currentTimeMillis()}" }
        binding.btnExtract.isEnabled = false
        binding.btnExtract.text = "正在分离..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { extractAudio(uri, outputName) } }
            binding.btnExtract.isEnabled = true
            binding.btnExtract.text = "开始分离音频"
            result.onSuccess {
                Toast.makeText(this@AudioSeparateActivity, "音频已保存：$it", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@AudioSeparateActivity, "分离失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractAudio(inputUri: Uri, outputName: String): String {
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
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
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
}
