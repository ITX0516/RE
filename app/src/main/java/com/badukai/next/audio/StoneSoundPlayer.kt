package com.badukai.next.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class StoneSoundPlayer(context: Context) {

    private var soundPool: SoundPool? = null
    private val stoneLoadId = IntArray(2) // 0=place, 1=capture
    private var loaded = false
    private val audioDir: File

    init {
        audioDir = File(context.cacheDir, "sounds")
        audioDir.mkdirs()
    }

    fun load() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.setOnLoadCompleteListener { _, _, _ -> loaded = true }

        // Generate sounds
        val placeFile = generateWav(audioDir, "stone_place.wav", 1800f, 60, 0.6f)
        val captureFile = generateWav(audioDir, "stone_capture.wav", 1200f, 80, 0.5f)

        stoneLoadId[0] = soundPool?.load(placeFile.absolutePath, 1) ?: 0
        stoneLoadId[1] = soundPool?.load(captureFile.absolutePath, 1) ?: 0
    }

    fun playPlace() {
        if (loaded) {
            soundPool?.play(stoneLoadId[0], 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun playCapture() {
        if (loaded) {
            soundPool?.play(stoneLoadId[1], 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }

    private fun generateWav(dir: File, name: String, freq: Float, durationMs: Int, volume: Float): File {
        val file = File(dir, name)
        if (file.exists()) return file

        val sampleRate = 22050
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val envelope = Math.exp((-5.0 * i) / numSamples).toFloat()
            val sample = (volume * Math.sin(2.0 * Math.PI * freq * t).toFloat() * envelope * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // WAV header
        val dataSize = numSamples * 2 // 16-bit
        val fileSize = 36 + dataSize

        writeWavHeader(dos, sampleRate, 16, 1, dataSize, fileSize)
        for (s in samples) dos.writeShort(s.toInt())
        dos.flush()

        FileOutputStream(file).use { it.write(bos.toByteArray()) }
        return file
    }

    private fun writeWavHeader(dos: DataOutputStream, sampleRate: Int, bitsPerSample: Int, channels: Int, dataSize: Int, fileSize: Int) {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign

        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(fileSize))
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16)) // subchunk1 size
        dos.writeShort(Integer.reverseBytes(1)) // PCM
        dos.writeShort(Integer.reverseBytes(channels))
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(byteRate))
        dos.writeShort(Integer.reverseBytes(blockAlign))
        dos.writeShort(Integer.reverseBytes(bitsPerSample))
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(dataSize))
    }
}
