package com.socialcommentcollector.app.platform

import com.socialcommentcollector.app.model.Platform
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {
    @Test
    fun `returns a direct supported URL without redirect lookup`() = runTest {
        var redirectCalled = false
        val resolver = UrlResolver(
            redirectResolver = RedirectResolver {
                redirectCalled = true
                it
            },
        )

        val result = resolver.resolve(" https://www.xiaohongshu.com/explore/123 ")

        assertFalse(redirectCalled)
        assertTrue(result is UrlResolutionResult.Success)
        result as UrlResolutionResult.Success
        assertEquals(Platform.XIAOHONGSHU, result.platform)
        assertEquals(URI("https://www.xiaohongshu.com/explore/123"), result.finalUrl)
    }

    @Test
    fun `resolves a short URL through an injected redirect resolver`() = runTest {
        val resolver = UrlResolver(
            redirectResolver = RedirectResolver {
                assertEquals(URI("https://xhslink.com/short"), it)
                URI("https://www.xiaohongshu.com/explore/resolved")
            },
        )

        val result = resolver.resolve("https://xhslink.com/short")

        assertTrue(result is UrlResolutionResult.Success)
        result as UrlResolutionResult.Success
        assertEquals(Platform.XIAOHONGSHU, result.platform)
        assertEquals(URI("https://www.xiaohongshu.com/explore/resolved"), result.finalUrl)
    }

    @Test
    fun `xhslink cn uses redirect resolver and preserves final query parameters`() = runTest {
        val final = URI(
            "https://www.xiaohongshu.com/explore/note-id?xsec_token=fixture-token&xsec_source=pc_feed",
        )
        val resolver = UrlResolver(
            redirectResolver = RedirectResolver {
                assertEquals(URI("https://xhslink.cn/o/short"), it)
                final
            },
        )

        val result = resolver.resolve("https://xhslink.cn/o/short")

        assertTrue(result is UrlResolutionResult.Success)
        assertEquals(final, (result as UrlResolutionResult.Success).finalUrl)
    }

    @Test
    fun `share text rejects unsupported final redirect host`() = runTest {
        val resolver = UrlResolver(RedirectResolver { URI("https://example.com/landing") })

        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM),
            resolver.resolve("https://xhslink.cn/o/short"),
        )
    }

    @Test
    fun `short redirect must terminate on Xiaohongshu web host`() = runTest {
        val resolver = UrlResolver(RedirectResolver { URI("https://go.xhslink.com/still-short") })

        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM),
            resolver.resolve("https://xhslink.cn/o/short"),
        )
    }

    @Test
    fun `rejects invalid input and unsupported hosts predictably`() = runTest {
        val resolver = UrlResolver(RedirectResolver { it })

        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.INVALID_URL),
            resolver.resolve("not a URL"),
        )
        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM),
            resolver.resolve("https://example.com/post"),
        )
        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.INSECURE_URL),
            resolver.resolve("http://xiaohongshu.com/post"),
        )
    }

    @Test
    fun `rejects unsupported and insecure redirect results`() = runTest {
        val unsupported = UrlResolver(RedirectResolver { URI("https://example.com/post") })
        val insecure = UrlResolver(RedirectResolver { URI("http://xiaohongshu.com/post") })

        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM),
            unsupported.resolve("https://xhslink.com/short"),
        )
        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.INSECURE_REDIRECT),
            insecure.resolve("https://xhslink.com/short"),
        )
    }

    @Test
    fun `returns a typed failure when redirect resolution throws`() = runTest {
        val resolver = UrlResolver(RedirectResolver { error("fixture failure") })

        assertEquals(
            UrlResolutionResult.Failure(UrlResolutionFailure.REDIRECT_FAILED),
            resolver.resolve("https://xhslink.com/short"),
        )
    }
}
