package com.yuno.tools.util

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.OutputStream

/**
 * 基于 jump3r（LAME 的纯 Java 移植）的 MP3 编码器。
 *
 * Android 平台没有系统级 MP3 编码器（MediaCodec 仅支持 MP3 解码），
 * 因此这里直接使用 LAME 的低层接口，把 PCM 采样编码为 MP3 码流。
 *
 * 注意：jump3r 的高层封装类 LameEncoder 依赖 java.desktop 中的
 * javax.sound.sampled，Android 上不存在该包，所以打包时已剔除该类，
 * 只保留 de.sciss.jump3r.mp3 / de.sciss.jump3r.mpg 下的核心编码类。
 */
class Mp3Encoder(
    private val sampleRate: Int,
    private val channels: Int,
    private val bitrateKbps: Int = 192
) {
    private val lame = Lame()
    private val bs = BitStream()
    private val id3 = ID3Tag()
    private val gfp: LameGlobalFlags
    private var mp3Buffer = ByteArray(8192)

    init {
        val ga = GainAnalysis()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbrTag = VBRTag()
        val version = Version()
        val reservoir = Reservoir()
        val takehiro = Takehiro()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        // 按 LAME 原始顺序装配各模块，缺一不可。
        lame.setModules(ga, bs, presets, qupvt, qu, vbrTag, version, id3, mpg)
        bs.setModules(ga, mpg, version, vbrTag)
        id3.setModules(bs, version)
        presets.setModules(lame)
        qu.setModules(bs, reservoir, qupvt, takehiro)
        qupvt.setModules(takehiro, reservoir, lame.enc.psy)
        reservoir.setModules(bs)
        takehiro.setModules(qupvt)
        vbrTag.setModules(lame, bs, version)
        mpg.setModules(intf, common)
        intf.setModules(vbrTag, common)

        gfp = lame.lame_init()
        gfp.num_channels = channels.coerceIn(1, 2)
        gfp.in_samplerate = sampleRate
        gfp.brate = bitrateKbps
        gfp.mode = if (channels >= 2) MPEGMode.JOINT_STEREO else MPEGMode.MONO
        gfp.VBR = VbrMode.vbr_off
        gfp.quality = 5

        // 不写 ID3 与 LAME 头信息，避免依赖未初始化的标签结构。
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false
        gfp.bWriteVbrTag = false

        val rc = lame.lame_init_params(gfp)
        require(rc >= 0) { "MP3 编码器初始化失败（code=$rc）" }
    }

    /**
     * 编码一段交错立体声 PCM（16bit，先左后右）。
     * 返回写出的 MP3 字节数。
     */
    fun encode(interleaved: ShortArray, frames: Int, out: OutputStream): Int {
        if (frames <= 0) return 0
        val left = IntArray(frames)
        val right = IntArray(frames)
        if (gfp.num_channels == 1) {
            for (i in 0 until frames) left[i] = interleaved[i].toInt()
            for (i in 0 until frames) right[i] = left[i]
        } else {
            var i = 0
            while (i < frames) {
                val l = interleaved[i * 2].toInt()
                val r = interleaved[i * 2 + 1].toInt()
                left[i] = l
                right[i] = r
                i++
            }
        }
        return encodeInts(left, right, frames, out)
    }

    private fun encodeInts(left: IntArray, right: IntArray, frames: Int, out: OutputStream): Int {
        if (mp3Buffer.size < Lame.LAME_MAXMP3BUFFER) mp3Buffer = ByteArray(Lame.LAME_MAXMP3BUFFER)
        val n = lame.lame_encode_buffer_int(gfp, left, right, frames, mp3Buffer, 0, mp3Buffer.size)
        if (n > 0) out.write(mp3Buffer, 0, n)
        return if (n > 0) n else 0
    }

    /** 冲刷编码器尾部缓冲，写出剩余帧。 */
    fun flush(out: OutputStream): Int {
        if (mp3Buffer.size < (Lame.LAME_MAXMP3BUFFER + 7200)) mp3Buffer = ByteArray(Lame.LAME_MAXMP3BUFFER + 7200)
        var total = 0
        val n = lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
        if (n > 0) {
            out.write(mp3Buffer, 0, n)
            total = n
        }
        return total
    }

    fun close() {
        runCatching { lame.lame_close(gfp) }
    }
}
