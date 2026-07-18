package com.nexusmedia.data

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.BassBoost
import android.media.audiofx.NoiseSuppressor

/**
 * Audio enhancement engine for NexusMedia.
 * Features: noise cleaning, sound boosting, EQ, bass boost, loudness enhancement.
 * 
 * Integration: Call from MediaViewModel with mediaPlayer.audioSessionId.
 */
object AudioEnhancer {

    /** Noise suppression (noise cleaner) — requires AUDIO_SESSION_ID from active MediaPlayer */
    fun applyNoiseCleaner(audioSessionId: Int): NoiseSuppressor? {
        return try {
            NoiseSuppressor.create(audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            null // Not supported on all devices / requires microphone permission for some implementations
        }
    }

    /** Sound booster: LoudnessEnhancer increases perceived volume beyond 100% (up to +15dB) */
    fun applySoundBooster(audioSessionId: Int, targetGainDb: Float = 12.0f): LoudnessEnhancer? {
        return try {
            LoudnessEnhancer(audioSessionId).apply { enabled = true; setTargetGain(targetGainDb.toInt() * 100) }
        } catch (e: Exception) {
            null
        }
    }

    /** Bass boost — adds low-frequency enhancement */
    fun applyBassBoost(audioSessionId: Int, strength: Short = 300): BassBoost? {
        return try {
            BassBoost(0, audioSessionId).apply { enabled = true; setStrength(strength) }
        } catch (e: Exception) {
            null
        }
    }

    /** Full EQ — multi-band equalizer (5 bands typical) */
    fun applyEqualizer(audioSessionId: Int): Equalizer? {
        return try {
            Equalizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            null
        }
    }

    /** Combined enhancement profile: noise cleaner + sound booster + EQ reference */
    fun applyFullEnhancement(audioSessionId: Int): List<Any> {
        val effects = mutableListOf<Any>()
        applyNoiseCleaner(audioSessionId)?.let { effects.add(it) }
        applySoundBooster(audioSessionId)?.let { effects.add(it) }
        applyBassBoost(audioSessionId)?.let { effects.add(it) }
        applyEqualizer(audioSessionId)?.let { effects.add(it) }
        return effects
    }
}
