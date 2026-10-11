package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.CarPlayAudioDuckingCommand
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.abs

/** Pure timing policy. Its owner serializes calls and applies the resulting gain only to music. */
internal class NavigationDuckingState {
    class Token { internal var closed = false }
    private data class Lease(val gain: Float, val expires: Long)
    private val speech = IdentityHashMap<Token, Lease>()
    private val commands = IdentityHashMap<Any, Lease>()
    private val retiredOwners = WeakHashMap<Any, Boolean>()
    private var from = 1f
    private var target = 1f
    private var started = 0L
    private var duration = 0L
    private var closed = false

    fun open() = Token().also { it.closed = closed }

    fun played(token: Token, now: Long, queuedMillis: Long) {
        if (closed || token.closed) return
        speech[token] = Lease(0.25f, now + queuedMillis.coerceIn(0, 5000) + 350)
        refresh(now)
    }

    fun stop(token: Token, now: Long) {
        token.closed = true
        speech.remove(token)
        refresh(now)
    }

    fun command(owner: Any, command: CarPlayAudioDuckingCommand, now: Long) {
        if (closed || retiredOwners.containsKey(owner)) return
        if (command.duck) {
            // An absent unduck must not leave music attenuated for the rest of the drive.
            commands[owner] = Lease(command.gain.coerceIn(0f, 1f), now + 60_000)
        } else {
            commands.remove(owner)
        }
        refresh(now, command.fadeMillis)
    }

    fun releaseOwner(owner: Any, now: Long) {
        retiredOwners[owner] = true
        commands.remove(owner)
        refresh(now)
    }

    fun gain(now: Long): Float {
        refresh(now)
        return interpolated(now)
    }

    fun targetGain() = target

    fun nextDelay(now: Long): Long? {
        if (closed) return null
        if (now < started + duration) return minOf(20L, started + duration - now)
        return (speech.values + commands.values).minOfOrNull { it.expires }
            ?.let { (it - now).coerceAtLeast(1) }
    }

    fun clear() {
        closed = true
        speech.keys.forEach { it.closed = true }
        speech.clear()
        commands.clear()
        retiredOwners.clear()
        from = 1f
        target = 1f
        duration = 0
    }

    private fun refresh(now: Long, fadeMillis: Long? = null) {
        speech.entries.removeAll { it.value.expires <= now }
        commands.entries.removeAll { it.value.expires <= now }
        val next = (speech.values + commands.values).minOfOrNull { it.gain } ?: 1f
        if (next == target) return
        from = interpolated(now)
        target = next
        started = now
        duration = fadeMillis?.coerceIn(0, 2000) ?: if (next < from) 120L else 300L
    }

    private fun interpolated(now: Long): Float {
        if (duration == 0L) return target
        val fraction = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
        return from + (target - from) * fraction
    }
}

/** Ignore silent keepalive PCM so an open navigation stream cannot permanently duck music. */
internal fun audibleNavigationPcm(data: ByteArray, offset: Int, length: Int): Boolean {
    var index = offset
    val end = minOf(data.size, offset + length) - 1
    while (index < end) {
        val sample = (data[index].toInt() and 0xff) or (data[index + 1].toInt() shl 8)
        if (abs(sample) >= 32) return true
        index += 2
    }
    return false
}
