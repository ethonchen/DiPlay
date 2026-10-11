package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.CarPlayAudioDuckingCommand
import org.junit.Assert.*
import org.junit.Test

class NavigationDuckingStateTest {
    @Test fun fadesAndRestoresAfterQueuedSpeechEvenWhenStreamStaysOpen() {
        val state = NavigationDuckingState()
        val token = state.open()
        state.played(token, 0, 200)
        assertEquals(1f, state.gain(0), 0.001f)
        assertEquals(0.625f, state.gain(60), 0.001f)
        assertEquals(0.25f, state.gain(120), 0.001f)
        assertEquals(0.25f, state.gain(549), 0.001f)
        assertEquals(0.25f, state.gain(550), 0.001f)
        assertEquals(0.625f, state.gain(700), 0.001f)
        assertEquals(1f, state.gain(850), 0.001f)
        assertNull(state.nextDelay(850))
    }

    @Test fun overlappingSpeechAndCommandsCannotRestoreEachOther() {
        val state = NavigationDuckingState()
        val first = state.open(); val second = state.open(); val owner = Any()
        state.played(first, 0, 1000)
        state.played(second, 100, 1000)
        state.command(owner, CarPlayAudioDuckingCommand(0.1f, 0, true), 100)
        state.stop(first, 200)
        assertEquals(0.1f, state.gain(200), 0.001f)
        state.command(owner, CarPlayAudioDuckingCommand(1f, 0, false), 200)
        assertEquals(0.25f, state.gain(200), 0.001f)
        state.stop(second, 200)
        assertEquals(1f, state.gain(500), 0.001f)
        state.played(first, 600, 1000)
        assertEquals(1f, state.gain(720), 0.001f)
    }

    @Test fun commandFadeIsNotASpeechTimeoutAndLostUnduckEventuallyRecovers() {
        val state = NavigationDuckingState(); val owner = Any()
        state.command(owner, CarPlayAudioDuckingCommand(0.2f, 100, true), 0)
        assertEquals(0.2f, state.gain(1000), 0.001f)
        assertEquals(0.2f, state.gain(60_000), 0.001f)
        assertEquals(1f, state.gain(60_300), 0.001f)
        state.command(owner, CarPlayAudioDuckingCommand(0.2f, 0, true), 61_000)
        state.releaseOwner(owner, 61_000)
        state.command(owner, CarPlayAudioDuckingCommand(0.1f, 0, true), 61_100)
        assertEquals(1f, state.gain(61_300), 0.001f)
        state.clear()
        state.played(state.open(), 62_000, 1000)
        assertEquals(1f, state.gain(62_200), 0.001f)
    }

    @Test fun detectsSignedLittleEndianPcmButIgnoresSilenceAndQuietKeepalives() {
        assertFalse(audibleNavigationPcm(ByteArray(1024), 0, 1024))
        assertFalse(audibleNavigationPcm(byteArrayOf(31, 0, -31, -1), 0, 4))
        assertTrue(audibleNavigationPcm(byteArrayOf(32, 0), 0, 2))
        assertTrue(audibleNavigationPcm(byteArrayOf(0, -128), 0, 2))
        assertFalse(audibleNavigationPcm(byteArrayOf(32, 0, 0, 0), 2, 2))
    }
}
