package com.deniscerri.ytdl.core.models

class ExecuteException : Exception {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, e: Throwable?) : super(message, e) {}
    constructor(e: Throwable?) : super(e) {}
}