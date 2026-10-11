package com.shilapi.xcertplay.airplay

import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], manifest = Config.NONE)
class CarPlayAudioDuckingCommandTest {
    @Test fun convertsDbAndClampsUntrustedValues() {
        val command = CarPlayAudioDuckingCommand.parse("duckAudio", mapOf("volume" to -12.0, "durationMs" to 250L))!!
        assertEquals(0.25118864f, command.gain, 0.00001f)
        assertEquals(250L, command.fadeMillis)
        assertEquals(1f, CarPlayAudioDuckingCommand.parse("duckAudio", mapOf("volume" to 100))!!.gain, 0f)
        assertEquals(2000L, CarPlayAudioDuckingCommand.parse("duckAudio", mapOf("durationMs" to 99999))!!.fadeMillis)
        assertEquals(0L, CarPlayAudioDuckingCommand.parse("unduckAudio", mapOf("durationMs" to -1))!!.fadeMillis)
        assertNull(CarPlayAudioDuckingCommand.parse("duckAudio", mapOf("volume" to Double.NaN)))
        assertNull(CarPlayAudioDuckingCommand.parse("duckAudio", mapOf("durationMs" to Double.POSITIVE_INFINITY)))
        assertNull(CarPlayAudioDuckingCommand.parse("modesChanged", emptyMap()))
        assertEquals(0.25f, CarPlayAudioDuckingCommand.parse("duckAudio", emptyMap())!!.gain, 0f)
    }

    @Test fun rtspCommandReachesSinkWithSessionOwnershipAndTeardownEndsIt() {
        val received = mutableListOf<Pair<Any, CarPlayAudioDuckingCommand>>()
        val ended = mutableListOf<Any>()
        val engine = CarPlayMediaEngine(object : MediaSink {
            override fun onAudioDucking(owner: Any, command: CarPlayAudioDuckingCommand) { received.add(owner to command) }
            override fun onAudioDuckingEnded(owner: Any) { ended.add(owner) }
        })
        val session = AirPlaySession(Socket(),
            AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:01",
                sourceVersion = "1.0", main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480)),
            AirPlayIdentity.generate(), PairingStore(), null, object : AirPlaySessionListener {}, engine)
        try {
            val handle = AirPlaySession::class.java.getDeclaredMethod("handle", RtspMessage.Request::class.java)
                .apply { isAccessible = true }
            for (type in listOf("duckAudio", "unduckAudio")) {
                val request = RtspMessage.Request("POST", "/command", "RTSP/1.0", emptyMap(),
                    BplistCodec.encode(mapOf("type" to type, "params" to mapOf("volume" to -12.0, "durationMs" to 200L))))
                assertEquals(200, (handle.invoke(session, request) as RtspMessage.Response).status)
            }
            assertEquals(2, received.size)
            assertSame(session, received[0].first)
            assertTrue(received[0].second.duck)
            assertFalse(received[1].second.duck)
        } finally { session.close() }
        assertEquals(listOf(session), ended)
        engine.onAudioDucking(session, CarPlayAudioDuckingCommand(0.1f, 0, true))
        assertEquals(2, received.size)
    }
}
