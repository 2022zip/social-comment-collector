package com.socialcommentcollector.app

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test fun launchesWithUnifiedInputStartButtonAndSecureWebView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.url_input)).check(matches(isDisplayed()))
            onView(withId(R.id.start_collection)).check(matches(withText(R.string.start_collection)))
            onView(withId(R.id.web_view)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val browser = activity.findViewById<WebView>(R.id.web_view)
                assertNotNull(browser)
                assertTrue(browser.settings.javaScriptEnabled)
                assertFalse(browser.settings.allowFileAccess)
                assertFalse(browser.settings.allowContentAccess)
                assertTrue(browser.settings.domStorageEnabled)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, browser.settings.mixedContentMode)
            }
        }
    }

    @Test fun clearRemovesInputAndRecreationPreservesInput() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.url_input)).perform(replaceText("https://example.com"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.url_input)).check(matches(withText("https://example.com")))
            onView(withId(R.id.clear_url)).perform(scrollTo(), click())
            onView(withId(R.id.url_input)).check(matches(withText("")))
        }
    }
}
