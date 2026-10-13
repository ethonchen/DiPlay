package com.shilapi.xcertplay.network

import org.junit.Assert.*
import org.junit.Test

class LegacyApRestoreTransactionTest {
    private data class Config(val value: String)
    private class Fake : LegacyApRestoreTransaction.Port<Config> {
        var current: Config? = Config("open")
        var radio = 13
        var allowed = true
        var cancelled = false
        var clock = 0L
        var rejectTarget = false
        var ignoreTarget = false
        var rejectStart = false
        var resetOnStart = false
        var cancelOnStop = false
        var cancelOnWrite = false
        val calls = mutableListOf<String>()
        override fun read() = current
        override fun target(original: Config) = Config("secured")
        override fun matches(configuration: Config) = configuration.value == "secured"
        override fun state() = radio
        override fun canControl() = allowed
        override fun write(configuration: Config): Boolean {
            calls += "write:${configuration.value}"
            if (configuration.value == "secured" && rejectTarget) return false
            if (!(configuration.value == "secured" && ignoreTarget)) current = configuration
            if (cancelOnWrite && configuration.value == "secured") cancelled = true
            return true
        }
        override fun enable(configuration: Config?, enabled: Boolean): Boolean {
            calls += if (enabled) "on:${configuration?.value}" else "off"
            if (!enabled && cancelOnStop) cancelled = true
            if (enabled && configuration?.value == "secured" && rejectStart) return false
            radio = if (enabled) 13 else 11
            if (enabled && resetOnStart && configuration?.value == "secured") current = Config("open")
            return true
        }
        fun run() = LegacyApRestoreTransaction(this, { cancelled }, now = { clock },
            pause = { clock += it * 1_000_000L }).run(500)
    }

    @Test fun runningOpenApIsStoppedWrittenVerifiedAndRestarted() {
        val f = Fake()
        assertEquals(LegacyApRestoreTransaction.Result.READY, f.run())
        assertEquals(listOf("off", "write:secured", "on:secured"), f.calls)
        assertEquals(Config("secured"), f.current)
        assertEquals(13, f.radio)
    }
    @Test fun alreadyCorrectApNeedsNoPermissionOrMutation() {
        val f = Fake().apply { current = Config("secured"); allowed = false }
        assertEquals(LegacyApRestoreTransaction.Result.READY, f.run())
        assertTrue(f.calls.isEmpty())
    }
    @Test fun correctButOffApIsStarted() {
        val f = Fake().apply { current = Config("secured"); radio = 11 }
        assertEquals(LegacyApRestoreTransaction.Result.READY, f.run())
        assertEquals(listOf("on:secured"), f.calls)
    }
    @Test fun deniedPermissionDoesNotStopTheExistingAp() {
        val f = Fake().apply { allowed = false }
        assertEquals(LegacyApRestoreTransaction.Result.PERMISSION_REQUIRED, f.run())
        assertTrue(f.calls.isEmpty())
        assertEquals(13, f.radio)
    }
    @Test fun rejectedWriteRestoresTheOriginalRunningAp() {
        val f = Fake().apply { rejectTarget = true }
        assertEquals(LegacyApRestoreTransaction.Result.WRITE_FAILED, f.run())
        assertEquals(Config("open"), f.current)
        assertEquals(13, f.radio)
        assertFalse(f.calls.contains("on:secured"))
    }
    @Test fun acceptedButIgnoredWriteNeverReportsSuccess() {
        val f = Fake().apply { ignoreTarget = true }
        assertEquals(LegacyApRestoreTransaction.Result.TIMED_OUT, f.run())
        assertEquals(Config("open"), f.current)
        assertEquals(13, f.radio)
        assertFalse(f.calls.contains("on:secured"))
    }
    @Test fun failedRadioStartupRollsBackTheNewConfiguration() {
        val f = Fake().apply { rejectStart = true }
        assertEquals(LegacyApRestoreTransaction.Result.START_FAILED, f.run())
        assertEquals(Config("open"), f.current)
        assertEquals(13, f.radio)
    }
    @Test fun oemResetDuringStartupIsDetectedByReadback() {
        val f = Fake().apply { resetOnStart = true }
        assertEquals(LegacyApRestoreTransaction.Result.VERIFY_FAILED, f.run())
        assertEquals(Config("open"), f.current)
    }
    @Test fun cancellationBeforeEntryMakesNoChanges() {
        val f = Fake().apply { cancelled = true }
        assertEquals(LegacyApRestoreTransaction.Result.CANCELLED, f.run())
        assertTrue(f.calls.isEmpty())
    }
    @Test fun cancellationAfterStoppingRestoresTheOriginalAp() {
        val f = Fake().apply { cancelOnStop = true }
        assertEquals(LegacyApRestoreTransaction.Result.CANCELLED, f.run())
        assertEquals(Config("open"), f.current)
        assertEquals(13, f.radio)
        assertFalse(f.calls.contains("write:secured"))
    }
    @Test fun cancellationDuringTheWriteCannotStartANewSession() {
        val f = Fake().apply { cancelOnWrite = true }
        assertEquals(LegacyApRestoreTransaction.Result.CANCELLED, f.run())
        assertEquals(Config("open"), f.current)
        assertEquals(13, f.radio)
        assertFalse(f.calls.contains("on:secured"))
    }
    @Test fun missingSnapshotDoesNotStopOrOverwriteAnAp() {
        val f = Fake().apply { current = null }
        assertEquals(LegacyApRestoreTransaction.Result.UNSUPPORTED, f.run())
        assertTrue(f.calls.isEmpty())
    }
    @Test fun unknownRadioStateIsBoundedAndNeverWrittenBlindly() {
        val f = Fake().apply { radio = 12 }
        assertEquals(LegacyApRestoreTransaction.Result.TIMED_OUT, f.run())
        assertTrue(f.calls.isEmpty())
    }
}
