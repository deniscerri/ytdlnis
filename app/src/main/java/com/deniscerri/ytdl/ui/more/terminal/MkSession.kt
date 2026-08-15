package com.deniscerri.ytdl.ui.more.terminal

import android.content.Context
import com.anggrayudi.storage.file.child
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.core.RuntimeManager
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        pendingCommand: PendingCommand? = null
    ): TerminalSession {
        with(context) {
            val envVariables = mutableMapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val runtimeManager = RuntimeManager.getInstance()
            runtimeManager.assertInit()

            val runtimeVariables = runtimeManager.getEnvironmentForTerminal()
            val ldPath = runtimeVariables["LD_LIBRARY_PATH"] ?: ""
            val pythonHome = runtimeVariables["PYTHONHOME"] ?: ""
            val sslCert = runtimeVariables["SSL_CERT_FILE"] ?: ""
            val openSslConf = runtimeVariables["OPENSSL_CONF"] ?: ""

            val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

            // Build shell FUNCTIONS instead of standalone executable wrapper scripts.
            // On Android 10+ (W^X enforcement), files written at runtime under app-private
            // storage (codeCacheDir, filesDir, etc.) cannot be mmap'd PROT_EXEC, so any
            // attempt to `execve()` a wrapper script written there fails with EACCES
            // ("Permission denied"), even with correct chmod bits. A file that is only
            // *read* (sourced by the shell) never hits that restriction, so we emit shell
            // functions into a single rc file and have `sh` source it via $ENV.
            fun shellFunction(name: String, commandToExec: String): String {
                return """
                    |$name() {
                    |    LD_LIBRARY_PATH="$ldPath" \
                    |    PYTHONHOME="$pythonHome" \
                    |    SSL_CERT_FILE="$sslCert" \
                    |    OPENSSL_CONF="$openSslConf" \
                    |    $commandToExec "${'$'}@"
                    |}
                    |
                """.trimMargin()
            }

            val rcBuilder = StringBuilder()

            // mksh doesn't interpret bash-style \w / \u escapes in PS1 — it re-evaluates
            // PS1 as a normal parameter/command substitution each time it's displayed,
            // so embed $PWD directly. Colors are plain ANSI escapes; TERM is already
            // xterm-256color so they render fine in TerminalView.
            //
            // The ANSI codes must be wrapped in \x01 / \x02 (mksh's equivalent of bash's
            // \[ \[) so the line editor treats them as zero-width. Without this, the editor
            // miscounts the prompt's visual length and the cursor drifts to the wrong line
            // after running a command.
            val esc = "\u001b"
            val nonPrintStart = "\u0001"
            val nonPrintEnd = "\u0002"
            rcBuilder.append(
                """
                |PS1='$nonPrintStart$esc[01;32m$nonPrintEnd${'$'}PWD$nonPrintStart$esc[00m$nonPrintEnd ${'$'} '
                |alias ls='ls --color=auto' 2>/dev/null
                |export CLICOLOR=1
                |export LSCOLORS=ExGxFxdxCxDxDxBxBxExEx
                |
                """.trimMargin()
            )

            val executables = mapOf(
                "python" to runtimeManager.pythonLocation.executable,
                "ffmpeg" to runtimeManager.ffmpegLocation.executable,
                "deno" to runtimeManager.denoLocation.executable,
                "node" to runtimeManager.nodeLocation.executable,
                "qjs" to runtimeManager.quickJsLocation.executable,
                "aria2" to runtimeManager.aria2Location.executable,
            )

            executables.forEach { (name, file) ->
                if (file.exists()) {
                    val execCommand = if (file.name.endsWith(".so")) {
                        "$linker \"${file.absolutePath}\""
                    } else {
                        "\"${file.absolutePath}\""
                    }
                    rcBuilder.append(shellFunction(name, execCommand))
                }
            }

            val pythonBin = runtimeManager.pythonLocation.executable
            val ytdlpBin = runtimeManager.ytdlpPath
            if (pythonBin.exists() && ytdlpBin != null && ytdlpBin.exists()) {
                val pythonExec = if (pythonBin.name.endsWith(".so")) {
                    "$linker \"${pythonBin.absolutePath}\""
                } else {
                    "\"${pythonBin.absolutePath}\""
                }

                val ytdlpExtraArgs = StringBuilder()
                if (runtimeManager.ffmpegLocation.isAvailable) {
                    ytdlpExtraArgs.append(" --ffmpeg-location \"${runtimeManager.ffmpegLocation.executable.absolutePath}\"")
                }
                if (runtimeManager.nodeLocation.isAvailable) {
                    ytdlpExtraArgs.append(" --js-runtimes \"node:${runtimeManager.nodeLocation.executable.absolutePath}\"")
                }
                if (runtimeManager.denoLocation.isAvailable) {
                    ytdlpExtraArgs.append(" --js-runtimes \"deno:${runtimeManager.denoLocation.executable.absolutePath}\"")
                }
                if (runtimeManager.quickJsLocation.isAvailable) {
                    ytdlpExtraArgs.append(" --js-runtimes \"quickjs:${runtimeManager.quickJsLocation.executable.absolutePath}\"")
                }

                rcBuilder.append(
                    shellFunction(
                        "yt-dlp",
                        "$pythonExec \"${ytdlpBin.absolutePath}\"$ytdlpExtraArgs"
                    )
                )
            }

            val currentSystemPath = System.getenv("PATH") ?: "/system/bin"
            runtimeVariables["PATH"] = currentSystemPath
            envVariables.putAll(runtimeVariables)

            val localDir = localDir()

            val rcFile = localDir.child("shellrc")
            rcFile.writeText(
                rcBuilder.toString() +
                        // Probe support in a subshell first: if `set -o multiline` is
                        // unsupported by this shell build, POSIX allows the shell to exit
                        // outright on a bad `set` option even mid-script. Running the probe
                        // in a subshell means a failure there can't abort sourcing of this
                        // rc file in the parent (interactive) shell — everything above this
                        // line (functions, aliases, exports, PS1) is already safely loaded
                        // by the time we get here regardless of the outcome.
                        "(set -o multiline) >/dev/null 2>&1 && set -o multiline\n"
            )


            val env = mutableListOf(
                "ENV=${rcFile.absolutePath}",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LINKER=$linker",
                "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                "PKG=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}",
            )

            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            localDir.child("stat").apply {
                if (exists().not()) {
                    writeText(TerminalUtils.stat)
                }
            }

            localDir.child("vmstat").apply {
                if (exists().not()) {
                    writeText(TerminalUtils.vmstat)
                }
            }

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            val shell = pendingCommand?.shell ?: "/system/bin/sh"

            return TerminalSession(
                shell,
                envVariables["HOME"],
                arrayOf(),
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }
    }

    fun Context.localDir(): File {
        return File(filesDir.parentFile, "terminal_local").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }
}

data class PendingCommand(
    val shell: String,
    val workingDir: String?,
    val env: List<String>?
)
