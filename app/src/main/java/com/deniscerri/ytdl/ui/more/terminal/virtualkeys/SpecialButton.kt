package com.deniscerri.ytdl.ui.more.terminal.virtualkeys


/** The {@link Class} that implements special buttons for {@link VirtualKeysView}. */
class SpecialButton(
    /** The special button key.  */
    val key: String
) {
    /** Get [.key] for this [SpecialButton].  */

    /**
     * Initialize a [SpecialButton].
     * 
     * @param key The unique key name for the special button. The key is registered in [.map]
     * with which the [SpecialButton] can be retrieved via a call to [     ][.valueOf].
     */
    init {
        map.put(key, this)
    }

    override fun toString(): String {
        return key
    }

    companion object {
        private val map = HashMap<String?, SpecialButton?>()

        var CTRL: SpecialButton = SpecialButton("CTRL")
        val ALT: SpecialButton = SpecialButton("ALT")
        val SHIFT: SpecialButton = SpecialButton("SHIFT")
        val FN: SpecialButton = SpecialButton("FN")

        /**
         * Get the [SpecialButton] for `key`.
         * 
         * @param key The unique key name for the special button.
         */
        @JvmStatic
        fun valueOf(key: String?): SpecialButton? {
            return map.get(key)
        }
    }
}