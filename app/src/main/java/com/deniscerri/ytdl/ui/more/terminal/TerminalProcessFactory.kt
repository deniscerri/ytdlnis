package com.deniscerri.ytdl.ui.more.terminal

import android.content.Context
import com.deniscerri.ytdl.core.RuntimeManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

object TerminalProcessFactory {

    private const val DEFAULT_TRANSCRIPT_ROWS = 3000

    fun createRuntimeSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        workingDir: String? = null
    ): TerminalSession {
        RuntimeManager.assertInit()

        val shellExecutable = "/system/bin/sh"
        val args = arrayOf("-i")

        val envArray = RuntimeManager.getEnvironmentForTerminal(context)
        val cwd = workingDir ?: context.getExternalFilesDir(null)?.absolutePath
        ?: context.filesDir.absolutePath

        val session =  TerminalSession(
            shellExecutable,
            cwd,
            args,
            envArray,
            DEFAULT_TRANSCRIPT_ROWS,
            sessionClient
        )

        val pythonBin = RuntimeManager.pythonLocation.executable.absolutePath
        val ytdlpBin = RuntimeManager.ytdlpPath?.absolutePath ?: ""
        val ffmpegBin = RuntimeManager.ffmpegLocation.executable.absolutePath
        val denoBin = RuntimeManager.denoLocation.executable.absolutePath
        val nodeBin = RuntimeManager.nodeLocation.executable.absolutePath
        val aria2Bin = RuntimeManager.aria2Location.executable.absolutePath

        val initCommands = buildString {
            append("alias yt-dlp='$pythonBin $ytdlpBin'\n")
            append("alias python='$pythonBin'\n")
            append("alias ffmpeg='$ffmpegBin'\n")
            append("alias deno='$denoBin'\n")
            append("alias node='$nodeBin'\n")
            append("alias aria2='$aria2Bin'\n")
            append("clear\n")
        }

        session.write(initCommands)
        return session
    }
}