package com.shilapi.xcertplay

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
@LooperMode(LooperMode.Mode.PAUSED)
class Android6MapEmbedServiceTest {
    @Test fun unsupportedMapRequestsAndServiceCleanupDoNotLoadSurfaceControl() {
        val controller = Robolectric.buildService(MapEmbedService::class.java).create()
        val service = controller.get()
        try {
            AirPlayPersistence.saveLauncherMapSharing(service, true)
            val replies = mutableListOf<Message>()
            val client = Messenger(Handler(Looper.getMainLooper()) {
                replies += Message.obtain(it)
                true
            })
            val endpoint = Messenger(service.onBind(Intent(MapEmbedService.ACTION)))
            listOf(MapEmbedService.MSG_ATTACH, MapEmbedService.MSG_RESIZE, MapEmbedService.MSG_DETACH).forEach { request ->
                endpoint.send(Message.obtain(null, request).apply { replyTo = client })
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, replies.size)
            replies.forEach {
                assertEquals(MapEmbedService.MSG_ERROR, it.what)
                assertEquals(MapEmbedService.ERROR_UNSUPPORTED, it.data.getString(MapEmbedService.KEY_ERROR))
            }
            AirPlayPersistence.saveLauncherMapSharing(service, false)
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            controller.destroy()
        }
    }
}
