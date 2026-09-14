package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.net.messages.client.battle.StadiumBattleStatePacket
import com.cobblemon.mod.common.net.messages.client.battle.StadiumPreviewMember
import com.google.gson.Gson
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component

class StadiumBattleSession(private val battle: PokemonBattle, val config: StadiumFormatConfig) {
    private data class Pending(val rqid: Int, val preview: Boolean, var submitted: Boolean = false)
    private val pending = mutableMapOf<String, Pending>()
    private val previewTeams = battle.actors.associate { it.uuid to it.pokemonList.toList() }
    private val clock = StadiumDecisionClock(config.moveTimer, config.matchTimerMinutes)
    private val gson = Gson()
    private var resolved = false
    private var previewComplete = !config.teamPreview
    private var ticks = 0

    fun request(actor: BattleActor, request: ShowdownActionRequest) {
        if (request.wait) { pending.remove(actor.showdownId); return }
        if (!request.teamPreview) request.side?.let { side ->
            val team = actor.pokemonList.associateBy { it.uuid }
            val order = side.pokemon.map { it.uuid }
            if (order.size != team.size || order.toSet() != team.keys) {
                BattleRegistry.abortStadiumBattle(battle.battleId)
                return
            }
            actor.pokemonList.clear()
            actor.pokemonList.addAll(order.map { team.getValue(it) })
        }
        if (pending[actor.showdownId]?.rqid == request.rqid) return
        pending[actor.showdownId] = Pending(request.rqid, request.teamPreview)
        clock.request(actor.showdownId, request.rqid)
        if (request.teamPreview) {
            actor.mustChoose = false
            notify(actor)
            if (actor.type != ActorType.PLAYER) {
                submit(actor, request.rqid, actor.pokemonList.indices.sortedBy { actor.pokemonList[it].health <= 0 }.map { it + 1 })
            }
        }
    }

    fun submit(actor: BattleActor, rqid: Int, order: List<Int>) {
        val state = pending[actor.showdownId] ?: return
        if (!state.preview || state.submitted || state.rqid != rqid || battle.ended || resolved) return
        if (!validOrder(order, actor.pokemonList.map { it.health > 0 }, battle.format.battleType.slotsPerActor)) {
            notify(actor, lang("stadium.preview.invalid_order"))
            return
        }
        state.submitted = true
        val team = actor.pokemonList.toList()
        actor.pokemonList.clear()
        actor.pokemonList.addAll(order.map { team[it - 1] })
        val result = runCatching { ShowdownService.service.sendStadiumChoice(battle.battleId, gson.toJson(mapOf(
            "protocolVersion" to 1, "side" to actor.showdownId, "rqid" to rqid, "choice" to "team ${order.joinToString(",")}"))) }
            .getOrElse { BattleRegistry.abortStadiumBattle(battle.battleId); return }
        if (!result.ok) {
            actor.pokemonList.clear()
            actor.pokemonList.addAll(team)
            state.submitted = false
            notify(actor, result.userMessage)
        } else if (!battle.ended) notify(actor)
    }

    fun tick() {
        if (resolved || battle.ended) return
        CinematicManager.sceneForBattle(battle.battleId)?.let(CinematicManager::getScene)?.let { scene ->
            battle.sides.forEachIndexed { side, actors -> actors.activePokemon.forEachIndexed activeSlot@{ slot, active ->
                val entity = active.battlePokemon?.entity ?: return@activeSlot
                val role = "pokemon_${side + 1}${('a'.code + slot).toChar()}"
                if ((scene.actorDirector.bindings[role] as? ActorBinding.Real)?.entity !== entity) scene.updateActorBinding(role, ActorBinding.Real(entity))
            } }
        }
        val ready = battle.dispatches.isEmpty() && battle.dispatchResult.canProceed()
        val active = if (!ready) emptySet() else battle.actors.filter { actor ->
            pending[actor.showdownId]?.let { !it.submitted && (it.preview || actor.mustChoose) } == true
        }.map { it.showdownId }.toSet()
        val expired = clock.tick(active)
        if (clock.matchTicks == 0 || expired.isNotEmpty()) {
            val payload = if (clock.matchTicks == 0) mapOf("protocolVersion" to 1, "scope" to "match") else mapOf(
                "protocolVersion" to 1, "scope" to "decision", "expiredSides" to expired.map { mapOf("id" to it, "rqid" to pending.getValue(it).rqid) })
            resolved = true
            val result = runCatching { ShowdownService.service.resolveStadiumTimeout(battle.battleId, gson.toJson(payload)) }
                .getOrElse { BattleRegistry.abortStadiumBattle(battle.battleId); return }
            if (!result.ok) BattleRegistry.abortStadiumBattle(battle.battleId)
        }
        if (++ticks % 20 == 0) battle.actors.forEach { notify(it) }
    }

    fun beginBattle() {
        previewComplete = true
        pending.entries.removeIf { it.value.preview }
        battle.actors.forEach { notify(it) }
    }

    private fun notify(actor: BattleActor, message: Component = Component.empty()) {
        if (actor.type != ActorType.PLAYER) return
        val state = pending[actor.showdownId]
        val preview = !previewComplete
        fun members(side: BattleActor, own: Boolean) = previewTeams.getValue(side.uuid).map {
            val pokemon = it.effectedPokemon
            StadiumPreviewMember(pokemon.species.resourceIdentifier.toString(), pokemon.aspects,
                if (own) it.getName() else pokemon.species.translatedName, !own || it.health > 0)
        }
        val opponent = actor.getSide().getOppositeSide().actors.single()
        actor.sendUpdate(StadiumBattleStatePacket(battle.battleId,
            if (preview) if (state == null || state.submitted) "waiting" else "preview" else "battle",
            state?.rqid ?: clock.decisions[actor.showdownId]?.rqid ?: 0, battle.format.battleType.slotsPerActor,
            if (preview) members(actor, true) else emptyList(), if (preview) members(opponent, false) else emptyList(),
            clock.seconds(actor.showdownId), clock.seconds(opponent.showdownId), clock.matchSeconds(), message))
    }

    fun close() {
        resolved = true
        battle.actors.forEach { actor -> runCatching { actor.sendUpdate(StadiumBattleStatePacket(battle.battleId, "closed")) } }
    }

    companion object {
        fun validOrder(order: List<Int>, healthy: List<Boolean>, slots: Int) = order.size == healthy.size &&
            order.toSet() == (1..healthy.size).toSet() && order.take(slots).size == slots &&
            order.take(slots).all { healthy[it - 1] }
    }
}
