package com.nfcmanager.app.data.nfc

/**
 * Minimal, defensive vCard 2.1/3.0/4.0 property extractor.
 *
 * This is intentionally tolerant: malformed lines are skipped, parameters
 * after the first `;` (like `TYPE=CELL`) are ignored, and nothing throws.
 * A real implementation would delegate to ez-vcard; here we only need enough
 * to safely surface Display Name / Phone / Email / Org in the UI.
 */
internal object VCardParser {

    data class Result(
        val displayName: String?,
        val phones: List<String>,
        val emails: List<String>,
        val organization: String?,
    )

    fun parse(raw: String): Result {
        val lines = unfold(raw.replace("\r\n", "\n").lines())
        var displayName: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var organization: String? = null
        var structuredN: String? = null

        for (line in lines) {
            val sep = line.indexOf(':').takeIf { it > 0 } ?: continue
            val key = line.substring(0, sep).substringBefore(';').uppercase().trim()
            val value = line.substring(sep + 1).trim()
            if (value.isEmpty()) continue
            when (key) {
                "FN" -> displayName = value
                "N" -> structuredN = value
                "TEL" -> phones += value
                "EMAIL" -> emails += value
                "ORG" -> organization = value.replace(';', ' ').trim()
            }
        }

        if (displayName == null && structuredN != null) {
            // N: family;given;middle;prefix;suffix  →  "given family"
            val parts = structuredN.split(';')
            val family = parts.getOrNull(0).orEmpty().trim()
            val given = parts.getOrNull(1).orEmpty().trim()
            displayName = listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
                .ifEmpty { null }
        }

        return Result(displayName, phones.toList(), emails.toList(), organization)
    }

    /** RFC 2425: continuation lines begin with a single space or tab. */
    private fun unfold(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (out.isNotEmpty()) {
                    out[out.lastIndex] = out.last() + line.substring(1)
                }
            } else {
                out += line
            }
        }
        return out
    }
}
