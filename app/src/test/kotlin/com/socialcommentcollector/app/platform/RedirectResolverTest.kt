package com.socialcommentcollector.app.platform

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RedirectResolverTest {
    @Test
    fun `follows one redirect to a supported final URL`() = runTest {
        val resolver = resolver(
            "https://xhslink.com/a" to redirect("https://www.xiaohongshu.com/explore/a"),
            "https://www.xiaohongshu.com/explore/a" to ok(),
        )

        assertEquals(
            URI("https://www.xiaohongshu.com/explore/a"),
            resolver.resolve(URI("https://xhslink.com/a")),
        )
    }

    @Test
    fun `follows multiple redirects within the limit`() = runTest {
        val resolver = resolver(
            "https://xhslink.com/a" to redirect("/b"),
            "https://xhslink.com/b" to redirect("https://www.xiaohongshu.com/explore/a"),
            "https://www.xiaohongshu.com/explore/a" to ok(),
        )

        assertEquals(
            URI("https://www.xiaohongshu.com/explore/a"),
            resolver.resolve(URI("https://xhslink.com/a")),
        )
    }

    @Test
    fun `follows relative redirects across approved short hosts and preserves final query`() = runTest {
        val final = "https://www.xiaohongshu.com/explore/note?xsec_token=fixture&xsec_source=pc_feed"
        val resolver = resolver(
            "https://xhslink.cn/o/a" to redirect("/o/b"),
            "https://xhslink.cn/o/b" to redirect("https://go.xhslink.com/c"),
            "https://go.xhslink.com/c" to redirect(final),
            final to ok(),
        )

        assertEquals(URI(final), resolver.resolve(URI("https://xhslink.cn/o/a")))
    }

    @Test
    fun `rejects redirect loops`() = runTest {
        val resolver = resolver(
            "https://xhslink.com/a" to redirect("/b"),
            "https://xhslink.com/b" to redirect("/a"),
        )

        assertFailure(RedirectFailure.LOOP) {
            resolver.resolve(URI("https://xhslink.com/a"))
        }
    }

    @Test
    fun `rejects more redirects than the configured limit`() = runTest {
        val resolver = resolver(
            "https://xhslink.com/a" to redirect("/b"),
            "https://xhslink.com/b" to redirect("/c"),
            "https://xhslink.com/c" to ok(),
            maxRedirects = 1,
        )

        assertFailure(RedirectFailure.TOO_MANY_REDIRECTS) {
            resolver.resolve(URI("https://xhslink.com/a"))
        }
    }

    @Test
    fun `rejects HTTPS downgrade`() = runTest {
        val resolver = resolver(
            "https://xhslink.com/a" to redirect("http://www.xiaohongshu.com/explore/a"),
        )

        assertFailure(RedirectFailure.INSECURE_REDIRECT) {
            resolver.resolve(URI("https://xhslink.com/a"))
        }
    }

    @Test
    fun `rejects unsupported final host`() = runTest {
        val resolver = resolver(
            "https://xhslink.com/a" to redirect("https://example.com/a"),
        )

        assertFailure(RedirectFailure.UNSUPPORTED_FINAL_URL) {
            resolver.resolve(URI("https://xhslink.com/a"))
        }
    }

    private fun resolver(
        vararg responses: Pair<String, RedirectResponse>,
        maxRedirects: Int = 5,
    ): HttpRedirectResolver {
        val fixtures = responses.toMap()
        return HttpRedirectResolver(
            transport = RedirectTransport { url -> fixtures[url.toString()] ?: ok() },
            maxRedirects = maxRedirects,
        )
    }

    private suspend fun assertFailure(
        expected: RedirectFailure,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected redirect failure $expected")
        } catch (failure: RedirectResolutionException) {
            assertEquals(expected, failure.reason)
        }
    }

    private companion object {
        fun redirect(location: String) = RedirectResponse(302, location)
        fun ok() = RedirectResponse(200, null)
    }
}
