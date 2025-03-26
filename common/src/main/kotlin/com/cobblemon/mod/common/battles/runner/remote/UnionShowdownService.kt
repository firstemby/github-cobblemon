package com.cobblemon.mod.common.battles.runner.remote

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.UnionBattleActor
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.runner.ShowdownStatus
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.HttpService
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.server
import com.google.gson.JsonArray
import com.google.gson.JsonParser
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
    val httpPort: Int = 8003,
    val socketPort: Int = 8002
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
        // TODO: See if this is needed
        registerSpecies()
        indicateSpeciesInitialized()
        registerBagItems()
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
        send("[${battle.battleId}]>start-battle")
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
        HttpService.post<Unit>(
            url = "http://$host:$httpPort/Species",
            body = PokemonSpecies.species.map { PokemonSpecies.ShowdownSpecies(it, null) }
        )
    }

    /**
     * Registers bag items in the showdown service.
     */
    override fun registerBagItems() {
        HttpService.post<Unit>(
            url = "http://$host:$httpPort/BagItems",
            body = BagItems.bagItemsScripts.map { BagItem(it.key, it.value) }.toList()
        )
    }

    override fun indicateSpeciesInitialized() {
        HttpService.post<Unit>(url = "http://$host:$httpPort/InitializeDex")
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
        val builder = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            service.addToShowdownLog(source = "Showdown", message = "Connected to server at ${service.host}:${service.socketPort}")
            super.onOpen(webSocket)
        }

        fun startBattle(instruction: String) {
            val arguments = instruction.replace("|start-battle|", "").split("|")
            if (arguments.size != 5) return
            val battleId = UUID.fromString(arguments[0])
            val playerIdA = UUID.fromString(arguments[1])
            val playerIdB = UUID.fromString(arguments[2])
            val teamA = JsonParser.parseString(arguments[3]).asJsonObject
            val teamB = JsonParser.parseString(arguments[4]).asJsonObject

            val playerA = server()?.playerList?.getPlayer(playerIdA)
            val playerB = server()?.playerList?.getPlayer(playerIdB)

            if (playerA == null && playerB == null) {
                Cobblemon.LOGGER.warn("Tried to start battle with no players present on server")
                return
            }

            val registryAccess = server()?.registryAccess() ?: return

            val playerAParty = PartyStore(UUID.randomUUID()).loadFromJSON(teamA, registryAccess)
            val playerBParty = PartyStore(UUID.randomUUID()).loadFromJSON(teamB, registryAccess)
            val playerATeam = toShowdownBattleTeam(playerAParty.toList()).onEach {
                it.effectedPokemon.level = 100
                it.effectedPokemon.heal()
            }
            val playerBTeam = toShowdownBattleTeam(playerBParty.toList()).onEach {
                it.effectedPokemon.level = 100
                it.effectedPokemon.heal()
            }

            if (playerA != null && playerB != null) {
                val playerActorA = PlayerBattleActor(playerIdA, playerATeam)
                val playerActorB = PlayerBattleActor(playerIdB, playerBTeam)
                BattleRegistry.startBattle(
                    battleId = battleId,
                    battleFormat = BattleFormat.GEN_9_SINGLES,
                    side1 = BattleSide(playerActorA),
                    side2 = BattleSide(playerActorB),
                    sendMessages = false,
                    service = service
                )
            }
            else if (playerA != null && playerB == null) {
                val playerActorA = PlayerBattleActor(playerIdA, playerATeam)
                val npc = NPCEntity(playerA.level())
                npc.moveTo(playerA.x + 5, playerA.y, playerA.z, playerA.yRot, playerA.xRot)
                npc.npc = NPCClasses.getByIdentifier(cobblemonResource("ai_test"))!!
                npc.initialize(1)
                npc.uuid = playerIdB
                playerA.level().addFreshEntity(npc)
                val playerActorB = UnionBattleActor(npc, playerBTeam)
                BattleRegistry.startBattle(
                    battleId = battleId,
                    battleFormat = BattleFormat.GEN_9_SINGLES,
                    side1 = BattleSide(playerActorA),
                    side2 = BattleSide(playerActorB),
                    sendMessages = false,
                    service = service
                )
            }
            else {
                val npc = NPCEntity(playerB!!.level())
                npc.moveTo(playerB.x + 5, playerB.y, playerB.z, playerB.yRot, playerB.xRot)
                npc.npc = NPCClasses.getByIdentifier(cobblemonResource("ai_test"))!!
                npc.initialize(1)
                npc.uuid = playerIdA
                playerB.level().addFreshEntity(npc)
                val playerActorA = UnionBattleActor(npc, playerATeam)
                val playerActorB = PlayerBattleActor(playerIdB, playerBTeam)
                BattleRegistry.startBattle(
                    battleId = battleId,
                    battleFormat = BattleFormat.GEN_9_SINGLES,
                    side1 = BattleSide(playerActorA),
                    side2 = BattleSide(playerActorB),
                    sendMessages = false,
                    service = service
                )
            }
        }

        fun toShowdownBattleTeam(team: List<Pokemon>): List<BattlePokemon> {
            return team.map { pokemon ->
                BattlePokemon(
                    originalPokemon = pokemon,
                    effectedPokemon = pokemon.clone(newUUID = false),
                    postBattleEntityOperation = { entity -> entity.discard() }
                )
            }
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            if (last) {
                builder.append(data)
                val message = builder.toString()
                service.addToShowdownLog(source = "Showdown", message = message)

                if (data.startsWith("|start-battle|")) {
                    startBattle(data.toString())
                }
                else {
                    val battleIdRegex = Regex("^\\[([^]]+)]")
                    val match = battleIdRegex.find(message) ?: return null

                    val battleId = match.groupValues[1]
                    val instruction = message.replace("[$battleId]", "")
                    ShowdownInterpreter.interpretMessage(UUID.fromString(battleId), instruction)
                }

                builder.clear()
            }
            else {
                builder.append(data)
            }
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

    private data class BagItem(val id: String, val script: String)

}
