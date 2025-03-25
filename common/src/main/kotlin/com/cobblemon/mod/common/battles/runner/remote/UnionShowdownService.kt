package com.cobblemon.mod.common.battles.runner.remote

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.runner.ShowdownStatus
import com.google.gson.JsonArray
import net.minecraft.resources.ResourceLocation
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletionStage

/**
 * Implementation of the ShowdownService interface for use with the Union Block.
 *
 * This wraps around a WebSocket connection to a remote showdown service.
 *
 * @property id The unique identifier for this showdown service.
 * @property host The host address of the showdown service.
 * @property socketPort The port for socket communication with the showdown service.
 * @author landonjw
 */
class UnionShowdownService(
    override val id: ResourceLocation,
    val host: String = "localhost",
    val socketPort: Int = 8001
) : ShowdownService {

    override var status: ShowdownStatus = ShowdownStatus.DISCONNECTED
        private set

    private val _log = mutableListOf<String>()
    override val log: List<String>?
        get() {
            if (!Cobblemon.config.enableDebugKeys) return null
            return _log
        }

    private lateinit var webSocket: WebSocket

    /**
     * Opens a connection to the showdown service.
     */
    override fun openConnection() = openConnection(false)

    /**
     * Opens a connection to the showdown service, with an option to suppress logging.
     *
     * @param silent If true, suppresses logging of the connection process.
     */
    private fun openConnection(silent: Boolean) {
        if (!silent) {
            this._log.clear()
            addToShowdownLog(source = "Minecraft", message = "Opening showdown connection")
        }
        webSocket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create("ws://$host:$socketPort"), WebSocketListener(this))
            .join()
        if (!silent) {
            this.status = ShowdownStatus.CONNECTED
        }
    }

    /**
     * Closes the connection to the showdown service.
     */
    override fun closeConnection() = closeConnection(false)

    /**
     * Closes the connection to the showdown service, with an option to suppress logging.
     *
     * @param silent If true, suppresses logging of the disconnection process.
     */
    private fun closeConnection(silent: Boolean) {
        if (!silent) {
            addToShowdownLog(source = "Minecraft", message = "Closing showdown connection")
        }
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye!").join()
        if (!silent) {
            this.status = ShowdownStatus.DISCONNECTED
        }
    }

    /**
     * Restarts the connection to the showdown service.
     */
    override fun restartConnection() {
        this.status = ShowdownStatus.RESTARTING
        try {
            closeConnection(true)
            openConnection(true)
            this.status = ShowdownStatus.CONNECTED
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to restart showdown connection", e)
            this.status = ShowdownStatus.DISCONNECTED
        }
    }

    /**
     * Starts a battle in the showdown service.
     *
     * @param battle The battle to start.
     * @param messages The initial messages for the battle.
     */
    override fun startBattle(battle: PokemonBattle, messages: Array<String>) {
        send(">start-battle ${battle.battleId}")
        send(battle.battleId, messages)
    }

    /**
     * Sends messages to a specific battle in the showdown service.
     *
     * @param battleId The unique identifier of the battle.
     * @param messages The messages to send.
     */
    override fun send(battleId: UUID, messages: Array<String>) {
        messages.forEach { send("[$battleId]$it") }
    }

    /**
     * Sends a message to the showdown service.
     *
     * @param message The message to send.
     */
    override fun send(message: String) {
        webSocket.sendText(message, true)
    }

    /**
     * Retrieves the ability IDs from the showdown service.
     *
     * @return A `JsonArray` of ability IDs.
     */
    override fun getAbilityIds(): JsonArray {
        TODO("Not yet implemented")
    }

    /**
     * Retrieves the moves from the showdown service.
     *
     * @return A `JsonArray` of moves.
     */
    override fun getMoves(): JsonArray {
        TODO("Not yet implemented")
    }

    /**
     * Retrieves the item IDs from the showdown service.
     *
     * @return A `JsonArray` of item IDs.
     */
    override fun getItemIds(): JsonArray {
        TODO("Not yet implemented")
    }

    /**
     * Registers species in the showdown service.
     */
    override fun registerSpecies() {
        TODO("Not yet implemented")
    }

    /**
     * Registers bag items in the showdown service.
     */
    override fun registerBagItems() {
        TODO("Not yet implemented")
    }

    /**
     * Adds a message to the showdown log.
     *
     * @param source The source of the message.
     * @param message The message to add.
     */
    internal fun addToShowdownLog(source: String, message: String) {
        if (!Cobblemon.config.enableDebugKeys) return
        val timestamp = SimpleDateFormat("HH:mm:ss").format(Date())
        if (_log.size >= 150) {
            _log.removeFirst()
        }
        _log.add("[$source][${timestamp}]> $message")
    }

    /**
     * WebSocket listener for handling events from the showdown service.
     *
     * @property service The UnionShowdownService instance.
     */
    class WebSocketListener(val service: UnionShowdownService) : WebSocket.Listener {
        override fun onOpen(webSocket: WebSocket) {
            service.addToShowdownLog(source = "Showdown", message = "Connected to server at ${service.host}:${service.socketPort}")
            super.onOpen(webSocket)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            service.addToShowdownLog(source = "Showdown", message = "$data")
            return super.onText(webSocket, data, last)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            service.addToShowdownLog(source = "Showdown", message = "Error: ${error.message}")
            Cobblemon.LOGGER.error("Error in showdown service ${service.id} websocket", error)
            super.onError(webSocket, error)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            service.addToShowdownLog(source = "Showdown", message = "Closed connection with status $statusCode and reason $reason")
            return super.onClose(webSocket, statusCode, reason)
        }
    }

}
