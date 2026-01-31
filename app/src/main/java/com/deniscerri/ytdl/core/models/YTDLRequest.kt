package com.deniscerri.ytdl.core.models

class YTDLRequest {
    private val urls: List<String>
    private val options = YTDLOptions()
    private val customCommandList: MutableList<String> = ArrayList()

    constructor(url: String) {
        urls = listOf(url)
    }

    constructor(urls: List<String>) {
        this.urls = urls
    }

    fun addOption(option: String, argument: String): YTDLRequest {
        options.addOption(option, argument)
        return this
    }

    fun addOption(option: String, argument: Number): YTDLRequest {
        options.addOption(option, argument)
        return this
    }

    fun addOption(option: String): YTDLRequest {
        options.addOption(option)
        return this
    }

    fun addCommands(commands: List<String>): YTDLRequest {
        customCommandList.addAll(commands)
        return this
    }

    fun getOption(option: String): String? {
        return options.getArgument(option)
    }

    fun getArguments(option: String): List<String?>? {
        return options.getArguments(option)
    }

    fun hasOption(option: String): Boolean {
        return options.hasOption(option)
    }

    fun buildCommand(): List<String> {
        val commandList: MutableList<String> = ArrayList()
        commandList.addAll(options.buildOptions())
        commandList.addAll(customCommandList)
        commandList.addAll(urls)
        return commandList
    }
}