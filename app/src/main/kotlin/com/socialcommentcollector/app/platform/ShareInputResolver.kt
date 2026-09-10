package com.socialcommentcollector.app.platform

class ShareInputResolver(
    private val extractor: ShareTextUrlExtractor,
    private val urlResolver: UrlResolver,
) {
    suspend fun resolve(rawInput: String): UrlResolutionResult = when (val extracted = extractor.extract(rawInput)) {
        is ShareTextUrlExtractionResult.Success -> urlResolver.resolve(extracted.url.toString())
        is ShareTextUrlExtractionResult.Failure -> UrlResolutionResult.Failure(
            if (extractor.hasWebUrlCandidate(rawInput)) {
                UrlResolutionFailure.UNSUPPORTED_PLATFORM
            } else {
                UrlResolutionFailure.INVALID_URL
            },
        )
    }
}
