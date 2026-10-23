package com.deniscerri.ytdl.core.llm

/** Best-effort removal of credentials from text sent to a provider. */
object SecretRedactor {
    const val PLACEHOLDER = "<redacted>"

    private const val VALUE = """("[^"]*"|'[^']*'|\S+)"""

    private val patterns: List<Pair<Regex, String>> = listOf(
        // yt-dlp options that take a credential: --password X, --password=X, -p X
        Regex("""(?<=^|\s)(--(?:password|username|video-password|ap-password|ap-username|twofactor|client-certificate-password))(=|\s+)$VALUE""")
            to "$1$2$PLACEHOLDER",
        Regex("""(?<=^|\s)(-[up2])(\s+)$VALUE""") to "$1$2$PLACEHOLDER",
        // HTTP headers carrying credentials, e.g. --add-headers "Authorization: Bearer x"
        Regex("""(?i)\b((?:proxy-)?authorization|cookie|set-cookie|x-api-key|api-key)(\s*:\s*)[^"'\r\n]+""")
            to "$1$2$PLACEHOLDER",
        Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{8,}""") to "$1 $PLACEHOLDER",
        // credentials embedded in URLs: scheme://user:pass@host
        Regex("""(?i)\b([a-z][a-z0-9+.-]*://)[^/\s:@]+:[^/\s@]+@""") to "$1$PLACEHOLDER@",
        // secret query parameters
        Regex("""(?i)([?&](?:access_token|refresh_token|id_token|token|api_key|apikey|key|auth|password|passwd|session|sig|signature)=)[^&\s"']+""")
            to "$1$PLACEHOLDER",
        // common API key / token shapes
        Regex("""\b(?:sk|pk|rk)-[A-Za-z0-9_-]{16,}""") to PLACEHOLDER,
        Regex("""\bAIza[0-9A-Za-z_-]{30,}""") to PLACEHOLDER,
        Regex("""\b(?:ghp|gho|ghu|ghs|github_pat)_[A-Za-z0-9_]{20,}""") to PLACEHOLDER,
        Regex("""\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}""") to PLACEHOLDER,
    )

    fun redact(text: String, extraSecrets: Collection<String> = emptyList()): String {
        var result = text
        extraSecrets.filter { it.length >= 6 }.forEach { result = result.replace(it, PLACEHOLDER) }
        patterns.forEach { (regex, replacement) -> result = regex.replace(result, replacement) }
        return result
    }
}
