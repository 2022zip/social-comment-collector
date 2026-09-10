package com.socialcommentcollector.app.platform.xiaohongshu.diagnostics

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Debug-only observation adapter. It uses one-shot JavaScript evaluation and never exposes a
 * JavaScript interface, cookies, storage values, network payloads, or native capabilities.
 */
class AndroidWebViewDiagnosticEvaluator(
    private val webView: WebView,
) : DiagnosticPageEvaluator {
    override suspend fun capture(): RawDiagnosticSnapshot = suspendCancellableCoroutine { continuation ->
        capture { result ->
            if (continuation.isActive) continuation.resumeWith(result)
        }
    }

    fun capture(callback: (Result<RawDiagnosticSnapshot>) -> Unit) {
        webView.evaluateJavascript(CAPTURE_SCRIPT) { encoded ->
            callback(runCatching { parse(encoded) })
        }
    }

    override suspend fun scrollOneViewport() = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(SCROLL_SCRIPT) {
            if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
        }
    }

    private fun parse(encoded: String): RawDiagnosticSnapshot {
        val jsonText = JSONTokener(encoded).nextValue() as? String
            ?: error("Diagnostic script did not return a JSON string")
        val json = JSONObject(jsonText)
        return RawDiagnosticSnapshot(
            url = json.optString("url"),
            title = json.optString("title"),
            readyState = json.optString("readyState"),
            elementCount = json.optInt("elementCount"),
            scrollHeight = json.optInt("scrollHeight"),
            viewportHeight = json.optInt("viewportHeight"),
            structuredStateKeys = json.getJSONArray("structuredStateKeys").strings(),
            semanticEntries = json.getJSONArray("semanticEntries").objects().map { entry ->
                RawSemanticEntry(
                    tag = entry.optString("tag"),
                    role = entry.optNullableString("role"),
                    label = entry.optNullableString("label"),
                    text = entry.optString("text"),
                )
            },
        )
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::optString)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    private fun JSONObject.optNullableString(name: String): String? = optString(name).takeIf(String::isNotBlank)

    private companion object {
        val CAPTURE_SCRIPT = """
            (() => {
              const clip = (value, limit = 160) => String(value || '').replace(/\s+/g, ' ').trim().slice(0, limit);
              const semanticEntries = Array.from(
                document.querySelectorAll('h1,h2,h3,article,[role],[aria-label]')
              ).slice(0, 60).map((element) => ({
                tag: clip(element.tagName, 24),
                role: clip(element.getAttribute('role'), 40),
                label: clip(element.getAttribute('aria-label'), 80),
                text: clip(element.innerText || element.textContent, 160)
              }));
              const structuredStateKeys = Object.keys(window)
                .filter((key) => /(state|initial|data|note|comment)/i.test(key))
                .slice(0, 40);
              return JSON.stringify({
                url: location.origin === 'null' ? location.href : location.origin + location.pathname,
                title: clip(document.title, 160),
                readyState: document.readyState,
                elementCount: document.getElementsByTagName('*').length,
                scrollHeight: Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0),
                viewportHeight: window.innerHeight,
                structuredStateKeys,
                semanticEntries
              });
            })()
        """.trimIndent()

        const val SCROLL_SCRIPT = "window.scrollBy({ top: Math.max(window.innerHeight * 0.8, 1), behavior: 'auto' }); true;"
    }
}
