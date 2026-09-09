package com.socialcommentcollector.app

import android.os.Bundle
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialcommentcollector.app.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var browser: WebView
    private val model: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as CollectorApplication
                MainViewModel(app.repository, createSavedStateHandle(), app.startCollection)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
        val input = findViewById<EditText>(R.id.url_input)
        input.setText(model.uiState.value.url)
        input.doAfterTextChanged { model.updateUrl(it?.toString().orEmpty()) }
        findViewById<Button>(R.id.clear_url).setOnClickListener { model.clearUrl() }

        browser = findViewById(R.id.web_view)
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
        }
        WebView.setWebContentsDebuggingEnabled(false)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, false)
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.toString() != "about:blank" && !isSecureWebAddress(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url != "about:blank") model.onXiaohongshuPageLoaded(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) model.onXiaohongshuPageUnavailable()
            }
        }
        browser.loadUrl("about:blank")
        findViewById<Button>(R.id.open_page).setOnClickListener {
            val uri = Uri.parse(model.uiState.value.url.trim())
            if (isSecureWebAddress(uri)) browser.loadUrl(uri.toString())
            else Toast.makeText(this, R.string.secure_url_required, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.start_collection).setOnClickListener { model.startCollection() }
        findViewById<Button>(R.id.clear_login).setOnClickListener { model.clearXiaohongshuSession() }
        listOf(R.id.progress_list, R.id.share_markdown).forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                Toast.makeText(this, R.string.placeholder_message, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.switch_language).setOnClickListener {
            val isEnglish = resources.configuration.locales[0].language == "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (isEnglish) "zh-CN" else "en"))
        }
        lifecycleScope.launch {
            var handledNavigationId: Long? = null
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { state ->
                    if (input.text.toString() != state.url) input.setText(state.url)
                    findViewById<Button>(R.id.open_page).isEnabled = state.canUseUrlActions
                    findViewById<Button>(R.id.start_collection).isEnabled = state.canUseUrlActions
                    state.navigationRequest?.takeIf { it.id != handledNavigationId }?.let { request ->
                        handledNavigationId = request.id
                        browser.loadUrl(request.url)
                    }
                    state.message?.let { message ->
                        Toast.makeText(this@MainActivity, message.stringResource(), Toast.LENGTH_SHORT).show()
                        model.consumeMessage()
                    }
                    findViewById<TextView>(R.id.task_summary).text = when {
                        state.storageUnavailable -> getString(R.string.storage_unavailable)
                        state.tasks.isEmpty() -> getString(R.string.no_tasks)
                        else -> getString(R.string.task_count, state.tasks.size)
                    }
                }
            }
        }
    }

    private fun com.socialcommentcollector.app.ui.MainMessage.stringResource(): Int = when (this) {
        com.socialcommentcollector.app.ui.MainMessage.INVALID_URL -> R.string.invalid_url
        com.socialcommentcollector.app.ui.MainMessage.INSECURE_URL -> R.string.secure_url_required
        com.socialcommentcollector.app.ui.MainMessage.UNSUPPORTED_PLATFORM -> R.string.unsupported_platform
        com.socialcommentcollector.app.ui.MainMessage.REDIRECT_FAILED -> R.string.redirect_failed
        com.socialcommentcollector.app.ui.MainMessage.PLATFORM_UNAVAILABLE -> R.string.platform_unavailable
        com.socialcommentcollector.app.ui.MainMessage.LOGIN_REQUIRED -> R.string.login_required
        com.socialcommentcollector.app.ui.MainMessage.SESSION_READY -> R.string.session_ready
        com.socialcommentcollector.app.ui.MainMessage.SESSION_CLEARED -> R.string.session_cleared
        com.socialcommentcollector.app.ui.MainMessage.SESSION_UNAVAILABLE -> R.string.session_unavailable
    }

    // Restrict WebView navigation schemes; platform/source recognition belongs to T0002.
    private fun isSecureWebAddress(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()

    override fun onResume() {
        super.onResume()
        if (::browser.isInitialized) browser.onResume()
    }

    override fun onPause() {
        if (::browser.isInitialized) browser.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.destroy()
        }
        super.onDestroy()
    }
}
