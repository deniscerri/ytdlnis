package com.deniscerri.ytdl.core.models

class ExecuteResponse(
    val command: List<String?>,
    val exitCode: Int,
    val elapsedTime: Long,
    val out: String,
    val err: String
)