/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokenav

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavAreaScanDataPacket
import com.cobblemon.mod.common.net.messages.server.pokenav.RequestPokeNavAreaScanPacket
import kotlin.math.sqrt
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB

object RequestPokeNavAreaScanHandler : ServerNetworkPacketHandler<RequestPokeNavAreaScanPacket> {
    private const val SCAN_RADIUS = 50.0

    override fun handle(packet: RequestPokeNavAreaScanPacket, server: MinecraftServer, player: ServerPlayer) {
        val box = AABB.ofSize(player.position(), SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2)
        val nearbyPokemon = player.level().getEntitiesOfClass(PokemonEntity::class.java, box) { it.isAlive }

        val entries = nearbyPokemon
            .mapNotNull { entity ->
                val distanceSq = entity.distanceToSqr(player)
                if (distanceSq > SCAN_RADIUS * SCAN_RADIUS) {
                    return@mapNotNull null
                }

                PokeNavAreaScanDataPacket.AreaScanEntry(
                    entityUuid = entity.uuid,
                    speciesId = entity.exposedSpecies.resourceIdentifier,
                    distance = sqrt(distanceSq).toFloat()
                )
            }
            .sortedWith(compareBy<PokeNavAreaScanDataPacket.AreaScanEntry> { it.distance }.thenBy { it.speciesId.toString() })

        PokeNavAreaScanDataPacket(
            scanRadius = SCAN_RADIUS.toFloat(),
            entries = entries
        ).sendToPlayer(player)
    }
}
