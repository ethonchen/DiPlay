package com.shilapi.xcertplay.airplay

import kotlin.math.pow

/** iPhone /command volume is in dB; durationMs describes the fade, not the speech length. */
data class CarPlayAudioDuckingCommand(val gain: Float, val fadeMillis: Long, val duck: Boolean) {
    companion object {
        fun parse(type: String, params: Map<String, Any?>): CarPlayAudioDuckingCommand? {
            val duck = when (type) {
                "duckAudio" -> true
                "unduckAudio" -> false
                else -> return null
            }
            val db = (params["volume"] as? Number)?.toDouble()
            val duration = (params["durationMs"] as? Number)?.toDouble()
            if (db != null && !db.isFinite() || duration != null && !duration.isFinite()) return null
            val gain = if (!duck) 1f else db?.let { 10.0.pow(it.coerceIn(-60.0, 0.0) / 20.0).toFloat() } ?: 0.25f
            return CarPlayAudioDuckingCommand(gain,
                duration?.coerceIn(0.0, 2000.0)?.toLong() ?: if (duck) 120L else 300L, duck)
        }
    }
}
