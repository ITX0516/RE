package com.badukai.next.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.badukai.next.R

/**
 * Plays stone placement and capture sounds from raw resources.
 */
class StoneSoundPlayer(context: Context) {

    private var soundPool: SoundPool? = null
    private var loaded = false

    private val placeSoundIds = IntArray(2)
    private var captureSoundId = 0
    private var selectedPlace = 0 // index into placeSoundIds

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()

        var loadedCount = 0
        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loadedCount++
            if (loadedCount >= 3) loaded = true
        }

        placeSoundIds[0] = soundPool?.load(context, R.raw.stone_place_1, 1) ?: 0
        placeSoundIds[1] = soundPool?.load(context, R.raw.stone_place_2, 1) ?: 0
        captureSoundId = soundPool?.load(context, R.raw.stone_capture, 1) ?: 0
    }

    fun setPlaceSound(index: Int) {
        selectedPlace = index.coerceIn(0, 1)
    }

    fun playPlace() {
        if (loaded) {
            val id = placeSoundIds[selectedPlace]
            if (id != 0) soundPool?.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun playCapture() {
        if (loaded) {
            if (captureSoundId != 0) soundPool?.play(captureSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }
}
