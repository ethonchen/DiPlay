package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class FytWheelKeysTest {
    @Test fun usesLegacyMainModuleAndNoReplayAndAcceptsEmptyVoidReplies() {
        val f = Fixture()
        f.start()
        assertEquals(0, f.module.registrations.size) // No synchronous IPC from the UI callback.
        f.drain()
        assertEquals(0, f.toolkit.requestedModule)
        assertEquals(listOf(37 to 0, 60 to 0), f.module.registrations)
        assertEquals(1, f.module.callbacks.values.distinct().size)
        f.connection.stop()
        f.drain()
        assertEquals(listOf(37, 60), f.module.unregistered)
        assertEquals(1, f.context.unbinds)
    }

    @Test fun oldPlayerCommandsReachTheActiveCarPlayControllerAndStopAfterDetach() {
        val app = RuntimeEnvironment.getApplication()
        val controller = mock(CarPlayController::class.java)
        `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        `when`(controller.sendMediaButton(anyInt())).thenReturn(true)
        val f = Fixture(onKey = { index, source -> CarPlayMediaKeys.onFytMediaKey(controller, index, source) })
        try {
            CarPlayMediaKeys.attach(app, controller)
            f.start(); f.drain()
            f.module.emit(37, intArrayOf(10, 5))
            f.module.emit(37, intArrayOf(8, 4))
            f.drain()
            verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
            verify(controller).sendMediaButton(CarPlayMediaButton.PREVIOUS)
            val report = WheelKeyLog.report(false).joinToString("\n")
            assertTrue(report, report.contains("media key fyt-37 -> CarPlay 4 sent=true"))
            CarPlayMediaKeys.detach(controller)
            f.module.emit(37, intArrayOf(10, 5))
            f.drain()
            verifyOnlyTheTwoMediaCommands(controller)
        } finally {
            f.connection.stop(); f.drain()
            CarPlayMediaKeys.detach(controller)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun radioBluetoothUnknownDirectionsAndMalformedCallbacksNeverBecomeTrackSkips() {
        val f = Fixture()
        f.start(); f.drain()
        for (ints in listOf(null, intArrayOf(), intArrayOf(10), intArrayOf(1, 5),
            intArrayOf(3, 4), intArrayOf(10, 1), intArrayOf(10, 999))) f.module.emit(37, ints)
        f.module.emit(60, intArrayOf(4))
        f.drain()
        assertTrue(f.keys.isEmpty())
        assertTrue(f.notes.any { it.contains("update=60") })
        f.connection.stop(); f.drain()
    }

    @Test fun newerRawKeyCallbackUsesAndroidKeyCodesWithoutRequestingKeyFocus() {
        val f = Fixture(rawSupported = true)
        f.start(); f.drain()
        f.module.emit(133, intArrayOf(87)); f.module.emit(133, intArrayOf(88))
        f.module.emit(133, intArrayOf(19))
        f.drain()
        assertEquals(listOf(4 to "fyt-133", 5 to "fyt-133"), f.keys)
        f.connection.stop(); f.drain()
    }

    @Test fun serviceReconnectRejectsOldCallbacksAndDoesNotLeakBindings() {
        val f = Fixture()
        f.start(); f.drain()
        val stale = f.module.callbacks.getValue(37)
        f.context.service.onServiceDisconnected(null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(2, f.context.binds)
        f.context.service.onServiceConnected(null, f.toolkit)
        f.drain()
        f.module.emitTo(stale, 37, intArrayOf(10, 5))
        f.module.emit(37, intArrayOf(10, 4))
        f.drain()
        assertEquals(listOf(5 to "fyt-37"), f.keys)
        f.connection.stop(); f.drain()
        assertEquals(2, f.context.unbinds)
    }

    @Test fun stopDuringPendingRegistrationUnregistersAndRejectsQueuedKeys() {
        val f = Fixture()
        f.start()
        f.connection.stop()
        f.drain()
        assertEquals(listOf(37, 60), f.module.unregistered)
        f.module.emit(37, intArrayOf(10, 5))
        f.drain()
        assertTrue(f.keys.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(1, f.context.binds)
    }

    @Test fun absentServiceRetriesUntilStop() {
        val f = Fixture()
        f.context.allowBinding = false
        f.connection.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(2, f.context.binds)
        f.connection.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(2, f.context.binds)
        assertEquals(0, f.context.unbinds)
    }

    @Test fun duplicateVendorAndAndroidReportsDoNotSkipTwoTracksAndSeparateTapsRemain() {
        val d = FytKeyDuplicates()
        assertTrue(d.accept(4, "fyt-37", 100))
        assertFalse(d.accept(4, "fyt-133", 105))
        assertFalse(d.accept(4, "KEYCODE_MEDIA_NEXT", 110))
        assertTrue(d.accept(4, "fyt-37", 130)) // Same-source rapid taps are distinct.
        assertTrue(d.accept(5, "fyt-37", 140))
        assertTrue(d.accept(4, "KEYCODE_MEDIA_NEXT", 400))
        assertFalse(d.accept(4, "fyt-37", 410))
        d.clear()
        assertTrue(d.accept(4, "fyt-37", 411))
    }

    @Test fun onlyLegacyFytEnablesTheAdapter() {
        assertTrue(FytWheelKeys.legacyFyt(23, "FYT", "FYT6025", "sql_fyt6025"))
        assertFalse(FytWheelKeys.legacyFyt(28, "FYT"))
        assertFalse(FytWheelKeys.legacyFyt(23, "BYD", "unknown"))
    }

    private fun verifyOnlyTheTwoMediaCommands(controller: CarPlayController) {
        verify(controller, times(1)).sendMediaButton(4)
        verify(controller, times(1)).sendMediaButton(5)
    }

    private class Queue : Executor {
        private val pending = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { pending.add(command) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }
    private class BindContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        lateinit var service: ServiceConnection
        var allowBinding = true
        var binds = 0
        var unbinds = 0
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            assertEquals(ComponentName("com.syu.ms", "app.ToolkitService"), intent.component)
            binds++; service = conn
            return allowBinding
        }
        override fun unbindService(conn: ServiceConnection) { assertSame(service, conn); unbinds++ }
    }
    private class Toolkit(private val module: IBinder) : Binder() {
        var requestedModule = -1
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertEquals(1, code)
            data.enforceInterface("com.syu.ipc.IRemoteToolkit")
            requestedModule = data.readInt()
            reply!!.writeNoException(); reply.writeStrongBinder(module)
            return true
        }
    }
    private class Module(private val rawSupported: Boolean) : Binder() {
        val callbacks = mutableMapOf<Int, IBinder>()
        val registrations = mutableListOf<Pair<Int, Int>>()
        val unregistered = mutableListOf<Int>()
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface("com.syu.ipc.IRemoteModule")
            val callback = data.readStrongBinder()!!
            val update = data.readInt()
            if (update == 133 && !rawSupported) return false
            when (code) {
                3 -> { registrations.add(update to data.readInt()); callbacks[update] = callback }
                4 -> { assertSame(callbacks[update], callback); unregistered.add(update) }
                else -> fail("Unexpected FYT command $code: adapter must only subscribe")
            }
            return true // Real FYT versions can return no exception header for these void calls.
        }
        fun emit(code: Int, ints: IntArray?) = emitTo(callbacks.getValue(code), code, ints)
        fun emitTo(callback: IBinder, code: Int, ints: IntArray?) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.syu.ipc.IModuleCallback")
                data.writeInt(code); data.writeIntArray(ints)
                data.writeFloatArray(null); data.writeStringArray(null)
                callback.transact(1, data, null, IBinder.FLAG_ONEWAY)
            } finally { data.recycle() }
        }
    }
    private class Fixture(onKey: ((Int, String) -> Unit)? = null, rawSupported: Boolean = false) {
        val context = BindContext()
        val queue = Queue()
        val module = Module(rawSupported)
        val toolkit = Toolkit(module)
        val keys = mutableListOf<Pair<Int, String>>()
        val notes = mutableListOf<String>()
        val connection = FytWheelKeyConnection(context, Handler(Looper.getMainLooper()), queue,
            onKey ?: { i, s -> keys.add(i to s); Unit }, { notes.add(it) })
        fun start() { connection.start(); context.service.onServiceConnected(null, toolkit) }
        fun drain() { repeat(3) { queue.drain(); shadowOf(Looper.getMainLooper()).idle() } }
    }
}
