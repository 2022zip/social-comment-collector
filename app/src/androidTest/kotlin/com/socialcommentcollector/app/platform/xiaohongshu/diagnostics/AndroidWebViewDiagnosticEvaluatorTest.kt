package com.socialcommentcollector.app.platform.xiaohongshu.diagnostics

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.socialcommentcollector.app.MainActivity
import com.socialcommentcollector.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidWebViewDiagnosticEvaluatorTest {
    @Test
    fun capturesAboutBlankWithoutCookieOrQueryValues() {
        val completed = CountDownLatch(1)
        var result: Result<RawDiagnosticSnapshot>? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                val evaluator = AndroidWebViewDiagnosticEvaluator(webView)
                evaluator.capture {
                    result = it
                    completed.countDown()
                }
            }
            assertEquals(true, completed.await(5, TimeUnit.SECONDS))
            val snapshot = requireNotNull(result).getOrThrow()
            assertEquals("about:blank", snapshot.url)
            assertFalse(snapshot.toString().contains("cookie", ignoreCase = true))
        }
    }
}
