package com.kidswatch.tv.server

import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.playback.PlaybackCommand
import com.kidswatch.tv.playback.PlaybackCommandBus
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaybackRoutesTest {

    private var currentTime = 1000000L

    private fun testApp(
        block: suspend ApplicationTestBuilder.(token: String) -> Unit,
    ) = testApplication {
        val sessionManager = SessionManager(clock = { currentTime })

        application {
            install(ContentNegotiation) { json() }
            routing {
                playbackRoutes(sessionManager)
            }
        }

        val token = sessionManager.createSession()!!
        block(token)
    }

    @Test
    fun postStop_authenticated_returns200() = testApp { token ->
        val response = client.post("/playback/stop") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun postStop_unauthenticated_returns401() = testApp { _ ->
        val response = client.post("/playback/stop")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postStop_emitsStopCommand() = runTest {
        var received: PlaybackCommand? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = PlaybackCommandBus.commands.first()
        }
        testApp { token ->
            client.post("/playback/stop") {
                header("Authorization", "Bearer $token")
            }
        }
        job.join()
        assertEquals(PlaybackCommand.Stop, received)
    }

    @Test
    fun postSkip_authenticated_returns200() = testApp { token ->
        val response = client.post("/playback/skip") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun postSkip_unauthenticated_returns401() = testApp { _ ->
        val response = client.post("/playback/skip")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postSkip_emitsSkipNextCommand() = runTest {
        var received: PlaybackCommand? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = PlaybackCommandBus.commands.first()
        }
        testApp { token ->
            client.post("/playback/skip") {
                header("Authorization", "Bearer $token")
            }
        }
        job.join()
        assertEquals(PlaybackCommand.SkipNext, received)
    }

    @Test
    fun postPause_authenticated_returns200() = testApp { token ->
        val response = client.post("/playback/pause") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
