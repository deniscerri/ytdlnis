package com.deniscerri.ytdl.ui.more.terminal

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.file.child
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.util.FileUtil
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.util.zip.ZipFile

object MkSession {
    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        pendingCommand: PendingCommand? = null
    ): TerminalSession {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

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
                    |    (
                    |        export LD_LIBRARY_PATH="$ldPath"
                    |        export PYTHONHOME="$pythonHome"
                    |        export SSL_CERT_FILE="$sslCert"
                    |        export OPENSSL_CONF="$openSslConf"
                    |        exec $commandToExec "${'$'}@"
                    |    )
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
                    val execCommand = "\"${file.absolutePath}\""
                    rcBuilder.append(shellFunction(name, execCommand))
                }
            }

            val pythonBin = runtimeManager.pythonLocation.executable

            rcBuilder.append(
                shellFunction(
                    "pip",
                    "\"${pythonBin.absolutePath}\" -m pip"
                )
            )

            val nodeBin = runtimeManager.nodeLocation.executable
            if (nodeBin.exists()) {
                rcBuilder.append(
                    shellFunction(
                        "npm",
                        "\"${nodeBin.absolutePath}\" ${$$"$NPM_CLI_PATH"}"
                    )
                )
            }

            val ytdlpBin = runtimeManager.ytdlpPath
            if (pythonBin.exists() && ytdlpBin != null && ytdlpBin.exists()) {

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

                if (preferences.getBoolean("use_cookies", false)){
                    FileUtil.getCookieFile(context){
                        ytdlpExtraArgs.append(" --cookies \"${it}\"")
                    }
                }

                rcBuilder.append(
                    shellFunction(
                        "yt-dlp",
                        "\"${pythonBin.absolutePath}\" \"${ytdlpBin.absolutePath}\"$ytdlpExtraArgs"
                    )
                )
            }

            val currentSystemPath = System.getenv("PATH") ?: "/system/bin"
            runtimeVariables["PATH"] = currentSystemPath
            // REMOVE LD_LIBRARY_PATH from global shell environment
            // This prevents system binaries (/system/bin/ls, sh, etc.) from loading custom app libraries.
            runtimeVariables.remove("LD_LIBRARY_PATH")
            envVariables.putAll(runtimeVariables)

            val localDir = localDir()

            val bashDir = prepareBash()
            val useBash = bashDir != null && pendingCommand?.shell == null

            val availableTools = buildList {
                executables.forEach { (name, file) -> if (file.exists()) add(name) }
                add("pip")
                if (nodeBin.exists()) add("npm")
                if (pythonBin.exists() && ytdlpBin != null && ytdlpBin.exists()) add("yt-dlp")
            }

            val writeYTDLPTerminal = preferences.getBoolean("write_ytdlp_terminal", true)
            val currentDir = runtimeVariables["HOME"] ?: ""

            val rcFile = localDir.child("shellrc")
            rcFile.writeText(
                // bash is launched with LD_LIBRARY_PATH pointing at its own libs. Drop it
                // right away so child processes (system binaries) don't inherit it.
                (if (useBash) "unset LD_LIBRARY_PATH\n" else "") +
                rcBuilder.toString() +
                        // Probe support in a subshell first: if `set -o multiline` is
                        // unsupported by this shell build, POSIX allows the shell to exit
                        // outright on a bad `set` option even mid-script. Running the probe
                        // in a subshell means a failure there can't abort sourcing of this
                        // rc file in the parent (interactive) shell — everything above this
                        // line (functions, aliases, exports, PS1) is already safely loaded
                        // by the time we get here regardless of the outcome.
                        "(set -o multiline) >/dev/null 2>&1 && set -o multiline\n" +
                        // bash uses native \[ \] markers (and \w) for zero-width sequences.
                        (if (useBash) "PS1='\\[\\e[01;32m\\]\\w\\[\\e[00m\\] \\$ '\n" else "") +
                        welcomeBanner(availableTools, currentDir) +
                        // Pre-fill the first prompt with "yt-dlp ". Readline can't be fed text directly,
                        // so bind a macro to the terminal's "status OK" reply (ESC [ 0 n) and request
                        // that reply once, right before the first prompt is drawn.
                        (if (writeYTDLPTerminal && useBash && "yt-dlp" in availableTools) {
                            "bind '\"\\e[0n\": \"yt-dlp \"'\n" +
                                "PROMPT_COMMAND='printf \"\\033[5n\"; unset PROMPT_COMMAND'\n"
                        } else "")
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

            val shell: String
            val args: Array<String>
            if (useBash) {
                // Run through the linker: app-private files can't be execve'd directly on Android 10+.
                env.add("LD_LIBRARY_PATH=${bashDir!!.absolutePath}")
                // readline needs terminfo to know the terminal auto-wraps; ncurses only knows the Termux path.
                env.add("TERMINFO=${bashDir.child("terminfo").absolutePath}")
                val bash = bashDir.child("bash").absolutePath
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    shell = linker
                    // args[0] becomes argv[0] in TerminalSession, so pass a placeholder first.
                    args = arrayOf("linker", bash, "--rcfile", rcFile.absolutePath, "-i")
                } else {
                    // Before Android 10 the linker can't be used as a launcher (it just prints its
                    // usage text), and execve from app storage is still allowed.
                    shell = bash
                    args = arrayOf("bash", "--rcfile", rcFile.absolutePath, "-i")
                }
            } else {
                shell = pendingCommand?.shell ?: "/system/bin/sh"
                args = arrayOf()
            }

            return TerminalSession(
                shell,
                envVariables["HOME"],
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }
    }

    /** Shell snippet that prints a short intro, like a Linux login message. */
    private fun welcomeBanner(tools: List<String>, currentDir: String): String {
        val lines = mutableListOf("\\033[1mWelcome to the YTDLnis terminal\\033[0m")
        lines.add("Use \\033[1;32myt-dlp\\033[0m to run yt-dlp commands,")
        lines.add("e.g. yt-dlp --version")
        lines.add("Available commands: ${tools.joinToString(", ")}")
        lines.add("\nCurrent directory: $currentDir")
        return lines.joinToString(separator = "\n", prefix = "", postfix = "\n") { "printf '$it\\n'" } + "printf '\\n'\n"
    }

    /**
     * Extracts the bundled bash (+ readline/ncurses/iconv) from libbash.zip.so into app storage.
     * Returns the directory holding them, or null so the caller falls back to /system/bin/sh.
     */
    private fun Context.prepareBash(): File? {
        return try {
            val zip = File(applicationInfo.nativeLibraryDir, "libbash.zip.so")
            if (!zip.exists()) return null

            val dir = File(localDir(), "bash")
            val marker = File(dir, ".version")
            // bump the leading number whenever the extraction layout changes
            val version = "3-${zip.length()}-${zip.lastModified()}"
            if (!File(dir, "bash").exists() || !marker.exists() || marker.readText() != version) {
                dir.deleteRecursively()
                dir.mkdirs()
                ZipFile(zip).use { z ->
                    z.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                        val out = File(dir, entry.name)
                        if (!out.canonicalPath.startsWith(dir.canonicalPath)) return@forEach
                        out.parentFile?.mkdirs()
                        z.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                        out.setReadable(true, false)
                        out.setExecutable(true, false)
                    }
                }
                marker.writeText(version)
            }
            dir
        } catch (e: Exception) {
            android.util.Log.w("MkSession", "bash unavailable, falling back to sh", e)
            null
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
