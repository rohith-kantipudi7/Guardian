package com.guardian.app.data.media

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Tiny HTTP server so the dashboard can show the REAL captured media. The ESP32 black box has no
 * media storage, so the phone serves its latest cabin/road photos and crash audio directly over the
 * LAN/hotspot; the transferred black-box JSON carries this server's base URL. Raw sockets keep it
 * dependency-free; clips are small so whole-file responses are fine.
 */
class MediaServer(private val port: Int = 8080) {
    @Volatile var cabinPath: String? = null
    @Volatile var roadPath: String? = null
    @Volatile var afterAudioPath: String? = null
    @Volatile var preAudioPath: String? = null

    private var job: Job? = null
    @Volatile private var server: ServerSocket? = null

    /** The phone's LAN/hotspot base URL (e.g. http://192.168.137.46:8080), or null if offline. */
    fun baseUrl(): String? = lanIp()?.let { "http://$it:$port" }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                val s = ServerSocket(port)
                server = s
                while (!s.isClosed) {
                    val client = runCatching { s.accept() }.getOrNull() ?: break
                    launch(Dispatchers.IO) { runCatching { handle(client) } }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Media server stopped: ${e.message}")
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        job?.cancel()
    }

    private fun handle(client: Socket) {
        client.use {
            val input = it.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            while (true) { val l = input.readLine() ?: break; if (l.isEmpty()) break } // drain headers
            val path = requestLine.split(" ").getOrNull(1)?.substringBefore('?') ?: "/"
            val (file, type) = resolve(path)
            val out = it.getOutputStream()
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                out.write(
                    ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $type\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(),
                )
                out.write(bytes)
            } else {
                val body = (if (path == "/") "guardian media server" else "not found").toByteArray()
                val status = if (path == "/") "200 OK" else "404 Not Found"
                out.write(
                    ("HTTP/1.1 $status\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(),
                )
                out.write(body)
            }
            out.flush()
        }
    }

    private fun resolve(path: String): Pair<File?, String> = when (path) {
        "/cabin.jpg" -> cabinPath?.let(::File) to "image/jpeg"
        "/road.jpg" -> roadPath?.let(::File) to "image/jpeg"
        "/after.m4a" -> afterAudioPath?.let(::File) to "audio/mp4"
        "/pre.m4a" -> preAudioPath?.let(::File) to "audio/mp4"
        else -> null to "text/plain"
    }

    private fun lanIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private companion object { const val TAG = "MediaServer" }
}
