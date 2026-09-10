package com.socialcommentcollector.app.platform

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextUrlExtractorTest {
    private val extractor = ShareTextUrlExtractor()

    @Test
    fun `extracts pure canonical and short Xiaohongshu URLs`() {
        assertUrl("https://xiaohongshu.com/explore/1", "https://xiaohongshu.com/explore/1")
        assertUrl("https://www.xiaohongshu.com/explore/1", "https://www.xiaohongshu.com/explore/1")
        assertUrl("https://xhslink.com/a1", "https://xhslink.com/a1")
        assertUrl("https://xhslink.cn/o/a1", "https://xhslink.cn/o/a1")
    }

    @Test
    fun `extracts supported URL from Chinese multiline share text`() {
        assertUrl(
            "如果我能像你那么潇洒就好了 🚗\n车轮碾过大地… https://xhslink.cn/o/5teLYHz60xA 保留口令，直达【小红书】围观~",
            "https://xhslink.cn/o/5teLYHz60xA",
        )
    }

    @Test
    fun `trims punctuation directly following URL`() {
        assertUrl(
            "看看这个：https://www.xiaohongshu.com/explore/abc，真的不错。",
            "https://www.xiaohongshu.com/explore/abc",
        )
    }

    @Test
    fun `selects first supported URL rather than first arbitrary URL`() {
        assertUrl(
            "说明 https://example.com/tracker 正文 https://xhslink.cn/o/abc123 保留口令",
            "https://xhslink.cn/o/abc123",
        )
    }

    @Test
    fun `supports approved Jike URL in share text`() {
        assertUrl(
            "来自即刻 https://web.okjike.com/u/example 分享",
            "https://web.okjike.com/u/example",
        )
    }

    @Test
    fun `returns typed failure for no supported URL and deceptive hosts`() {
        assertFailure("只有分享说明，没有链接")
        assertFailure("https://example.com/post")
        assertFailure("https://xhslink.cn.evil.example/o/abc")
        assertFailure("https://evil-xhslink.cn/o/abc")
    }

    private fun assertUrl(raw: String, expected: String) {
        val result = extractor.extract(raw)
        assertTrue(result is ShareTextUrlExtractionResult.Success)
        assertEquals(URI(expected), (result as ShareTextUrlExtractionResult.Success).url)
    }

    private fun assertFailure(raw: String) {
        assertEquals(
            ShareTextUrlExtractionResult.Failure(ShareTextUrlExtractionFailure.NO_SUPPORTED_URL_FOUND),
            extractor.extract(raw),
        )
    }
}
