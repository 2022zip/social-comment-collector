package com.socialcommentcollector.app.platform

import com.socialcommentcollector.app.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {
    private val detector = PlatformDetector()

    @Test
    fun `detects Xiaohongshu canonical and subdomain URLs`() {
        assertEquals(Platform.XIAOHONGSHU, detector.detect("https://xiaohongshu.com/explore/1"))
        assertEquals(Platform.XIAOHONGSHU, detector.detect("https://www.xiaohongshu.com/explore/1"))
        assertEquals(Platform.XIAOHONGSHU, detector.detect("https://creator.xiaohongshu.com/note/1"))
    }

    @Test
    fun `detects Xiaohongshu short URLs`() {
        assertEquals(Platform.XIAOHONGSHU, detector.detect("https://xhslink.com/a1b2"))
        assertEquals(Platform.XIAOHONGSHU, detector.detect("https://go.xhslink.com/a1b2"))
    }

    @Test
    fun `detects approved Jike host`() {
        assertEquals(Platform.JIKE, detector.detect("https://okjike.com/originalPosts/1"))
        assertEquals(Platform.JIKE, detector.detect("https://web.okjike.com/u/1"))
    }

    @Test
    fun `returns unknown for unsupported malformed and blank input`() {
        assertEquals(Platform.UNKNOWN, detector.detect("https://example.com/post"))
        assertEquals(Platform.UNKNOWN, detector.detect("not a URL"))
        assertEquals(Platform.UNKNOWN, detector.detect(""))
        assertEquals(Platform.UNKNOWN, detector.detect("  \n "))
    }

    @Test
    fun `matches parsed host rather than deceptive text`() {
        assertEquals(Platform.UNKNOWN, detector.detect("https://xiaohongshu.com.evil.example/post"))
        assertEquals(Platform.UNKNOWN, detector.detect("https://example.com/xhslink.com/post"))
        assertEquals(
            Platform.UNKNOWN,
            detector.detect("https://example.com/post?next=https://xiaohongshu.com/explore/1"),
        )
    }

    @Test
    fun `trims pasted whitespace and handles host case`() {
        assertEquals(
            Platform.XIAOHONGSHU,
            detector.detect("  https://WWW.XIAOHONGSHU.COM/explore/1  "),
        )
    }

    @Test
    fun `accepts only HTTPS web URLs`() {
        assertEquals(Platform.UNKNOWN, detector.detect("http://www.xiaohongshu.com/explore/1"))
        assertEquals(Platform.UNKNOWN, detector.detect("ftp://www.xiaohongshu.com/explore/1"))
    }
}
