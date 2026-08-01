/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.habitat

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatPools
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readMapK
import com.cobblemon.mod.common.util.writeMapK
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Opens the habitat block editor GUI with information about the habitat pools to choose from as well as the current
 * settings. The client will then send a SaveHabitatBlockSettingsPacket with the modified settings.
 *
 * @author Hiroku
 * @since February 21st, 2026
 */
class OpenHabitatBlockEditorPacket(
    val blockPos: BlockPos,
    val currentlySpawned: Int,
    val currentPhase: Int,
    val maxPokemonLevel: Int,
    val spawnablePositionTypes: List<String>,
    val worldBuckets: List<String>,
    val activatedHabitatBuckets: List<String>,
    val habitatPools: Map<ResourceLocation, HabitatPool>,
    val habitatSettingsDTO: HabitatSettingsDTO
) : NetworkPacket<OpenHabitatBlockEditorPacket> {
    companion object {
        val ID = cobblemonResource("open_habitat_block_editor")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenHabitatBlockEditorPacket {
            val blockPos = buffer.readBlockPos()
            val currentlySpawned = buffer.readInt()
            val currentPhase = buffer.readInt()
            val maxPokemonLevel = buffer.readInt()
            val spawnablePositionTypes = buffer.readList { buffer.readUtf() }
            val worldBuckets = buffer.readList { buffer.readUtf() }
            val activatedHabitatBuckets = buffer.readList { buffer.readUtf() }
            val habitatPools = buffer.readMapK(
                size = IntSize.U_SHORT,
                mutableMapOf(),
                entryReader = {
                    val pool = HabitatPool()
                    pool.decode(buffer)
                    pool.id to pool
                }
            )
            val habitatSettingsDTO = HabitatSettingsDTO()
            habitatSettingsDTO.decode(buffer)
            return OpenHabitatBlockEditorPacket(
                blockPos = blockPos,
                currentlySpawned = currentlySpawned,
                currentPhase = currentPhase,
                maxPokemonLevel = maxPokemonLevel,
                spawnablePositionTypes = spawnablePositionTypes,
                worldBuckets = worldBuckets,
                activatedHabitatBuckets = activatedHabitatBuckets,
                habitatPools = habitatPools,
                habitatSettingsDTO = habitatSettingsDTO
            )
        }
    }

    constructor(habitatBlockEntity: HabitatBlockEntity): this(
        blockPos = habitatBlockEntity.blockPos,
        currentlySpawned = habitatBlockEntity.spawnedEntityIDs.size,
        currentPhase = habitatBlockEntity.currentPhase,
        maxPokemonLevel = Cobblemon.config.maxPokemonLevel,
        spawnablePositionTypes = SpawnablePosition.spawnablePositionTypes.map { it.name },
        worldBuckets = Cobblemon.bestSpawner.config.worldBuckets.keys.toList(),
        activatedHabitatBuckets = Cobblemon.bestSpawner.config.activatedHabitatBuckets.keys.toList(),
        habitatSettingsDTO = HabitatSettingsDTO(habitatBlockEntity),
        habitatPools = HabitatPools.habitatPoolsById.toMap(),
    )

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeInt(currentlySpawned)
        buffer.writeInt(currentPhase)
        buffer.writeInt(maxPokemonLevel)
        buffer.writeCollection(spawnablePositionTypes) { _, it -> buffer.writeUtf(it) }
        buffer.writeCollection(worldBuckets) { _, bucket -> buffer.writeUtf(bucket) }
        buffer.writeCollection(activatedHabitatBuckets) { _, bucket -> buffer.writeUtf(bucket) }
        buffer.writeMapK(
            size = IntSize.U_SHORT,
            map = habitatPools,
            entryWriter = { (_, value) -> value.encode(buffer) },
        )
        habitatSettingsDTO.encode(buffer)
    }
}