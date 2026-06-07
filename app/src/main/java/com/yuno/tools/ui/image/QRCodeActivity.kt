package com.yuno.tools.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.yuno.tools.databinding.ActivityQrcodeBinding

class QRCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "二维码生成"
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnGenerate.setOnClickListener {
            val text = binding.etText.text.toString()
            if (text.isNotEmpty()) {
                try {
                    val bitmap = generateQRCode(text, 512)
                    binding.ivQRCode.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun generateQRCode(text: String, size: Int): Bitmap {
        val writer = MultiFormatWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
