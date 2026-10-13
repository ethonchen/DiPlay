package com.shilapi.xcertplay.network

/** A bounded, read-back-verified configuration transaction; it does not prove IP readiness. */
internal class LegacyApRestoreTransaction<C : Any>(
    private val port: Port<C>,
    private val cancelled: () -> Boolean,
    private val now: () -> Long = System::nanoTime,
    private val pause: (Long) -> Unit = Thread::sleep,
    private val log: (String) -> Unit = {},
) {
    interface Port<C> {
        fun read(): C?
        fun target(original: C): C
        fun matches(configuration: C): Boolean
        fun state(): Int?
        fun canControl(): Boolean
        fun write(configuration: C): Boolean
        fun enable(configuration: C?, enabled: Boolean): Boolean
    }

    enum class Result {
        READY, PERMISSION_REQUIRED, UNSUPPORTED, STOP_FAILED, WRITE_FAILED,
        VERIFY_FAILED, START_FAILED, TIMED_OUT, CANCELLED, FAILED,
    }
    private class Abort(val result: Result) : RuntimeException()

    fun run(timeoutMillis: Long): Result {
        require(timeoutMillis > 0)
        val deadline = now() + timeoutMillis * 1_000_000L
        var original: C? = null
        var wasOn = false
        var touched = false
        fun checkpoint() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw Abort(Result.CANCELLED)
            if (now() >= deadline) throw Abort(Result.TIMED_OUT)
        }
        fun await(predicate: () -> Boolean) {
            while (true) {
                checkpoint()
                val done = predicate()
                checkpoint()
                if (done) return
                pause(100)
            }
        }
        val result = try {
            checkpoint()
            await { port.state().let { it == 11 || it == 13 } }
            val saved = port.read() ?: throw Abort(Result.UNSUPPORTED)
            original = saved
            checkpoint()
            wasOn = port.state() == 13
            val matches = port.matches(saved)
            checkpoint()
            if (matches && wasOn) {
                if (port.read()?.let(port::matches) != true) throw Abort(Result.VERIFY_FAILED)
                Result.READY
            } else {
                if (!port.canControl()) throw Abort(Result.PERMISSION_REQUIRED)
                val target = port.target(saved)
                checkpoint()
                if (wasOn) {
                    touched = true
                    if (!port.enable(null, false)) throw Abort(Result.STOP_FAILED)
                    await {
                        val state = port.state()
                        if (state == 14) throw Abort(Result.STOP_FAILED)
                        state == 11
                    }
                }
                checkpoint()
                if (port.state() != 11) throw Abort(Result.STOP_FAILED)
                if (!matches) {
                    touched = true
                    if (!port.write(target)) throw Abort(Result.WRITE_FAILED)
                    // Hidden setters can enqueue a write. Do not mistake acceptance for persistence.
                    await { port.read()?.let(port::matches) == true }
                }
                checkpoint()
                touched = true
                if (!port.enable(target, true)) throw Abort(Result.START_FAILED)
                await {
                    val state = port.state()
                    if (state == 14) throw Abort(Result.START_FAILED)
                    state == 13
                }
                checkpoint()
                if (port.read()?.let(port::matches) != true) throw Abort(Result.VERIFY_FAILED)
                checkpoint()
                Result.READY
            }
        } catch (failure: Abort) {
            failure.result
        } catch (_: SecurityException) {
            Result.PERMISSION_REQUIRED
        } catch (_: ReflectiveOperationException) {
            Result.UNSUPPORTED
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.CANCELLED
        } catch (failure: Exception) {
            log("q7-restore failureClass=${failure.javaClass.simpleName}")
            Result.FAILED
        }
        if (result != Result.READY && touched && original != null) {
            // Best effort, under the caller's lock. Never write while an AP is still running.
            val rollbackDeadline = now() + 2_000_000_000L
            val restored = runCatching {
                if (port.state() != 11) {
                    check(port.enable(null, false))
                    while (port.state() != 11 && now() < rollbackDeadline) pause(100)
                }
                check(port.state() == 11)
                check(port.write(original!!))
                if (wasOn) check(port.enable(original, true))
                true
            }.getOrDefault(false)
            log("q7-restore rollbackRequested=$restored")
        }
        log("q7-restore result=$result configVerified=${result == Result.READY}")
        return result
    }
}
