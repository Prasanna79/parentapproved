package com.kidswatch.tv.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardAssetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun indexHtml_existsInAssets() {
        val assets = context.assets.list("") ?: emptyArray()
        assertTrue("index.html should exist in assets", assets.contains("index.html"))
    }

    @Test
    fun indexHtml_containsPlaylistForm() {
        val html = context.assets.open("index.html").bufferedReader().readText()
        assertTrue("index.html should contain playlist form", html.contains("playlist-form"))
    }

    @Test
    fun appJs_containsAuthFunction() {
        val js = context.assets.open("app.js").bufferedReader().readText()
        assertTrue("app.js should contain auth logic", js.contains("/auth"))
    }
}
