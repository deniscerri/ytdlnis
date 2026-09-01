package com.deniscerri.ytdl.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges background download work to foreground-only user decisions. Requests stay
 * in StateFlow while the app is backgrounded so opening MainActivity cannot miss one.
 */
object DownloadRecoveryCoordinator {
    enum class Kind {
        SUBTITLE_FAILURE,
        LOW_STORAGE,
        COOKIE_LOGIN,
    }

    enum class Action {
        CONTINUE,
        RETRY,
        CANCEL,
    }

    data class Request(
        val requestId: Long = nextRequestId.incrementAndGet(),
        val downloadId: Long,
        val title: String,
        val kind: Kind,
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
        val results = synchronized(lock) {
            val target = pending[requestId] ?: return
            // One successful refresh updates the shared master jar. Resolve every
            // YouTube item waiting on the same stale authentication state together.
            val matching = if (target.request.kind == Kind.COOKIE_LOGIN) {
                pending.values.filter { it.request.kind == Kind.COOKIE_LOGIN }
            } else {
                listOf(target)
            }
            matching.forEach { pending.remove(it.request.requestId) }
            publishLocked()
            matching.map { it.result }
        }
        results.forEach { it.complete(action) }
    }

    fun hasPendingCookieLogin(): Boolean = synchronized(lock) {
        pending.values.any { it.request.kind == Kind.COOKIE_LOGIN }
    }

    private fun publishLocked() {
        _requests.value = pending.values.map { it.request }
    }
}
