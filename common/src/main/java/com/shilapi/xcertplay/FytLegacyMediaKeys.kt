package com.shilapi.xcertplay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RemoteControlClient
import android.media.session.MediaSession
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.util.Locale

/** FYT Android 6 also uses the pre-MediaSession receiver/RCC and stock-music command broadcasts. */
@Suppress("DEPRECATION")
internal object FytLegacyMediaKeys {
    private const val MUSIC = "com.android.music.musicservicecommand"
    private const val EXTERNAL = "com.syu.ms.action.external_command"
    private val actions = listOf(Intent.ACTION_MEDIA_BUTTON, MUSIC, EXTERNAL,
        "com.android.music.next", "com.android.music.previous", "com.android.music.togglepause", "com.android.music.pause")
    @Volatile private var current: Registration? = null
    @Volatile private var state = "inactive"

    fun start(context: Context, session: MediaSession, expected: CarPlayController) {
        if (!FytWheelKeys.legacyFyt(Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL, Build.BOARD) ||
            AirPlayPersistence.loadCarBluetoothAudio(context)) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        install(context, session, audio) { index, source -> CarPlayMediaKeys.onLegacyFytMediaKey(expected, index, source) }
    }

    /** Own one registration at a time; the explicit receiver and live receiver share this owner. */
    @SuppressLint("UnspecifiedImmutableFlag") // Android 6 fills the KeyEvent into this explicit mutable PendingIntent.
    internal fun install(context: Context, session: MediaSession, audio: AudioManager,
        forward: (Int, String) -> Boolean) {
        stop()
        val app = context.applicationContext
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(app, FytLegacyMediaReceiver::class.java))
        val pending = PendingIntent.getBroadcast(app, 744, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val registration = Registration(app, audio, pending, forward)
        current = registration
        val sessionReceiver = runCatching { session.setMediaButtonReceiver(pending) }.isSuccess
        registration.register()
        state = "sessionReceiver=$sessionReceiver ${registration.status()}"
        note("registered $state")
    }

    fun updatePlayback(playing: Boolean) { current?.updatePlayback(playing) }
    fun stop() {
        val old = current
        current = null
        old?.stop()
        state = "inactive"
    }
    fun report(): String = "FYT legacy media active=${current != null} state=$state"
    fun receive(intent: Intent, source: String): Boolean {
        val index = mediaIndex(intent)
        val owner = current
        val action = intent.action?.takeIf { it in actions } ?: "<unsupported>"
        note("broadcast source=$source action=$action mapped=$index active=${owner != null}")
        if (index == null || owner == null) return false
        return owner.forward(index, "fyt-legacy-$source")
    }

    internal fun mediaIndex(intent: Intent): Int? {
        val action = intent.action ?: return null
        if (action !in actions) return null
        if (action == Intent.ACTION_MEDIA_BUTTON) {
            val event = runCatching { intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) }.getOrNull() ?: return null
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null
            return CarPlayMediaButton.forKeyCode(event.keyCode)
        }
        val direct = when (action) {
            "com.android.music.next" -> "next"
            "com.android.music.previous" -> "previous"
            "com.android.music.togglepause" -> "togglepause"
            "com.android.music.pause" -> "pause"
            else -> null
        }
        val command = direct ?: listOf("command", "cmd", "action", "action_command").firstNotNullOfOrNull { key ->
            runCatching { intent.getStringExtra(key) }.getOrNull()
        }
        val mapped = when (command?.lowercase(Locale.ROOT)) {
            "next", "skip_next", "skip", "forward", "skip_forward" -> CarPlayMediaButton.NEXT
            "previous", "skip_previous", "prev", "rewind", "back", "skip_back", "skip_backward" -> CarPlayMediaButton.PREVIOUS
            "play", "start", "resume" -> CarPlayMediaButton.PLAY
            "pause", "stop" -> CarPlayMediaButton.PAUSE
            "togglepause", "playpause", "play_pause", "media_play_pause" -> CarPlayMediaButton.PLAY_PAUSE
            else -> null
        }
        if (mapped != null) return mapped
        // Only documented Android media codes; unknown vendor integers are never guessed.
        for (key in listOf("keycode", "key_code")) {
            val code = runCatching { intent.getIntExtra(key, -1) }.getOrDefault(-1)
            CarPlayMediaButton.forKeyCode(code)?.let { return it }
        }
        return null
    }

    private fun note(message: String) {
        WheelKeyLog.note("FYT legacy $message")
        Log.i("DiPlay-FytLegacy", "FYT legacy $message")
    }

    private class Registration(
        private val context: Context,
        private val audio: AudioManager,
        private val pending: PendingIntent,
        val forward: (Int, String) -> Boolean,
    ) {
        private var live = false
        private var button = false
        private var remote: RemoteControlClient? = null
        private var remoteRegistered = false
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (current !== this@Registration) return
                if (receive(intent, "dynamic") && isOrderedBroadcast) abortBroadcast()
            }
        }
        fun register() {
            live = runCatching {
                val filter = IntentFilter().apply { priority = 1000; actions.forEach(::addAction) }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            }.onFailure { note("live receiver failed=${it.javaClass.simpleName}") }.isSuccess
            button = runCatching { audio.registerMediaButtonEventReceiver(pending) }
                .onFailure { note("button receiver failed=${it.javaClass.simpleName}") }.isSuccess
            runCatching {
                RemoteControlClient(pending).also { client ->
                    // Keep the handle even if a later call fails, so teardown still unregisters it.
                    remote = client
                    client.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                        RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS or RemoteControlClient.FLAG_KEY_MEDIA_PLAY or
                        RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE)
                    audio.registerRemoteControlClient(client)
                    remoteRegistered = true
                }
            }.onFailure { note("remote control registration failed=${it.javaClass.simpleName}") }
        }
        fun updatePlayback(playing: Boolean) {
            runCatching { remote?.setPlaybackState(if (playing) RemoteControlClient.PLAYSTATE_PLAYING else RemoteControlClient.PLAYSTATE_PAUSED) }
        }
        fun status(): String = "liveReceiver=$live buttonReceiver=$button remoteControl=$remoteRegistered"
        fun stop() {
            remote?.let { runCatching { audio.unregisterRemoteControlClient(it) } }
            remote = null
            remoteRegistered = false
            if (button) runCatching { audio.unregisterMediaButtonEventReceiver(pending) }
            if (live) runCatching { context.unregisterReceiver(receiver) }
            runCatching { pending.cancel() }
            live = false
            button = false
        }
    }
}

/** Also works for firmware that delivers to an explicit PendingIntent instead of live receivers. */
class FytLegacyMediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (FytLegacyMediaKeys.receive(intent, "manifest") && isOrderedBroadcast) abortBroadcast()
    }
}
