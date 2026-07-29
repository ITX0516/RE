package com.badukai.next.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.badukai.next.R

/**
 * Plays stone placement sounds from raw resources.
 * Loads all 5 place sounds for user selection.
 */
class StoneSoundPlayer(context: Context) {

    private var soundPool: SoundPool? = null
    private var loaded = false

    val placeCount = 5
    private val placeSoundIds = IntArray(placeCount)
    private var selectedPlace = 0

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
            if (loadedCount >= placeCount + 1) loaded = true
        }

        placeSoundIds[0] = soundPool?.load(context, R.raw.stone_place_1, 1) ?: 0
        placeSoundIds[1] = soundPool?.load(context, R.raw.stone_place_2, 1) ?: 0
        placeSoundIds[2] = soundPool?.load(context, R.raw.stone_place_3, 1) ?: 0
        placeSoundIds[3] = soundPool?.load(context, R.raw.stone_place_4, 1) ?: 0
        placeSoundIds[4] = soundPool?.load(context, R.raw.stone_place_5, 1) ?: 0
    }

    fun setPlaceSound(index: Int) {
        selectedPlace = index.coerceIn(0, placeCount - 1)
    }

    fun playPlace() {
        if (loaded) {
            val id = placeSoundIds[selectedPlace]
            if (id != 0) soundPool?.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun playCapture() {
        // Use same selected place sound for capture
        playPlace()
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }
}
