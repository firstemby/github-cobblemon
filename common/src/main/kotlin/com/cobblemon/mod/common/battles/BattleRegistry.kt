/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.moves.HiddenPowerUtil
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.battles.stadium.StadiumBattleSession
import com.cobblemon.mod.common.battles.stadium.StadiumShowdownProtocol
import com.cobblemon.mod.common.battles.stadium.StadiumSpectating
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.update
import com.google.gson.GsonBuilder
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.util.lang
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object BattleRegistry {

    val gson = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(ShowdownMoveset::class.java, ShowdownMovesetAdapter)
        .create()
    private val battleMap = ConcurrentHashMap<UUID, PokemonBattle>()
    private val stadiumFailures = ConcurrentHashMap<UUID, () -> Unit>()

    fun onServerStarted() {
        StadiumSpectating.clear()
        battleMap.clear()
        stadiumFailures.clear()
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        StadiumSpectating.disconnect(player)
        battleMap.values.forEach { it.spectators.remove(player.uuid) }
        // Stop battles
        getBattleByParticipatingPlayer(player)?.stop()
    }

    /**
     * Packs a team into the showdown format
     *
     * Example from https://gitlab.com/cable-mc/cobblemon-showdown/-/blob/master/sim/TEAMS.md#packed-format
     *  NICKNAME|SPECIES|ITEM|ABILITY|MOVES|NATURE|EVS|GENDER|IVS|SHINY|LEVEL|HAPPINESS,POKEBALL,HIDDENPOWERTYPE,GIGANTAMAX,DYNAMAXLEVEL,TERATYPE
     *
     * @return a string of the packed team
     */
    fun List<BattlePokemon>.packTeam(): String {
        val team = mutableListOf<String>()
        for (pokemon in this) {
            val pk = pokemon.effectedPokemon
            val packedTeamBuilder = StringBuilder()
            // If no nickname, write species first and leave next blank
            packedTeamBuilder.append("${pk.showdownId()}|")
            // Species, left empty if no nickname
            packedTeamBuilder.append("|")

            // REQUIRES OUR SHOWDOWN
            packedTeamBuilder.append("${pk.uuid}|")
            packedTeamBuilder.append("${pk.currentHealth}|")
            val showdownStatus = if (pk.status != null) pk.status!!.status.showdownName else ""
            packedTeamBuilder.append("$showdownStatus|")
            // If a temporary status is on the Pokémon, provide a duration.
            if (pk.status?.status in listOf(Statuses.SLEEP, Statuses.FROZEN)) {
                packedTeamBuilder.append("2|")
            } else {
                packedTeamBuilder.append("-1|")
            }

            // Held item, empty if none
            val heldItemID = HeldItemProvider.provideShowdownId(pokemon) ?: ""
            packedTeamBuilder.append("$heldItemID|")
            // Ability, our showdown has edits here to trust whatever we tell it, this was needed to support more than 4 abilities.
            packedTeamBuilder.append("${pk.ability.name.replace("_", "")}|")
            // Moves
            packedTeamBuilder.append(
                "${
                    pk.moveSet.getMoves().joinToString(",") { move -> move.name.replace("_", "") }
                }|"
            )
            // Additional move info
            packedTeamBuilder.append(
                "${
                    pk.moveSet.getMoves()
                        .joinToString(",") { move -> move.currentPp.toString() + "/" + move.maxPp.toString() }
                }|"
            )
            // Nature
            val battleNature = pk.effectiveNature
            packedTeamBuilder.append("${battleNature.name.path}|")
            // EVs
            val evsInOrder = Stats.PERMANENT.map { pk.evs.getOrDefault(it) }.joinToString(separator = ",")
            packedTeamBuilder.append("$evsInOrder|")
            // Gender
            packedTeamBuilder.append("${pk.gender.showdownName}|")
            // IVs
            val ivsInOrder = Stats.PERMANENT.map { pk.ivs.getEffectiveBattleIV(it) }.joinToString(separator = ",")
            packedTeamBuilder.append("$ivsInOrder|")
            // Shiny
            packedTeamBuilder.append("${if (pk.shiny) "S" else ""}|")
            // Level
            packedTeamBuilder.append("${pk.level}|")

            // Misc
            // Happiness
            packedTeamBuilder.append("${pk.friendship},")
            // Caught Ball
            // This is safe to do as all our pokeballs that have showdown item equivalents are the same IDs they use for the pokeball attribute
            val pokeball = pokemon.effectedPokemon.caughtBall.name.path.replace("_", "")
            packedTeamBuilder.append("$pokeball,")
            // Hidden Power Type
            // This is empty if the pokemon is not Hyper Trained, and the provided type corresponds to base IVs and not battle IVs.
            val hiddenPowerType = if (pk.ivs.hyperTrainedIVs.any()) HiddenPowerUtil.getHiddenPowerType(pk).name else ""
            packedTeamBuilder.append("$hiddenPowerType,")
            // Gigantamax
            packedTeamBuilder.append("${if (pk.gmaxFactor) "G" else ""},")
            // DynamaxLevel
            // 0 - 9, empty == 10
            packedTeamBuilder.append("${if (pk.dmaxLevel < 10) pk.dmaxLevel else ""},")
            // Teratype
            packedTeamBuilder.append("${pokemon.effectedPokemon.teraType.name},")

            team.add(packedTeamBuilder.toString())
        }
        return team.joinToString("]")
    }

    private fun startShowdown(battle: PokemonBattle) {

        // Build request message
        val messages = mutableListOf<String>()
        messages.add(">start { \"format\": ${battle.format.toFormatJSON()} }")

        /*
         * Showdown IDs are like p1, p2, p3, etc. Showdown uses these keys to identify who is doing what to whom.
         *
         * "But why are these showdown IDs so weird"
         *
         * I'll tell you, Jimmy.
         * https://gitlab.com/cable-mc/cobblemon-showdown/-/blob/master/sim/SIM-PROTOCOL.md#user-content-identifying-pok%C3%A9mon
         *
         * See the lines about multi battles and free for alls. The same side of the battle will share 'parity' (even or odd) across
         * all participants. So side 1 will be 1, 3, 5, ... while side 2 will be 2, 4, 6, ...
         *
         * That isn't how our code works, as we have the BattleSide thing, but it's how Showdown works, so we need to play along a bit.
         */

        var actorIndex = 1
        for (actor in battle.side1.actors) {
            actor.showdownId = "p$actorIndex"
            actorIndex += 2
        }

        actorIndex = 2
        for (actor in battle.side2.actors) {
            actor.showdownId = "p$actorIndex"
            actorIndex += 2
        }

        for (actor in battle.actors) {
            repeat(battle.format.battleType.slotsPerActor) {
                actor.activePokemon.add(ActiveBattlePokemon(actor))
            }
            val entities = actor.pokemonList.mapNotNull { it.entity }
            entities.forEach { it.battleId = battle.battleId }
        }

        // -> Add the players and team
        for (actor in battle.actors.sortedBy { it.showdownId }) {
            messages.add(""">player ${actor.showdownId} {"name":"${actor.uuid}","team":"${actor.pokemonList.packTeam()}"}""")
        }

        // -> Set team size
        for (actor in battle.actors.sortedBy { it.showdownId }) {
            messages.add(">${actor.showdownId} team ${actor.pokemonList.count()}")
        }

        // Compiles the request and sends it off
        ShowdownService.service.startBattle(battle, messages.toTypedArray())
    }

    /**
     * Instantiates a new [PokemonBattle] and launches the Showdown service.
     *
     * @param battleFormat The format to use for the battle.
     * @param side1 The first side for the battle.
     * @param side2 The second side for the battle.
     * @param canPreempt Whether the battle can be blocked by subscribers to [CobblemonEvents.BATTLE_STARTED_PRE].
     *
     * NOTE: this does NOT validate the [battleFormat] or sides before launching Showdown.
     * This creates the battle and hands it off to Showdown - YOU WILL GET ISSUES IF THE BATTLE IS INVALID!
     * See [BattleBuilder] functions for how the various types of battles are built and validated.
     */
    @JvmStatic
    fun startBattle(
        battleFormat: BattleFormat,
        side1: BattleSide,
        side2: BattleSide,
        canPreempt: Boolean = true
    ): BattleStartResult {
        val battle = PokemonBattle(battleFormat, side1, side2)
        val start: () -> Unit = {
            battleMap[battle.battleId] = battle
            startShowdown(battle)
        }

        if (!canPreempt) start().also { return SuccessfulBattleStart(battle) }

        val preBattleEvent = BattleStartedEvent.Pre(battle)
        CobblemonEvents.BATTLE_STARTED_PRE.postThen(preBattleEvent) {
            start()
            CobblemonEvents.BATTLE_STARTED_POST.post(BattleStartedEvent.Post(battle))
            return SuccessfulBattleStart(battle)
        }
        return ErroredBattleStart(mutableSetOf(BattleStartError.canceledByEvent(preBattleEvent.reason)))
    }

    fun stadiumRequest(config: StadiumFormatConfig, side1: BattleSide, side2: BattleSide): String {
        require(side1.actors.size == 1 && side2.actors.size == 1)
        return StadiumShowdownProtocol.request(config, listOf(side1.actors.single(), side2.actors.single()).mapIndexed { index, actor ->
            StadiumShowdownProtocol.Side("p${index + 1}", actor.uuid.toString(), actor.pokemonList.packTeam())
        })
    }

    fun validateStadium(config: StadiumFormatConfig, side1: BattleSide, side2: BattleSide): StadiumShowdownProtocol.Result = runCatching {
        StadiumShowdownProtocol.validate(ShowdownService.service, config, stadiumRequest(config, side1, side2))
    }.getOrElse { StadiumShowdownProtocol.failure("invalid_party", "Unable to prepare the stadium parties.") }

    fun startStadiumBattle(battleFormat: BattleFormat, side1: BattleSide, side2: BattleSide, config: StadiumFormatConfig,
        onFailure: () -> Unit): BattleStartResult {
        val validation = validateStadium(config, side1, side2)
        if (!validation.ok) return stadiumRejection(validation.userMessage)
        val battle = runCatching { PokemonBattle(battleFormat, side1, side2, initializeProgress = false) }
            .getOrElse { return stadiumRejection(lang("stadium.engine.initialize_failed")) }
        val preBattleEvent = BattleStartedEvent.Pre(battle)
        val gate = ShowdownService.stadiumOutput
        var submitted = false
        try {
            CobblemonEvents.BATTLE_STARTED_PRE.postThen(preBattleEvent) {
                gate.begin(battle.battleId)
                battle.actors.forEachIndexed { index, actor ->
                    actor.showdownId = "p${index + 1}"
                    repeat(battle.format.battleType.slotsPerActor) { actor.activePokemon.add(ActiveBattlePokemon(actor)) }
                }
                val request = stadiumRequest(config, side1, side2)
                submitted = true
                val result = ShowdownService.service.startStadiumBattle(battle, request)
                if (!result.ok) {
                    gate.discard(battle.battleId)
                    battle.actors.forEach { it.activePokemon.clear() }
                    battle.ended = true
                    return stadiumRejection(result.userMessage)
                }
                battleMap[battle.battleId] = battle
                stadiumFailures[battle.battleId] = onFailure
                battle.stadiumSession = StadiumBattleSession(battle, config)
                battle.resetEvolutionProgress()
                battle.actors.flatMap { it.pokemonList }.mapNotNull { it.entity }.forEach { it.battleId = battle.battleId }
                gate.commit(battle.battleId)
                if (battle.ended) return stadiumRejection(lang("stadium.engine.startup_stopped"))
                CobblemonEvents.BATTLE_STARTED_POST.post(BattleStartedEvent.Post(battle))
                return SuccessfulBattleStart(battle)
            }
        } catch (exception: Exception) {
            gate.discard(battle.battleId)
            if (battleMap[battle.battleId] === battle) abortStadiumBattle(battle.battleId)
            else {
                battle.ended = true
                battle.actors.forEach { it.activePokemon.clear() }
                if (submitted) runCatching { ShowdownService.service.endBattle(battle) }
            }
            Cobblemon.LOGGER.error("Stadium startup {} failed ({})", battle.battleId, exception.javaClass.simpleName)
            return stadiumRejection(lang("stadium.engine.startup_failed"))
        }
        battle.ended = true
        return ErroredBattleStart(mutableSetOf(BattleStartError.canceledByEvent(preBattleEvent.reason)))
    }

    private fun stadiumRejection(message: Component) = ErroredBattleStart(mutableSetOf(object : BattleStartError {
        override fun getMessageFor(entity: Entity) = message.copy()
    }))

    fun abortStadiumBattle(id: UUID) {
        val failure = stadiumFailures.remove(id) ?: return
        val battle = battleMap[id] ?: return
        battle.ended = true
        battle.dispatches.clear()
        battle.captureActions.clear()
        ShowdownInterpreter.lastCauser.remove(id)
        battle.actors.forEach { actor ->
            runCatching {
                actor.mustChoose = false
                actor.responses.clear()
                actor.expectingPassActions.clear()
                actor.request = null
                actor.pokemonList.forEach { pokemon ->
                    pokemon.entity?.takeIf { it.battleId == id }?.let { entity ->
                        entity.battleId = null
                        runCatching { entity.effects.wipe() }
                        runCatching { entity.recallWithAnimation() }
                    }
                }
                if (actor is NPCBattleActor) actor.npc.entityData.update(NPCEntity.BATTLE_IDS) { it - id }
                actor.activePokemon.clear()
            }.onFailure { Cobblemon.LOGGER.error("Stadium participant cleanup failed for {} ({})", id, it.javaClass.simpleName) }
        }
        runCatching { battle.sendUpdate(BattleEndPacket()) }
        try { closeBattle(battle) } finally { failure() }
    }

    @JvmStatic
    fun closeBattle(battle: PokemonBattle) {
        if (!battleMap.remove(battle.battleId, battle)) return
        try {
            battle.onEndHandlers.toList().forEach { handler ->
                runCatching { handler(battle) }.onFailure { Cobblemon.LOGGER.error("Battle end handler failed for {}", battle.battleId, it) }
            }
        } finally {
            runCatching { battle.stadiumSession?.close() }.onFailure {
                Cobblemon.LOGGER.error("Could not close stadium session {}", battle.battleId, it)
            }
            stadiumFailures.remove(battle.battleId)
            ShowdownService.stadiumOutput.discard(battle.battleId)
            runCatching { ShowdownService.service.endBattle(battle) }.onFailure {
                Cobblemon.LOGGER.error("Could not close Showdown battle {} ({})", battle.battleId, it.javaClass.simpleName)
            }
        }
    }

    @JvmStatic
    fun getBattle(id: UUID): PokemonBattle? {
        return battleMap[id]
    }

    @JvmStatic
    fun getBattleByParticipatingPlayer(serverPlayer: ServerPlayer): PokemonBattle? {
        return battleMap.values.find { it.getActor(serverPlayer) != null }
    }

    @JvmStatic
    fun getBattleByParticipatingPlayerId(playerId: UUID): PokemonBattle? {
        return battleMap.values.find { playerId in it.playerUUIDs }
    }

    fun tick() {
        battleMap.forEachValue(Long.MAX_VALUE) { it.tick() }
        StadiumSpectating.tick()
    }

    fun getBattleBySpectatingPlayer(playerId: UUID): PokemonBattle? = battleMap.values.firstOrNull { playerId in it.spectators }
}
