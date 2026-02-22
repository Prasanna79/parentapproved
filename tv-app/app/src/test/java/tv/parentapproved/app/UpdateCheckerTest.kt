package tv.parentapproved.app

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker(currentVersionCode: Int = 12) = UpdateChecker(
        versionCheckUrl = server.url("/version.json").toString(),
        currentVersionCode = currentVersionCode,
        client = OkHttpClient(),
    )

    @Test
    fun `parses valid version json`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"latest": "0.9.1", "latestCode": 13, "url": "https://example.com/app.apk", "releaseNotes": "Bug fixes"}
        """.trimIndent()))

        val checker = checker(currentVersionCode = 12)
        val result = checker.checkForUpdate()

        assertNotNull(result)
        assertEquals("0.9.1", result!!.latest)
        assertEquals(13, result.latestCode)
        assertEquals("https://example.com/app.apk", result.url)
        assertEquals("Bug fixes", result.releaseNotes)
    }

    @Test
    fun `detects update available when latestCode is higher`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"latest": "1.0.0", "latestCode": 15, "url": "https://example.com/app.apk"}
        """.trimIndent()))

        val checker = checker(currentVersionCode = 12)
        checker.checkForUpdate()

        assertTrue(checker.isUpdateAvailable)
    }

    @Test
    fun `no update when current version is latest`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"latest": "0.9.0", "latestCode": 12, "url": "https://example.com/app.apk"}
        """.trimIndent()))

        val checker = checker(currentVersionCode = 12)
        checker.checkForUpdate()

        assertFalse(checker.isUpdateAvailable)
    }

    @Test
    fun `no update when current version is higher`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"latest": "0.8.0", "latestCode": 11, "url": "https://example.com/app.apk"}
        """.trimIndent()))

        val checker = checker(currentVersionCode = 12)
        checker.checkForUpdate()

        assertFalse(checker.isUpdateAvailable)
    }

    @Test
    fun `handles 404 gracefully`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val checker = checker()
        val result = checker.checkForUpdate()

        assertNull(result)
        assertFalse(checker.isUpdateAvailable)
    }

    @Test
    fun `handles malformed json gracefully`() = runBlocking {
        server.enqueue(MockResponse().setBody("not json"))

        val checker = checker()
        val result = checker.checkForUpdate()

        assertNull(result)
        assertFalse(checker.isUpdateAvailable)
    }

    @Test
    fun `handles network timeout gracefully`() = runBlocking {
        // Don't enqueue any response — connection will fail
        server.shutdown()

        val checker = checker()
        val result = checker.checkForUpdate()

        assertNull(result)
        assertFalse(checker.isUpdateAvailable)
    }

    @Test
    fun `ignores unknown json fields`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"latest": "0.9.1", "latestCode": 13, "url": "https://example.com/app.apk", "unknown": "field"}
        """.trimIndent()))

        val checker = checker()
        val result = checker.checkForUpdate()

        assertNotNull(result)
        assertEquals("0.9.1", result!!.latest)
    }
}
