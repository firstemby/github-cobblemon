/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner.socket

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.stadium.StadiumShowdownProtocol
import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.text.replace

/**
 * Mediator service for communicating between the Cobblemon Minecraft mod and Cobblemon showdown service via
 * a socket client.
 *
 * This is primarily used for debugging purposes, but could be extended in the future to provide
 * a means of connecting to a remote Showdown server. This does not provide any fault handling in the
 * event that the server goes down.
 *
 * When messages are sent to showdown, this will await a response from showdown.
 * The protocol for messages sent from showdown is <length of characters in payload><payload>,
 * and a payload length of 0 indicates that there is no response.
 *
 * @see {@code sim/cobbled-debug-server.ts} within cobblemon-showdown repository
 * @since February 27, 2023
 * @author landonjw
 */
class SocketShowdownService(val host: String = "localhost", val port: Int = 18468, val localPort: Int = 0) : ShowdownService {

    private lateinit var socket: Socket
    private lateinit var writer: OutputStreamWriter
    private lateinit var reader: ShowdownSocketReader
    private val stadiumBattles = mutableSetOf<UUID>()
    private var stadiumCapabilities: StadiumShowdownProtocol.Capabilities? = null
    val gson = Gson()

    @Synchronized override fun openConnection() {
        socket = Socket()
        try {
            socket.bind(InetSocketAddress(localPort))
            socket.connect(InetSocketAddress(host, port), 10000)
            socket.soTimeout = 30000
            writer = socket.getOutputStream().writer(Charsets.UTF_8)
            reader = ShowdownSocketReader(socket.getInputStream())
            stadiumCapabilities = null
        } catch (exception: Exception) {
            socket.close()
            throw exception
        }
    }

    @Synchronized override fun closeConnection() {
        try { if (this::socket.isInitialized) socket.close() } finally { failStadiumBattles() }
    }

    @Synchronized override fun startBattle(battle: PokemonBattle, messages: Array<String>) {
        writer.write(">startbattle ${battle.battleId}\n")
        acknowledge { Cobblemon.LOGGER.error("Failed to start battle!") }
        send(battle.battleId, messages)
    }

    @Synchronized override fun endBattle(battle: PokemonBattle) {
        if (socket.isClosed) return
        if (stadiumBattles.remove(battle.battleId)) {
            stadiumJson(">endStadiumBattle ${battle.battleId}")
            return
        }
        writer.write(">endbattle ${battle.battleId}\n")
        acknowledge { Cobblemon.LOGGER.error("Failed to end battle ${battle.battleId}!") }
    }

    @Synchronized override fun send(battleId: UUID, messages: Array<String>) {
        try {
            for (message in messages) {
                writer.write("$battleId~$message\n")
                writer.flush()
                readBattleInput().forEach { interpretMessage(battleId, it) }
            }
        } catch (exception: Exception) {
            if (battleId !in stadiumBattles) throw exception
            socket.close()
            failStadiumBattles()
        }
    }

    private fun readMessage() = reader.message()
    private fun readBattleInput() = reader.messages()

    private fun interpretMessage(battleId: UUID, message: String) {
        ShowdownService.stadiumOutput.receive(battleId, message)
    }

    private fun stadiumJson(command: String): String {
        require('\n' !in command && '\r' !in command)
        writer.write("$command\n")
        writer.flush()
        return reader.json()
    }

    @Synchronized override fun getStadiumCapabilities(): StadiumShowdownProtocol.Capabilities {
        stadiumCapabilities?.let { return it }
        val capabilities = runCatching { StadiumShowdownProtocol.capabilities(stadiumJson(">getStadiumCapabilities")) }
            .getOrElse {
                closeConnection()
                openConnection()
                StadiumShowdownProtocol.Capabilities()
            }
        stadiumCapabilities = capabilities
        return capabilities
    }

    @Synchronized override fun validateStadiumBattle(request: String) = try {
        StadiumShowdownProtocol.result(stadiumJson(">validateStadiumBattle $request"))
    } catch (exception: Exception) {
        closeConnection()
        throw exception
    }

    @Synchronized override fun startStadiumBattle(battle: PokemonBattle, request: String): StadiumShowdownProtocol.Result {
        try {
            val result = StadiumShowdownProtocol.result(stadiumJson(">startStadiumBattle ${battle.battleId} $request"))
            if (result.ok) {
                stadiumBattles.add(battle.battleId)
                writer.write(">readStadiumBattle ${battle.battleId}\n")
                writer.flush()
                readBattleInput().forEach { interpretMessage(battle.battleId, it) }
            }
            return result
        } catch (exception: Exception) {
            closeConnection()
            throw exception
        }
    }

    private fun failStadiumBattles() {
        val ids = stadiumBattles.toList()
        stadiumBattles.clear()
        ids.forEach { interpretMessage(it, "stadiumerror\n{}") }
    }

    @Synchronized override fun sendStadiumChoice(battleId: UUID, request: String) = stadiumAction("sendStadiumChoice", battleId, request)
    @Synchronized override fun resolveStadiumTimeout(battleId: UUID, request: String) = stadiumAction("resolveStadiumTimeout", battleId, request)

    private fun stadiumAction(command: String, battleId: UUID, request: String): StadiumShowdownProtocol.Result {
        try {
            val result = StadiumShowdownProtocol.result(stadiumJson(">$command $battleId $request"))
            writer.write(">readStadiumBattle $battleId\n")
            writer.flush()
            readBattleInput().forEach { interpretMessage(battleId, it) }
            return result
        } catch (exception: Exception) {
            closeConnection()
            throw exception
        }
    }

    @Synchronized override fun sendRegistryData(data: Map<String, String>, type: String) {
        data.forEach { (key, value) -> sendRegistryEntry(key, value, type) }
        // The code for sending bulk data is commented out below, because:
        // 1) while debugging it's useful to have individual entries, and socket is only used for debug atm
        // 2) the species JSONs are way too big to be handled as one line on the JS side (maybe both sides?)
        // this can eventually be remedied if socket is intended to be used for any actual servers/etc.
        // alternatively, you could get around this limitation by just having ONLY PokemonSpecies send individually,
        // but it's likely that with enough custom data for moves/abilities/etc. you'd run into the same issue

        /*val payload = data.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            val newV = v.replace(Regex("[\r\n]+"), " ")
            "\"$k\": $newV"
        }
        writer.write(">receiveData $type payload")
        acknowledge { Cobblemon.LOGGER.error("Failed to send $type data to Showdown: $data") }*/
    }

    @Synchronized fun sendRegistryEntry(key: String, data: String, type: String) {
        val payload = data.replace(Regex("[\r\n]+"), " ")
        writer.write(">receiveEntry $type $key $payload")
        acknowledge { Cobblemon.LOGGER.error("Failed to send $type data to Showdown: $payload") }
    }

    @Synchronized override fun sendRegistryEntry(data: String, type: String) {
        val payload = data.replace(Regex("[\r\n]+"), " ")
        writer.write(">receiveEntry $type $payload")
        acknowledge { Cobblemon.LOGGER.error("Failed to send $type data to Showdown: $payload") }
    }

    @Synchronized override fun getRegistryData(type: String): JsonArray {
        writer.write(">getData $type")
        writer.flush()
        val response = readMessage()
        return gson.fromJson(response, JsonArray::class.java)
    }

    @Synchronized override fun resetRegistryData(type: String) {
        writer.write(">resetData $type")
        acknowledge()
    }

    @Synchronized override fun resetAllRegistries() {
        writer.write(">resetAll")
        acknowledge()
    }

    @Synchronized fun acknowledge(ifFails: () -> Unit = {}) {
        writer.flush()
        if (reader.characters(3) != "ACK") {
            ifFails()
        }
    }

    @Synchronized override fun indicateSpeciesInitialized() {
        writer.write(">afterSpeciesInit")
        acknowledge()
    }

}
