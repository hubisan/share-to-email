package ch.hubisan.sharetoemail

import java.util.concurrent.atomic.AtomicBoolean

object ShareLock {
    private val inProgress = AtomicBoolean(false)

    fun tryAcquire(): Boolean = inProgress.compareAndSet(false, true)
    fun release() { inProgress.set(false) }
}
