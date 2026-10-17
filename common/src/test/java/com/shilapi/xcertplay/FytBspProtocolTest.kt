package com.shilapi.xcertplay

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class FytBspProtocolTest {
    @Test fun frameworkProbeSubscribesOnlyToButtonUpdatesAndNeverWritesProperties() {
        var callback: IBinder? = null
        val operations = mutableListOf<Int>()
        val module = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("com.syu.ipc.IRemoteModule")
                assertTrue("BSP probe must never send commands", code == 3 || code == 4)
                operations.add(code)
                callback = data.readStrongBinder()
                assertEquals(2, data.readInt())
                if (code == 3) assertEquals(0, data.readInt())
                return true
            }
        }
        val toolkit = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(1, code)
                data.enforceInterface("com.syu.ipc.IRemoteToolkit")
                assertEquals(0, data.readInt())
                reply!!.writeNoException(); reply.writeStrongBinder(module)
                return true
            }
        }
        val received = mutableListOf<List<Int>>()
        val sub = FytKeySubscription(toolkit, updates = listOf(2)) { _, ints -> received.add(ints?.toList() ?: emptyList()); Unit }
        sub.register()
        for (update in listOf(2, 37)) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.syu.ipc.IModuleCallback")
                data.writeInt(update); data.writeIntArray(intArrayOf(221, 1))
                data.writeFloatArray(null); data.writeStringArray(null)
                callback!!.transact(1, data, null, IBinder.FLAG_ONEWAY)
            } finally { data.recycle() }
        }
        assertEquals(listOf(listOf(221, 1)), received)
        sub.close()
        assertEquals(listOf(3, 4), operations)
    }
}
