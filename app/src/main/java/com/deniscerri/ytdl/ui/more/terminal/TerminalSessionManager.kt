package com.deniscerri.ytdl.ui.more.terminal

import com.termux.terminal.TerminalSession
import java.util.concurrent.ConcurrentHashMap

object TerminalSessionManager {
    private val activeSessions = ConcurrentHashMap<Long, TerminalSession>()

    fun getSession(id: Long): TerminalSession? = activeSessions[id]

    fun putSession(id: Long, session: TerminalSession) {
        activeSessions[id] = session
    }

    fun removeSession(id: Long): TerminalSession? {
        return activeSessions.remove(id)
    }

    fun isRunning(id: Long): Boolean {
        return activeSessions[id]?.isRunning == true
    }

    fun getAllSessions(): Map<Long, TerminalSession> = activeSessions

    fun killSession(id: Long) {
        activeSessions[id]?.let { session ->
            session.finishIfRunning()
            activeSessions.remove(id)
        }
    }
}