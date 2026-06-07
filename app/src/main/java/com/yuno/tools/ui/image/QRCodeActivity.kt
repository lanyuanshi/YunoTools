package com.yuno.tools.ui.image

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class QRCodeActivity : AppCompatActivity() {
    private var currentBitmap: Bitmap? = null
    private var foregroundColor = Color.BLACK
    private var backgroundColor = Color.WHITE
    private lateinit var ivQRCode: ImageView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnShare: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        val etText = findViewById<android.widget.EditText>(R.id.etText)
        ivQRCode = findViewById(R.id.ivQRCode)
        val btnGenerate = findViewById<MaterialButton>(R.id.btnGenerate)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        val logoSwitch = findViewById<SwitchMaterial>(R.id.switchLogo)

        bindColor(findViewById(R.id.colorBlack), true, Color.BLACK)
        bindColor(findViewById(R.id.colorBlue), true, Color.rgb(0, 122, 255))
        bindColor(findViewById(R.id.colorGreen), true, Color.rgb(52, 199, 89))
        bindColor(findViewById(R.id.colorPink), true, Color.rgb(255, 45, 85))
        bindColor(findViewById(R.id.bgWhite), false, Color.WHITE)
        bindColor(findViewById(R.id.bgWarm), false, Color.rgb(255, 247, 230))
        bindColor(findViewById(R.id.bgBlue), false, Color.rgb(236, 245, 255))

        btnGenerate.setOnClickListener {
            val text = etText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "请输入二维码内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runCatching {
                currentBitmap = generateQRCode(text, 960, logoSwitch.isChecked)
                ivQRCode.setImageBitmap(currentBitmap)
                ivQRCode.isVisible = true
                btnSave.isVisible = true
                btnShare.isVisible = true
            }.onFailure { Toast.makeText(this, "生成失败：${it.message}", Toast.LENGTH_SHORT).show() }
        }
        btnSave.setOnClickListener { saveQRCode() }
        btnShare.setOnClickListener { shareQRCode() }
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun bindColor(view: LinearLayout, isForeground: Boolean, color: Int) {
        view.setOnClickListener {
            if (isForeground) foregroundColor = color else backgroundColor = color
            Toast.makeText(this, if (isForeground) "已选择前景色" else "已选择背景色", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateQRCode(text: String, size: Int, withLogo: Boolean): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) foregroundColor else backgroundColor)
        if (!withLogo) return bitmap
        val canvas = Canvas(bitmap)
        val logoSize = size * 0.18f
        val left = (size - logoSize) / 2f
        val top = (size - logoSize) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(left - 18, top - 18, left + logoSize + 18, top + logoSize + 18), 28f, 28f, paint)
        val logo = Bitmap.createScaledBitmap(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher), logoSize.toInt(), logoSize.toInt(), true)
        canvas.drawBitmap(logo, left, top, null)
        return bitmap
    }

    private fun saveQRCode() {
        val bitmap = currentBitmap ?: return
        val fileName = "qrcode_${System.currentTimeMillis()}.png"
        runCatching {
            val bytes = bitmap.toPngBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YunoTools")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunoTools")
                dir.mkdirs(); FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
            }
            Toast.makeText(this, "已保存到相册/YunoTools", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show() }
    }

    private fun shareQRCode() {
        val bitmap = currentBitmap ?: return
        runCatching {
            val file = File(cacheDir, "share_qrcode.png")
            FileOutputStream(file).use { it.write(bitmap.toPngBytes()) }
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "分享二维码"))
        }.onFailure { Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show() }
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
