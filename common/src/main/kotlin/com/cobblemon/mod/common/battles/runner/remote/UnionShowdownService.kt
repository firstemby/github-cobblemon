package com.cobblemon.mod.common.battles.runner.remote

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.UnionBattleActor
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.ShowdownDebugLog
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
import net.minecraft.world.entity.schedule.Activity
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.*
import java.util.concurrent.CompletionStage

/**
 * Implementation of the ShowdownService interface for use with the Union Block.
 *
 * This wraps around a WebSocket connection to a remote showdown service.
 *
 * @property id The unique identifier for this showdown service.
 * @property host The host address of the showdown service.
 * @property httpPort The port for HTTP communication with the showdown service.
 * @property socketPort The port for WebSocket communication with the showdown service.
 *
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

    override val log = ShowdownDebugLog()

    private lateinit var webSocket: WebSocket

    /**
     * Opens a connection to the showdown service.
     */
    override fun openConnection() = openConnection(false)

    /**
     * Opens a connection to the showdown service.
     *
     * @param silent If true, suppresses changing the status of the connection process.
     */
    private fun openConnection(silent: Boolean) {
        log.clear()
        log.add(source = "Minecraft", message = "Opening showdown connection")
        try {
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
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("Failed to open showdown connection to $host:$socketPort", e)
            log.add(source = "Minecraft", message = "Failed to open showdown connection with error: ${e.message}")
            if (!silent) {
                this.status = ShowdownStatus.DISCONNECTED
            }
        }
    }

    /**
     * Closes the connection to the showdown service.
     */
    override fun closeConnection() = closeConnection(false)

    /**
     * Closes the connection to the showdown service.
     *
     * @param silent If true, suppresses changing the status of the disconnection process.
     */
    private fun closeConnection(silent: Boolean) {
        log.add(source = "Minecraft", message = "Closing showdown connection")
        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye!").join()
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("Failed to close showdown connection to $host:$socketPort", e)
            log.add(source = "Minecraft", message = "Failed to close showdown connection to $host:$socketPort with error: ${e.message}")
        }
        finally {
            if (!silent) {
                this.status = ShowdownStatus.DISCONNECTED
            }
        }
    }

    /**
     * Restarts the connection to the showdown service.
     */
    override fun restartConnection() {
        this.status = ShowdownStatus.RESTARTING
        closeConnection(true)
        openConnection(true)
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
     * WebSocket listener for handling events from the showdown service.
     *
     * @property service The UnionShowdownService instance.
     */
    class WebSocketListener(val service: UnionShowdownService) : WebSocket.Listener {
        val builder = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            service.log.add(source = "Showdown", message = "Connected to server at ${service.host}:${service.socketPort}")
            super.onOpen(webSocket)
        }

        /**
         * Starts a battle based on the received instruction.
         *
         * @param instruction The instruction to start the battle.
         */
        fun startBattle(instruction: String) {
            val arguments = instruction.replace("|start-battle|", "").split("|")
            if (arguments.size != 7) return
            val battleId = UUID.fromString(arguments[0])
            val playerIdA = UUID.fromString(arguments[1])
            val playerNameA = arguments[2]
            val playerIdB = UUID.fromString(arguments[3])
            val playerNameB = arguments[4]
            val teamA = JsonParser.parseString(arguments[5]).asJsonObject
            val teamB = JsonParser.parseString(arguments[6]).asJsonObject
            val playerA = server()?.playerList?.getPlayer(playerIdA)
            val playerB = server()?.playerList?.getPlayer(playerIdB)

            println(arguments[5])
            println(arguments[6])

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
                npc.moveTo(playerA.x + 5, playerA.y, playerA.z, playerA.yRot, (playerA.xRot + 180f) % 360f)
                npc.npc = NPCClasses.getByIdentifier(cobblemonResource("standard"))!!
                npc.loadTextureFromGameProfileName(playerNameB)
                npc.initialize(1)
                npc.config.setDirectly("yaw", DoubleValue((playerA.xRot + 90f) % 360f))
                npc.uuid = playerIdB
                npc.customName = playerNameB.text()
                npc.isInvulnerable = true
                npc.isMovable = false
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
                npc.moveTo(playerB.x + 5, playerB.y, playerB.z, playerB.yRot, (playerB.xRot + 180f) % 360f)
                npc.npc = NPCClasses.getByIdentifier(cobblemonResource("standard"))!!
                npc.loadTextureFromGameProfileName(playerNameA)
                npc.initialize(1)
                npc.uuid = playerIdA
                npc.customName = playerNameA.text()
                npc.isInvulnerable = true
                npc.isMovable = false
                npc.config.setDirectly("yaw", DoubleValue((playerB.xRot + 90f) % 360f))
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

        /**
         * Converts a list of Pokemon to a list of BattlePokemon for the showdown service.
         *
         * @param team The list of Pokemon to convert.
         * @return A list of BattlePokemon.
         */
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
                service.log.add(source = "Showdown", message = message)

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
            service.log.add(source = "Showdown", message = "Error: ${error.message}")
            Cobblemon.LOGGER.warn("Error in showdown service ${service.id} websocket", error)
            super.onError(webSocket, error)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            service.log.add(source = "Showdown", message = "Closed connection with status $statusCode and reason $reason")
            return super.onClose(webSocket, statusCode, reason)
        }
    }

    /**
     * Data class representing a bag item. We use this for serialization to the showdown service.
     *
     * @property id The unique identifier of the bag item.
     * @property script The script associated with the bag item.
     */
    private data class BagItem(val id: String, val script: String)

}
