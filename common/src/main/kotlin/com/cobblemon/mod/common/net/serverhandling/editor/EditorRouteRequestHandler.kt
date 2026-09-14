/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.editor

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.NativeWaypointMotor
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.WaypointRoutePreview
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.net.messages.client.editor.EditorRouteResultS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorRouteRequestC2SPacket
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import java.util.UUID

object EditorRouteRequestHandler : ServerNetworkPacketHandler<EditorRouteRequestC2SPacket> {
    private data class Job(val request: UUID, val player: ServerPlayer, val motor: NativeWaypointMotor,
        val preview: WaypointRoutePreview, val level: ServerLevel = player.serverLevel(), val started: Long = System.nanoTime())
    private val jobs = linkedMapOf<UUID, Job>()
    private val lastRequests = mutableMapOf<UUID, Long>()

    init {
        PlatformEvents.SERVER_TICK_POST.subscribe { tick(it.server) }
        PlatformEvents.SERVER_STOPPING.subscribe {
            val active = jobs.values.toList()
            jobs.clear()
            lastRequests.clear()
            active.forEach(::close)
        }
    }

    override fun handle(packet: EditorRouteRequestC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        fun reject(message: Component) = EditorRouteResultS2CPacket(packet.requestId, error = message).sendToPlayer(player)
        if (!player.hasPermissions(2)) { reject(Component.translatable("cobblemon.cinematic_editor.route.permission")); return }
        val now = System.nanoTime()
        if (now - (lastRequests[player.uuid] ?: 0L) < 1_000_000_000L) { reject(Component.translatable("cobblemon.cinematic_editor.route.throttled")); return }
        lastRequests[player.uuid] = now
        jobs.remove(player.uuid)?.let(::close)
        if (jobs.size >= 4) { reject(Component.translatable("cobblemon.cinematic_editor.route.busy")); return }
        try {
            val track = WaypointPathfinding.fromJson(JsonParser.parseString(packet.trackJson).asJsonObject)
            if (track.controlPoints.any { it.distanceToSqr(player.position()) > 256.0 * 256.0 }) {
                reject(Component.translatable("cobblemon.cinematic_editor.route.too_far", 256))
                return
            }
            val binding = packet.binding
            if (binding is SerializedBinding.Bedrock && (!binding.scale.isFinite() || binding.scale !in 0.01f..16f)) {
                reject(Component.translatable("cobblemon.cinematic_editor.route.invalid_scale", 0.01f, 16))
                return
            }
            val source = source(packet.binding, player)
            if (!source.bbWidth.isFinite() || source.bbWidth !in 0.01f..16f ||
                !source.bbHeight.isFinite() || source.bbHeight !in 0.01f..32f) {
                reject(Component.translatable("cobblemon.cinematic_editor.route.invalid_dimensions"))
                return
            }
            val motor = NativeWaypointMotor(track, source)
            jobs[player.uuid] = Job(packet.requestId, player, motor, WaypointRoutePreview(track, motor))
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not compute editor route", error)
            reject(Component.translatable("cobblemon.cinematic_editor.route.compute_failed"))
        }
    }

    private fun source(binding: SerializedBinding, player: ServerPlayer): Entity = when (binding) {
        is SerializedBinding.Pokemon -> {
            val pokemon = PokemonProperties.parse(binding.propertiesSpec).create()
            pokemon.forcedAspects = pokemon.forcedAspects + binding.aspects
            PokemonEntity(player.serverLevel(), pokemon)
        }
        is SerializedBinding.Bedrock -> {
            GenericBedrockEntity(player.serverLevel()).apply {
                category = ResourceLocation.parse(if (':' in binding.category) binding.category else "cobblemon:${binding.category}")
                aspects = binding.aspects.toSet()
                scale = binding.scale
                savesToWorld = false
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        lastRequests.keys.removeIf { server.playerList.getPlayer(it) == null }
        for ((id, job) in jobs.toMap()) {
            if (job.player.hasDisconnected() || job.player.serverLevel() !== job.level || !job.player.hasPermissions(2)) {
                jobs.remove(id, job)
                close(job)
                continue
            }
            var failure: Component? = null
            try {
                val deadline = System.nanoTime() + 2_000_000L
                var steps = 0
                while (!job.preview.done && steps++ < 40 && System.nanoTime() < deadline) job.preview.step()
                if (System.nanoTime() - job.started > 45_000_000_000L) failure = Component.translatable("cobblemon.cinematic_editor.route.timed_out")
            } catch (error: Exception) {
                failure = Component.translatable("cobblemon.cinematic_editor.route.simulation_failed")
                Cobblemon.LOGGER.warn("Route computation failed", error)
            }
            if (job.preview.done || failure != null) {
                if (!jobs.remove(id, job)) continue
                try {
                    val playback = job.preview.playback
                    val error = failure ?: playback.error
                    EditorRouteResultS2CPacket(job.request, job.preview.frames, job.preview.seconds, playback.finished,
                        playback.waypointIndex.coerceAtMost(63) + 1, playback.jumpCount, error,
                        if (error == null) emptyList() else job.motor.searchNodes.take(EditorRouteResultS2CPacket.MAX_NODES),
                        job.motor.projectedTarget).sendToPlayer(job.player)
                    Cobblemon.LOGGER.info("Computed native route: {}s, {} jumps, finished={}, {}", job.preview.seconds,
                        playback.jumpCount, playback.finished, error)
                } catch (error: Exception) {
                    Cobblemon.LOGGER.warn("Could not send editor route result", error)
                } finally {
                    close(job)
                }
            }
        }
    }

    private fun close(job: Job) {
        try {
            job.preview.close()
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not close editor route preview", error)
        }
    }

}
