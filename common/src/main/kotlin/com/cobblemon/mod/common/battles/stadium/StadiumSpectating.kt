/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneEndPacket
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumSpectateStatePacket
import com.cobblemon.mod.common.util.server
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import java.util.UUID

object StadiumSpectating {
    private class Session(val id: UUID, val configId: String, val dimension: ResourceLocation,
                          val participants: Set<UUID>, val sceneId: UUID?, var battle: PokemonBattle? = null) {
        val viewers = mutableSetOf<UUID>()
        fun active() = battle?.let { !it.ended } ?: (sceneId?.let(CinematicManager::getScene)?.isActive() == true)
    }

    private val sessions = mutableMapOf<UUID, Session>()
    private val watching = mutableMapOf<UUID, UUID>()
    private val sent = mutableMapOf<UUID, StadiumSpectateStatePacket>()
    private var ticks = 0

    fun isWatching(playerId: UUID) = playerId in watching

    fun isRunning(configId: String, dimension: ResourceLocation) = sessions.values.any {
        it.configId == configId && it.dimension == dimension && it.active()
    }

    fun start(config: StadiumBattleConfig, dimension: ResourceLocation, participants: Set<UUID>, sceneId: UUID? = null): UUID {
        val id = UUID.randomUUID()
        sessions[id] = Session(id, config.id, dimension, participants, sceneId)
        sceneId?.let(CinematicManager::getScene)?.onStop {
            if (sessions[id]?.battle == null) end(id)
        }
        return id
    }

    fun attachBattle(id: UUID, battle: PokemonBattle) {
        val session = sessions[id] ?: return
        session.battle = battle
        battle.onEndHandlers += { end(id) }
        session.viewers.toList().mapNotNull { server()?.playerList?.getPlayer(it) }.forEach { player ->
            runCatching { initializeBattle(player, battle) }.onFailure {
                Cobblemon.LOGGER.error("Could not initialize stadium spectator {}", player.uuid, it)
                leave(player, id)
            }
        }
    }

    fun joinBattle(player: ServerPlayer, battleId: UUID) {
        sessions.values.firstOrNull { it.battle?.battleId == battleId }?.let { join(player, it.id) }
    }

    fun join(player: ServerPlayer, id: UUID) {
        val session = sessions[id] ?: return
        if (player.uuid in watching || !available(player) || !eligible(session, player)) return
        watching[player.uuid] = id
        session.viewers.add(player.uuid)
        try {
            session.battle?.let { initializeBattle(player, it) }
            session.sceneId?.let(CinematicManager::getScene)?.takeIf { it.isActive() }?.addViewerPlayer(player)
            sync(player, StadiumSpectateStatePacket(id, name(session), true))
        } catch (exception: Exception) {
            Cobblemon.LOGGER.error("Could not join stadium spectator {}", player.uuid, exception)
            leave(player, id)
        }
    }

    private fun initializeBattle(player: ServerPlayer, battle: PokemonBattle) {
        if (battle.ended || !battle.spectators.add(player.uuid)) return
        player.sendPacket(BattleInitializePacket(battle, null))
        player.sendPacket(BattleMessagePacket(battle.chatLog))
    }

    fun leave(player: ServerPlayer, id: UUID? = watching[player.uuid]): Boolean {
        if (id == null || watching[player.uuid] != id) return false
        watching.remove(player.uuid)
        sessions[id]?.let { releaseViewer(it, player.uuid, player) }
        cleanUp(player.uuid) { sync(player, StadiumSpectateStatePacket(null)) }
        return true
    }

    private fun releaseViewer(session: Session, uuid: UUID, player: ServerPlayer?) {
        session.viewers.remove(uuid)
        val wasSpectating = session.battle?.spectators?.remove(uuid) == true
        session.sceneId?.let { sceneId ->
            cleanUp(uuid) { CinematicManager.getScene(sceneId)?.removeViewer(uuid) }
            if (player != null) cleanUp(uuid) { player.sendPacket(SceneEndPacket(sceneId)) }
        }
        if (player != null && wasSpectating) cleanUp(uuid) {
            if (BattleRegistry.getBattleByParticipatingPlayer(player) == null) player.sendPacket(BattleEndPacket())
        }
    }

    private inline fun cleanUp(uuid: UUID, action: () -> Unit) {
        runCatching(action).onFailure { Cobblemon.LOGGER.error("Could not clean up stadium spectator {}", uuid, it) }
    }

    fun leaveBattle(player: ServerPlayer, battleId: UUID): Boolean {
        val id = watching[player.uuid] ?: return false
        if (sessions[id]?.battle?.battleId != battleId) return false
        return leave(player, id)
    }

    fun disconnect(player: ServerPlayer) {
        leave(player)
        sent.remove(player.uuid)
    }

    private fun end(id: UUID) {
        val session = sessions.remove(id) ?: return
        session.viewers.toList().forEach { uuid ->
            watching.remove(uuid, id)
            val player = server()?.playerList?.getPlayer(uuid)
            releaseViewer(session, uuid, player)
            if (player != null) cleanUp(uuid) { sync(player, StadiumSpectateStatePacket(null)) }
            else sent.remove(uuid)
        }
    }

    fun clear() {
        sessions.keys.toList().forEach(::end)
        watching.clear()
        sent.clear()
        ticks = 0
    }

    fun tick() {
        val server = server() ?: return
        sessions.values.filter { !it.active() || StadiumBattleRegistry.get(it.configId) == null }.map { it.id }.forEach(::end)
        for ((uuid, id) in watching.toMap()) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            val session = sessions[id] ?: continue
            val config = StadiumBattleRegistry.get(session.configId)
            if (!player.isAlive || player.hurtTime > 0 || !Cobblemon.config.allowSpectating ||
                player.level().dimension().location() != session.dimension ||
                config?.spectatorAreas?.none { it.contains(player.position()) } != false ||
                BattleRegistry.getBattleByParticipatingPlayer(player) != null) leave(player, id)
        }
        if (++ticks % 5 != 0) return
        for (player in server.playerList.players) {
            if (player.uuid in watching) continue
            runCatching { refresh(player) }.onFailure {
                Cobblemon.LOGGER.error("Could not refresh stadium spectator {}", player.uuid, it)
            }
        }
    }

    fun refresh(player: ServerPlayer, force: Boolean = false) {
        val joined = watching[player.uuid]?.let(sessions::get)
        val candidate = if (joined == null && available(player)) sessions.values.filter { eligible(it, player) }
            .minByOrNull { StadiumBattleRegistry.get(it.configId)!!.arena!!.center.distanceToSqr(player.position()) } else null
        val state = joined?.let { StadiumSpectateStatePacket(it.id, name(it), true) }
            ?: candidate?.let { StadiumSpectateStatePacket(it.id, name(it)) } ?: StadiumSpectateStatePacket(null)
        if (force) sent.remove(player.uuid)
        sync(player, state)
    }

    private fun available(player: ServerPlayer): Boolean = Cobblemon.config.allowSpectating && player.isAlive &&
        !player.isSpectator && !player.isPassenger && BattleRegistry.getBattleByParticipatingPlayer(player) == null &&
        BattleRegistry.getBattleBySpectatingPlayer(player.uuid) == null &&
        CinematicManager.getActiveScenes().none { player.uuid in it.networkSync.viewerIdsSnapshot() }

    private fun eligible(session: Session, player: ServerPlayer): Boolean {
        if (!session.active() || player.uuid in session.participants || session.dimension != player.level().dimension().location()) return false
        val config = StadiumBattleRegistry.get(session.configId) ?: return false
        if (config.spectatorAreas.none { it.contains(player.position()) }) return false
        val arena = config.arena ?: return false
        val aim = StadiumSpectatorArea.aimPoint(arena, player.eyePosition, player.lookAngle) ?: return false
        val hit = player.level().clip(ClipContext(player.eyePosition, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        return hit.type == HitResult.Type.MISS || hit.location.distanceToSqr(aim) < 0.25
    }

    private fun name(session: Session) = StadiumBattleRegistry.get(session.configId)?.displayName ?: lang("stadium.default_name")

    private fun sync(player: ServerPlayer, state: StadiumSpectateStatePacket) {
        if (sent[player.uuid] == state) return
        player.sendPacket(state)
        sent[player.uuid] = state
    }
}
