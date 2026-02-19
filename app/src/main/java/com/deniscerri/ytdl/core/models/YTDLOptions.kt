package com.deniscerri.ytdl.core.models

class YTDLOptions {
    private val options: MutableMap<String, MutableList<String>> = LinkedHashMap()
    fun addOption(option: String, argument: String): YTDLOptions {
        if (!options.containsKey(option)) {
            val arguments: MutableList<String> = ArrayList()
            arguments.add(argument)
            options[option] = arguments
        } else {
            options[option]!!.add(argument)
        }
        return this
    }

    fun addOption(option: String, argument: Number): YTDLOptions {
        if (!options.containsKey(option)) {
            val arguments: MutableList<String> = ArrayList()
            arguments.add(argument.toString())
            options[option] = arguments
        } else {
            options[option]!!.add(argument.toString())
        }
        return this
    }

    fun addOption(option: String): YTDLOptions {
        if (!options.containsKey(option)) {
            val arguments: MutableList<String> = ArrayList()
            arguments.add("")
            options[option] = arguments
        } else {
            options[option]!!.add("")
        }
        return this
    }

    fun getArgument(option: String): String? {
        if (!options.containsKey(option)) return null
        val argument = options[option]!![0]
        return argument.ifEmpty { null }
    }

    fun getArguments(option: String): List<String>? {
        return if (!options.containsKey(option)) null else options[option]
    }

    fun hasOption(option: String): Boolean {
        return options.containsKey(option)
    }

    fun buildOptions(): List<String> {
        val commandList: MutableList<String> = mutableListOf()
        for ((option, value) in options) {
            for (argument in value) {
                commandList.add(option)
                if (argument.isNotEmpty()) commandList.add(argument)
            }
        }
        return commandList
    }
}