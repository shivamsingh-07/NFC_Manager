package com.nfcmanager.app.domain.util

import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates URIs coming off a tag (or user input) before we hand them to the
 * system browser or persist them to an action payload.
 *
 * Policy:
 *  - Only allow a small allow-list of schemes.
 *  - Reject schemes known to trigger privileged actions (intent://,
 *    android-app://, javascript:, file:, content:, data:, etc.).
 *  - Require a host for http/https, with a "looks like a domain" check.
 *  - Refuse control characters and ASCII whitespace in the raw form.
 *
 * Two entry points:
 *  - [sanitize] is strict — it requires a scheme and is what we use for data
 *    we read off a tag (NDEF URIs always carry a scheme).
 *  - [normalizeForUserInput] is lenient — it accepts bare domains like
 *    `google.com` or `www.github.com` and normalizes them to `https://…`
 *    before sanitizing. This is the one to use for UI inputs.
 */
@Singleton
class UrlValidator @Inject constructor() {

    /**
     * Strict sanitizer. Returns the canonical URL string on success, or `null`
     * if the input is unsafe / malformed / schemeless.
     */
    fun sanitize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
        if (trimmed.any { it.isISOControl() || it == ' ' || it == '\t' }) return null

        val lower = trimmed.lowercase()
        if (BLOCKED_SCHEMES.any { lower.startsWith("$it:") }) return null

        val parsed = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return null
        }

        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme !in ALLOWED_SCHEMES) return null

        return when (scheme) {
            "http", "https" -> {
                val host = parsed.host?.trim().orEmpty()
                if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return null
                // Defence against URI.parse accepting odd hosts like "." or
                // "...". A valid registrable domain has a letter-ending TLD.
                if (!isValidHostShape(host)) return null
                trimmed
            }
            "tel", "mailto", "sms", "geo" -> trimmed
            else -> null
        }
    }

    /**
     * Lenient normalization for user-facing inputs.
     *
     *  - Trims whitespace.
     *  - If no scheme is present, assumes the input is a bare domain
     *    (optionally with path/query) and prepends `https://`.
     *  - Then delegates to [sanitize] for the full safety check.
     *
     * Examples:
     *  - `google.com`        → `https://google.com`
     *  - `www.github.com/x`  → `https://www.github.com/x`
     *  - `http://example.org`→ `http://example.org`
     *  - `javascript:alert(1)` → null (blocked scheme)
     *  - `random text`       → null (no dot, has whitespace)
     */
    fun normalizeForUserInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val candidate = if (SCHEME_REGEX.containsMatchIn(trimmed)) {
            trimmed
        } else {
            // No scheme → only accept if the authority portion looks like a
            // real domain. This rejects arbitrary text that happens to lack
            // spaces (e.g. "randomtext") while accepting `google.com`, IDN-ish
            // forms, and `sub.domain.co.uk`.
            val authority = trimmed
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
            if (!isValidHostShape(authority)) return null
            "https://$trimmed"
        }

        return sanitize(candidate)
    }

    fun domainOf(url: String): String? = try {
        URI(url).host
    } catch (_: URISyntaxException) {
        null
    }

    private fun isValidHostShape(host: String): Boolean {
        if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return false
        // Accept hostnames and IPv4-ish strings. We keep this deliberately
        // permissive: defence-in-depth lives in [sanitize] via scheme/host
        // checks; this is just the "is it plausibly a host?" gate.
        return HOST_REGEX.matches(host)
    }

    companion object {
        private const val MAX_URL_LENGTH = 2048
        private const val MAX_HOST_LENGTH = 253

        private val ALLOWED_SCHEMES = setOf("http", "https", "tel", "mailto", "sms", "geo")
        private val BLOCKED_SCHEMES = setOf(
            "javascript", "file", "content", "data", "intent",
            "android-app", "fb", "market", "jar",
        )

        /** RFC 3986 scheme prefix: `^<alpha>[<alpha><digit>+-.]*:`. */
        private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:")

        /**
         * Simplified registrable-domain check: at least one dot, each label
         * made of alphanumerics/hyphens (not leading/trailing hyphen), and
         * the final label is alphabetic length 2..63 (a TLD). This rejects
         * `randomtext`, `...`, trailing-dot junk, etc.
         */
        private val HOST_REGEX = Regex(
            "^[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?" +
                "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*" +
                "\\.[a-zA-Z]{2,63}$",
        )
    }
}
