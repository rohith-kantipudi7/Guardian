package com.guardian.app.data.remote

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

@Serializable
data class LiveCrashMessage(val type: String, val event: CrashDto? = null)

/**
 * Live crash feed from the response center (`/ws/emergency`). Companion-only: the phone
 * listens to what its vehicle device already reported; it never forwards emergencies.
 */
class BackendEventStream(private val client: OkHttpClient = defaultClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    fun crashes(backendUrl: String): Flow<LiveCrashMessage> = flow {
        val wsUrl = backendUrl.trimEnd('/').replaceFirst(Regex("^http"), "ws") + "/ws/emergency"
        var attempt = 0
        while (true) {
            emitAll(connectOnce(wsUrl))
            // Socket closed or failed: back off and reconnect.
            delay((1000L shl attempt.coerceAtMost(4)).coerceAtMost(15_000L))
            attempt++
        }
    }

    private fun connectOnce(url: String): Flow<LiveCrashMessage> = callbackFlow {
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { json.decodeFromString<LiveCrashMessage>(text) }.getOrNull() ?: return
                if (msg.event != null) trySend(msg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("GuardianLive", "live feed unavailable: ${t.message}")
                channel.close()
            }
        })
        awaitClose { socket.cancel() }
    }

    private companion object {
        fun defaultClient() = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
