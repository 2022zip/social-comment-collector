package com.socialcommentcollector.app.platform.xiaohongshu.diagnostics

import java.net.URI

data class RawSemanticEntry(
    val tag: String,
    val role: String?,
    val label: String?,
    val text: String,
)

data class RawDiagnosticSnapshot(
    val url: String,
    val title: String,
    val readyState: String,
    val elementCount: Int,
    val scrollHeight: Int,
    val viewportHeight: Int,
    val structuredStateKeys: List<String>,
    val semanticEntries: List<RawSemanticEntry>,
)

data class DiagnosticSemanticEntry(
    val tag: String,
    val role: String?,
    val label: String?,
    val text: String,
)

data class DiagnosticSnapshot(
    val safeUrl: String,
    val title: String,
    val readyState: String,
    val elementCount: Int,
    val scrollHeight: Int,
    val viewportHeight: Int,
    val structuredStateKeys: List<String>,
    val semanticEntries: List<DiagnosticSemanticEntry>,
) {
    internal fun pageFingerprint(): String = listOf(
        readyState,
        elementCount,
        scrollHeight,
        viewportHeight,
        structuredStateKeys,
        semanticEntries,
    ).joinToString("|")
}

class DiagnosticSanitizer(
    private val maxSemanticEntries: Int = 60,
    private val maxStateKeys: Int = 40,
    private val maxTextLength: Int = 160,
) {
    init {
        require(maxSemanticEntries > 0)
        require(maxStateKeys > 0)
        require(maxTextLength > 0)
    }

    fun sanitize(raw: RawDiagnosticSnapshot): DiagnosticSnapshot = DiagnosticSnapshot(
        safeUrl = safeUrl(raw.url),
        title = safeText(raw.title),
        readyState = safeText(raw.readyState),
        elementCount = raw.elementCount.coerceAtLeast(0),
        scrollHeight = raw.scrollHeight.coerceAtLeast(0),
        viewportHeight = raw.viewportHeight.coerceAtLeast(0),
        structuredStateKeys = raw.structuredStateKeys
            .asSequence()
            .map(::safeText)
            .filter(String::isNotBlank)
            .distinct()
            .take(maxStateKeys)
            .toList(),
        semanticEntries = raw.semanticEntries
            .asSequence()
            .take(maxSemanticEntries)
            .map {
                DiagnosticSemanticEntry(
                    tag = safeText(it.tag),
                    role = it.role?.let(::safeText)?.ifBlank { null },
                    label = it.label?.let(::safeText)?.ifBlank { null },
                    text = safeText(it.text),
                )
            }
            .toList(),
    )

    private fun safeUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed == "about:blank") return trimmed
        return runCatching {
            val parsed = URI(trimmed)
            if (parsed.scheme.isNullOrBlank() || parsed.host.isNullOrBlank()) return@runCatching "[invalid-url]"
            URI(parsed.scheme, null, parsed.host, parsed.port, parsed.path.orEmpty(), null, null).toString()
        }.getOrDefault("[invalid-url]")
    }

    private fun safeText(value: String): String = value
        .replace(BEARER_VALUE, "Bearer [REDACTED]")
        .replace(SENSITIVE_VALUE) { match -> "${match.groupValues[1]}=[REDACTED]" }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxTextLength)

    private companion object {
        val BEARER_VALUE = Regex("(?i)bearer\\s+[a-z0-9._~+/=-]+")
        val SENSITIVE_VALUE = Regex(
            "(?i)\\b(token|xsec_token|cookie|password|passwd|authorization|auth|secret)\\s*[:=]\\s*[^\\s&]+",
        )
    }
}

sealed interface DiagnosticObservation {
    val before: DiagnosticSnapshot
    val after: DiagnosticSnapshot
    val observationCount: Int

    data class Changed(
        override val before: DiagnosticSnapshot,
        override val after: DiagnosticSnapshot,
        override val observationCount: Int,
    ) : DiagnosticObservation

    data class NavigationChanged(
        override val before: DiagnosticSnapshot,
        override val after: DiagnosticSnapshot,
        override val observationCount: Int,
    ) : DiagnosticObservation

    /** No page change was observed before the diagnostic bound. This is never an end-of-comments signal. */
    data class Stalled(
        override val before: DiagnosticSnapshot,
        override val after: DiagnosticSnapshot,
        override val observationCount: Int,
    ) : DiagnosticObservation
}
