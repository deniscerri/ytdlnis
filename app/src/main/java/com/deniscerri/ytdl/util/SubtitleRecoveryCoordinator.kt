package com.deniscerri.ytdl.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps subtitle decisions pending while the app is backgrounded, then delivers the
 * user's choice back to the worker when MainActivity is opened.
 */
object SubtitleRecoveryCoordinator {
    enum class Action {
        CONTINUE,
        RETRY,
        CANCEL,
    }

    data class Request(
        val requestId: Long = SubtitleRecoveryCoordinator.nextRequestId.incrementAndGet(),
        val downloadId: Long,
        val title: String,
        val message: String,
    )

    private data class Pending(
        val request: Request,
        val result: CompletableDeferred<Action>,
    )

    private val nextRequestId = AtomicLong(System.currentTimeMillis())
    private val lock = Any()
    private val pending = linkedMapOf<Long, Pending>()
    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests = _requests.asStateFlow()

    suspend fun awaitDecision(request: Request): Action {
        val result = CompletableDeferred<Action>()
        synchronized(lock) {
            pending[request.requestId] = Pending(request, result)
            publishLocked()
        }

        return try {
            result.await()
        } finally {
            synchronized(lock) {
                pending.remove(request.requestId)
                publishLocked()
            }
        }
    }

    fun resolve(requestId: Long, action: Action) {
        val result = synchronized(lock) {
            val target = pending.remove(requestId) ?: return
            publishLocked()
            target.result
        }
        result.complete(action)
    }

    private fun publishLocked() {
        _requests.value = pending.values.map { it.request }
    }
}
