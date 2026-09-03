package com.kaon.music.core.logging

/**
 * Redaction helpers for values that must never reach a log in any build.
 *
 * ARCHITECTURE.md §5.3: poTokens, integrity tokens, BotGuard responses, visitorData, cookies,
 * signatureCipher material, and full stream URLs are never logged. These helpers produce a
 * correlatable fingerprint (length + last 4 characters) so a support log can still distinguish
 * "token A" from "token B" without disclosing either.
 *
 * Enforced by LoggingBoundaryTest.
 */
object Redact {

    private const val PLACEHOLDER = "<redacted>"
    private const val VISIBLE_SUFFIX = 4

    /**
     * Fingerprints a secret: never emits the secret itself, only its length and a short suffix.
     * A suffix is safe because these values are high-entropy and long; four characters cannot
     * reconstruct one but is enough to tell two apart across log lines.
     */
    fun secret(value: String?): String {
        if (value == null) return "<null>"
        if (value.isEmpty()) return "<empty>"
        if (value.length <= VISIBLE_SUFFIX) return "$PLACEHOLDER(len=${value.length})"
        return "$PLACEHOLDER(len=${value.length},…${value.takeLast(VISIBLE_SUFFIX)})"
    }

    /** True when a secret is present, without disclosing anything about it. */
    fun presence(value: String?): String = if (value.isNullOrEmpty()) "absent" else "present"

    /**
     * Reduces a stream URL to scheme, host, and the identifying query parameters that are safe to
     * log. Signature, n-parameter, and every other query value are dropped — a truncated prefix is
     * not sufficient redaction because CDN URLs place identifying material early.
     */
    fun url(value: String?): String {
        if (value.isNullOrEmpty()) return "<empty>"
        val schemeEnd = value.indexOf("://")
        if (schemeEnd < 0) return PLACEHOLDER
        val afterScheme = schemeEnd + 3
        val pathStart = value.indexOf('/', afterScheme).let { if (it < 0) value.length else it }
        val host = value.substring(afterScheme, pathStart)
        return "${value.substring(0, schemeEnd)}://$host/$PLACEHOLDER"
    }
}
