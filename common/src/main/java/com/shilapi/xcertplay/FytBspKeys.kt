package com.shilapi.xcertplay

import android.os.Handler
import android.os.IBinder
import java.util.concurrent.Executor

/** Read-only framework button probe. BSP's vendor key numbers are recorded, never guessed as media keys. */
internal object FytBspKeys {
    @Volatile private var owner: Any? = null
    @Volatile private var state = "inactive"
    private var subscription: FytKeySubscription? = null
    fun start(main: Handler, worker: Executor) {
        stop(worker)
        val token = Any()
        owner = token
        state = "connecting"
        worker.execute {
            val next = runCatching {
                val toolkit = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                    .invoke(null, "syu") as? IBinder ?: error("framework syu service unavailable")
                FytKeySubscription(toolkit, updates = listOf(2)) { code, ints ->
                    main.post {
                        if (owner === token) {
                            state = "update=$code ints=${ints?.take(6)}"
                            WheelKeyLog.note("FYT BSP $state")
                        }
                    }
                }.also { it.register() }
            }
            main.post {
                if (owner !== token) next.getOrNull()?.let { stale -> worker.execute { stale.close() } }
                else next.onSuccess {
                    subscription = it
                    state = "connected update=2"
                    WheelKeyLog.note("FYT BSP $state")
                }.onFailure {
                    state = "unavailable ${it.javaClass.simpleName}"
                    WheelKeyLog.note("FYT BSP $state")
                }
            }
        }
    }
    fun stop(worker: Executor) {
        owner = null
        subscription?.let { old -> worker.execute { old.close() } }
        subscription = null
        state = "inactive"
    }
    fun report(): String = "FYT BSP keys wanted=${owner != null} state=$state"
}
