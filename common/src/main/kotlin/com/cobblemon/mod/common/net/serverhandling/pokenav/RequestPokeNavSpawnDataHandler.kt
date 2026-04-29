/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokenav

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.prioritizedAreaCalculators
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavSpawnDataPacket
import com.cobblemon.mod.common.net.messages.server.pokenav.RequestPokeNavSpawnDataPacket
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.spawner
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.resources.ResourceLocation

object RequestPokeNavSpawnDataHandler : ServerNetworkPacketHandler<RequestPokeNavSpawnDataPacket> {
    override fun handle(packet: RequestPokeNavSpawnDataPacket, server: MinecraftServer, player: ServerPlayer) {
        val buckets = Cobblemon.bestSpawner.config.buckets.filter { it.weight > 0F }
        if (buckets.isEmpty()) {
            PokeNavSpawnDataPacket(
                buckets = emptyList(),
                selectedBucket = "",
                entries = emptyList()
            ).sendToPlayer(player)
            return
        }

        val selectedBucket = packet.bucketName
            ?.let { requested -> buckets.firstOrNull { it.name.equals(requested, ignoreCase = true) } }
            ?: buckets.first()

        val spawner = player.spawner
        val cause = SpawnCause(spawner = spawner, entity = player)
        val zoneInput = SpawningZoneInput(
            cause = cause,
            world = player.level() as ServerLevel,
            baseX = Mth.ceil(player.x - Cobblemon.config.spawningZoneDiameter / 2F),
            baseY = Mth.ceil(player.y - Cobblemon.config.spawningZoneHeight / 2F),
            baseZ = Mth.ceil(player.z - Cobblemon.config.spawningZoneDiameter / 2F),
            length = Cobblemon.config.spawningZoneDiameter,
            height = Cobblemon.config.spawningZoneHeight,
            width = Cobblemon.config.spawningZoneDiameter
        )

        val zone = Cobblemon.spawningZoneGenerator.generate(spawner = spawner, input = zoneInput)
        val contexts = Cobblemon.areaSpawnablePositionResolver.resolve(
            spawner,
            prioritizedAreaCalculators,
            zone
        )

        val spawnProbabilities = spawner.selector.getProbabilities(spawner, selectedBucket, contexts)
        val aggregated = mutableMapOf<ResourceLocation, Float>()
        spawnProbabilities.forEach { (detail, chance) ->
            val pokemonDetail = detail as? PokemonSpawnDetail ?: return@forEach
            val speciesString = pokemonDetail.pokemon.species ?: return@forEach
            if (speciesString.equals("random", ignoreCase = true)) {
                return@forEach
            }

            val speciesId = runCatching { speciesString.asIdentifierDefaultingNamespace() }.getOrNull() ?: return@forEach
            aggregated[speciesId] = (aggregated[speciesId] ?: 0F) + chance
        }

        val entries = aggregated.entries
            .sortedByDescending { it.value }
            .map { (speciesId, chance) ->
                PokeNavSpawnDataPacket.SpawnChanceEntry(
                    speciesId = speciesId,
                    chance = chance.coerceAtLeast(0F)
                )
            }

        PokeNavSpawnDataPacket(
            buckets = buckets.map { it.name },
            selectedBucket = selectedBucket.name,
            entries = entries
        ).sendToPlayer(player)
    }
}
