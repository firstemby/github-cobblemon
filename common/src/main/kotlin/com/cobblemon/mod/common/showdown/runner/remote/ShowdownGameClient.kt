package com.cobblemon.mod.common.showdown.runner.remote

import com.cobblemon.mod.common.Cobblemon
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * Allows for connection to a Pokemon Showdown game server for battling against players using the game server
 *
 * @since March 1, 2025
 * @author Apion
 */
class ShowdownGameClient(val socketUri: String) {
    val messageQueue = ArrayDeque<String>()
    var socket: WebSocket? = null

    fun connect() {
        val httpClient = HttpClient.newHttpClient()
        val wsBuilder = httpClient.newWebSocketBuilder()
        val future = wsBuilder.buildAsync(URI.create(socketUri), ClientListener(this))
        socket = future.get(30, TimeUnit.SECONDS)
    }

    fun close() {
        socket?.abort()
    }

    fun sendMessage(msg: String) {
        socket?.sendText(msg, true)?.get()
    }

    fun pollMessage() {
        socket?.request(1)
    }

    private class ClientListener(val client: ShowdownGameClient) : WebSocket.Listener {
        override fun onOpen(webSocket: WebSocket?) {
            Cobblemon.LOGGER.info("Showdown websocket connected")
        }

        override fun onClose(webSocket: WebSocket?, statusCode: Int, reason: String?): CompletionStage<*>? {
            Cobblemon.LOGGER.info("Showdown websocket closed")
            return null
        }

        override fun onText(webSocket: WebSocket?, data: CharSequence?, last: Boolean): CompletionStage<*>? {
            client.messageQueue.addAll(data.toString().split("\n"))
            if (!last) {
                webSocket?.request(1L)
            }
            return null
        }

        override fun onBinary(webSocket: WebSocket?, data: ByteBuffer?, last: Boolean): CompletionStage<*>? {
            Cobblemon.LOGGER.info("Received binary message from Showdown server")
            return null
        }

        override fun onError(webSocket: WebSocket?, error: Throwable?) {
            Cobblemon.LOGGER.error("Showdown websocket error", error)
        }

        override fun onPing(webSocket: WebSocket?, message: ByteBuffer?): CompletionStage<*> {
            Cobblemon.LOGGER.info("Received showdown ping")
            return CompletableFuture.completedFuture(null)
        }

        override fun onPong(webSocket: WebSocket?, message: ByteBuffer?): CompletionStage<*> {
            Cobblemon.LOGGER.info("Received showdown pong")
            return CompletableFuture.completedFuture(null)
        }
    }
}