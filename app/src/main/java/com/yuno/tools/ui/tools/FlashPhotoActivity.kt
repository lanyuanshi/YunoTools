package com.yuno.tools.ui.tools

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlashPhotoActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var emptyText: TextView
    private lateinit var infoText: TextView
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button

    private var importedFile: File? = null
    private var importedMimeType: String = "image/jpeg"
    private var importedDisplayName: String = "flash_photo.jpg"
    private var importedSize: Long = 0L
    private var importedWidth: Int = 0
    private var importedHeight: Int = 0

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        importSelectedImage(uri)
    }

    private val requestLegacyWritePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveImportedCopy() else Toast.makeText(this, "需要存储权限才能保存到相册", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flash_photo)
        ThemeApplier.apply(this)

        preview = findViewById(R.id.imagePreview)
        emptyText = findViewById(R.id.textEmpty)
        infoText = findViewById(R.id.textInfo)
        saveButton = findViewById(R.id.btnSave)
        shareButton = findViewById(R.id.btnShare)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnPick).setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        saveButton.setOnClickListener { saveImportedCopyWithPermission() }
        shareButton.setOnClickListener { shareImportedCopy() }
        refreshEmptyState()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out)
    }

    private fun importSelectedImage(uri: Uri) {
        try {
            val sourceName = queryDisplayName(uri) ?: "flash_photo_${timestamp()}.jpg"
            val mimeType = contentResolver.getType(uri) ?: mimeTypeFromName(sourceName)
            val extension = extensionFromName(sourceName, mimeType)
            val fileName = "flash_photo_${timestamp()}.$extension"
            val targetDir = File(filesDir, "flash_photos").apply { mkdirs() }
            val targetFile = File(targetDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法读取选择的文件")

            importedFile = targetFile
            importedMimeType = mimeType
            importedDisplayName = sourceName
            importedSize = targetFile.length()
            readImageBounds(targetFile)
            showImportedFile(targetFile)
            Toast.makeText(this, "已导入图片副本", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败：${e.message ?: "文件无法读取"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImportedFile(file: File) {
        emptyText.visibility = View.GONE
        preview.visibility = View.VISIBLE
        Glide.with(this).load(file).into(preview)
        saveButton.isEnabled = true
        shareButton.isEnabled = true
        infoText.text = buildString {
            appendLine("原始名称：$importedDisplayName")
            appendLine("副本大小：${formatBytes(importedSize)}")
            appendLine("文件类型：$importedMimeType")
            if (importedWidth > 0 && importedHeight > 0) appendLine("图片尺寸：${importedWidth} × $importedHeight")
            append("应用副本：${file.absolutePath}")
        }
    }

    private fun refreshEmptyState() {
        if (importedFile == null) {
            emptyText.visibility = View.VISIBLE
            preview.visibility = View.GONE
            saveButton.isEnabled = false
            shareButton.isEnabled = false
            infoText.text = "尚未导入图片"
        }
    }

    private fun saveImportedCopyWithPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLegacyWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveImportedCopy()
    }

    private fun saveImportedCopy() {
        val source = importedFile ?: return
        try {
            val savedName = "YunoFlash_${timestamp()}.${extensionFromName(source.name, importedMimeType)}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, savedName)
                    put(MediaStore.Images.Media.MIME_TYPE, importedMimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YunoTools/FlashPhoto")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("无法创建相册文件")
                contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunoTools/FlashPhoto")
                dir.mkdirs()
                source.copyTo(File(dir, savedName), overwrite = true)
            }
            Toast.makeText(this, "已保存到相册 YunoTools/FlashPhoto", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message ?: "无法写入相册"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImportedCopy() {
        val source = importedFile ?: return
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", source)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = importedMimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享闪照副本"))
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return null
    }

    private fun readImageBounds(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        importedWidth = options.outWidth
        importedHeight = options.outHeight
    }

    private fun mimeTypeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> "image/jpeg"
    }

    private fun extensionFromName(name: String, mimeType: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")) return ext
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "jpg"
        }
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
