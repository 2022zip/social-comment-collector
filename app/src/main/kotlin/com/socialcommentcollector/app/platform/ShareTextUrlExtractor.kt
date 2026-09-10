package com.socialcommentcollector.app.platform

import java.net.URI
import java.net.URISyntaxException

enum class ShareTextUrlExtractionFailure {
    NO_SUPPORTED_URL_FOUND,
}

sealed interface ShareTextUrlExtractionResult {
    data class Success(val url: URI) : ShareTextUrlExtractionResult
    data class Failure(val reason: ShareTextUrlExtractionFailure) : ShareTextUrlExtractionResult
}

class ShareTextUrlExtractor(
    private val platformDetector: PlatformDetector = PlatformDetector(),
) {
    fun extract(rawInput: String): ShareTextUrlExtractionResult {
        WEB_URL.findAll(rawInput).forEach { match ->
            val candidate = match.value.trimEnd(*TRAILING_SHARE_PUNCTUATION)
            val uri = candidate.toUriOrNull() ?: return@forEach
            if (platformDetector.detectSupportedHost(uri) != com.socialcommentcollector.app.model.Platform.UNKNOWN) {
                return ShareTextUrlExtractionResult.Success(uri)
            }
        }
        return ShareTextUrlExtractionResult.Failure(ShareTextUrlExtractionFailure.NO_SUPPORTED_URL_FOUND)
    }

    internal fun hasWebUrlCandidate(rawInput: String): Boolean = WEB_URL.containsMatchIn(rawInput)

    private fun String.toUriOrNull(): URI? = try {
        URI(this).takeIf { it.isAbsolute && !it.isOpaque && it.host != null && it.userInfo == null }
    } catch (_: URISyntaxException) {
        null
    }

    private companion object {
        val WEB_URL = Regex(
            "https?://[^\\s<>\\\"'“”‘’，。！？；：、【】（）《》]+",
            RegexOption.IGNORE_CASE,
        )
        val TRAILING_SHARE_PUNCTUATION = charArrayOf(
            '.', ',', '!', '?', ';', ':',
            '。', '，', '！', '？', '；', '：', '、',
            '~', '～', '】', '》', '）', ')', ']', '}',
        )
    }
}
