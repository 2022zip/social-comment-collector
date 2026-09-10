package com.socialcommentcollector.app.platform

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareInputResolverTest {
    @Test
    fun `extracts first supported URL before resolving redirect`() = runTest {
        val final = URI("https://www.xiaohongshu.com/explore/note?xsec_token=fixture")
        val resolver = ShareInputResolver(
            extractor = ShareTextUrlExtractor(),
            urlResolver = UrlResolver(RedirectResolver { final }),
        )

        val result = resolver.resolve(
            "说明 https://example.com/a 正文 https://xhslink.cn/o/short 保留口令",
        )

        assertTrue(result is UrlResolutionResult.Success)
        result as UrlResolutionResult.Success
        assertEquals(URI("https://xhslink.cn/o/short"), result.originalUrl)
        assertEquals(final, result.finalUrl)
    }

    @Test
    fun `maps absent and unsupported web URLs predictably`() = runTest {
        val resolver = ShareInputResolver(ShareTextUrlExtractor(), UrlResolver(RedirectResolver { it }))

        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.INVALID_URL),
            resolver.resolve("只有分享文案"),
        )
        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM),
            resolver.resolve("说明 https://example.com/post"),
        )
    }
}
