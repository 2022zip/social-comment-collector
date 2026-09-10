package com.socialcommentcollector.app.platform

import com.socialcommentcollector.app.model.Platform
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class PlatformDetector {
    fun detect(input: String): Platform {
        val uri = input.trim().toUriOrNull() ?: return Platform.UNKNOWN
        return detect(uri)
    }

    internal fun detect(uri: URI): Platform {
        if (!HTTPS_SCHEME.equals(uri.scheme, ignoreCase = true)) return Platform.UNKNOWN
        if (uri.userInfo != null) return Platform.UNKNOWN

        val host = uri.normalizedHost() ?: return Platform.UNKNOWN
        return detectHost(host)
    }

    internal fun detectSupportedHost(uri: URI): Platform {
        if (uri.userInfo != null || uri.isOpaque || uri.host == null) return Platform.UNKNOWN
        return detectHost(uri.normalizedHost() ?: return Platform.UNKNOWN)
    }

    private fun detectHost(host: String): Platform = when {
        host.isDomainOrSubdomainOf(XIAOHONGSHU_DOMAIN) -> Platform.XIAOHONGSHU
        XIAOHONGSHU_SHORT_DOMAINS.any { domain -> host.isDomainOrSubdomainOf(domain) } -> Platform.XIAOHONGSHU
        host.isDomainOrSubdomainOf(JIKE_DOMAIN) -> Platform.JIKE
        else -> Platform.UNKNOWN
    }

    internal fun isXiaohongshuShortLink(uri: URI): Boolean =
        uri.normalizedHost()?.let { host ->
            XIAOHONGSHU_SHORT_DOMAINS.any { domain -> host.isDomainOrSubdomainOf(domain) }
        } == true

    private fun URI.normalizedHost(): String? =
        host?.lowercase(Locale.ROOT)?.removeSuffix(".")?.takeIf(String::isNotEmpty)

    private fun String.isDomainOrSubdomainOf(domain: String): Boolean =
        this == domain || endsWith(".$domain")

    private fun String.toUriOrNull(): URI? = try {
        URI(this).takeIf { it.isAbsolute && !it.isOpaque }
    } catch (_: URISyntaxException) {
        null
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val XIAOHONGSHU_DOMAIN = "xiaohongshu.com"
        val XIAOHONGSHU_SHORT_DOMAINS = setOf("xhslink.com", "xhslink.cn")
        const val JIKE_DOMAIN = "okjike.com"
    }
}
