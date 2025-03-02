package com.cobblemon.mod.common.showdown.runner.socket

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.scheduling.ScheduledTask
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.showdown.runner.CobblemonShowdownBattleServer
import com.cobblemon.mod.common.showdown.runner.ShowdownBattle
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.Charset

class SocketCobblemonShowdownBattleServer(
    val host: String = "127.0.0.1",
    val port: Int = 18468
) : CobblemonShowdownBattleServer {
    private lateinit var socket: Socket
    private lateinit var writer: OutputStreamWriter
    private lateinit var reader: BufferedReader
    private val gson = GsonBuilder().create()

    override fun registerSpecies() {
        writer.write(">resetSpeciesData\n")
        acknowledge()
        PokemonSpecies.species.forEach { species ->
            sendSpeciesData(species, null)
            species.forms.forEach { form ->
                if (form != species.standardForm) {
                    sendSpeciesData(species, form)
                }
            }
        }
    }

    override fun connect() {
        socket = Socket(InetAddress.getLocalHost(), port, InetAddress.getLocalHost(), 0)
        writer = socket.getOutputStream().writer(charset = Charset.forName("ascii"))
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }

    override fun disconnect() {
        socket.close()
    }

    override fun initializeNewBattle(battleId: String, messages: Array<String>): ShowdownBattle {
        writer.write(">startbattle ${battleId}\n")
        acknowledge { Cobblemon.LOGGER.error("Failed to start battle!") }
        messages.forEach {
            sendServerMessage("$battleId~$it")
        }
        return SocketShowdownBattle(battleId, this)
    }

    override fun sendServerMessage(msg: String) {
        writer.write("$msg\n")
        writer.flush()
    }

    override fun getAbilityIds(): Collection<String> {
        writer.write(">getCobbledAbilityIds")
        writer.flush()
        val response = readMessage(true)
        return gson.fromJson(response, JsonArray::class.java).map { it.asString }
    }

    override fun getMoves(): Collection<MoveTemplate> {
        writer.write(">getCobbledMoves\n")
        writer.flush()
        val response = readMessage(true)
        return gson.fromJson(response, JsonArray::class.java).map { MoveTemplate.decodeJsonMove(it.asJsonObject)}
    }

    override fun getItemIds(): Collection<String> {
        writer.write(">getCobbledItemIds")
        writer.flush()
        val response = readMessage(true)
        return gson.fromJson(response, JsonArray::class.java).map { it.asString }
    }

    override fun onServerStarted() {
        val pollSocketTask = ScheduledTask.Builder().interval(1/4F)
            .infiniteIterations()
            .execute {
                val message = readMessage(false)
                if (message != null) {
                    val args = message.split("~")
                    ShowdownInterpreter.interpretMessage(args[0], args[1])
                }
            }
            .build()
        ServerTaskTracker.addTask(pollSocketTask)
    }

    private fun acknowledge(ifFails: () -> Unit = {}) {
        writer.flush()
        val ack = read(reader, 3)
        if (ack != "ACK") {
            ifFails()
        }
    }

    private fun readMessage(block: Boolean): String? {
        if (!reader.ready() && !block) {
            return null
        }
        val payloadSize = read(reader, 8).toInt()
        if (payloadSize == 0) {
            return null
        }
        val payload = read(reader, payloadSize)
        println(payload)
        return payload
    }

    private fun read(reader: BufferedReader, size: Int): String {
        val buffer = CharArray(size)
        while (true) {
            if(reader.read(buffer) == 0) continue
            return String(buffer)
        }
    }

    private fun sendSpeciesData(species: Species, form: FormData?) {
        writer.write(">receiveSpeciesData ${gson.toJson(PokemonSpecies.ShowdownSpecies(species, form))}\n")
        acknowledge()
    }
}