/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.dto

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawn
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatSpawn
import com.cobblemon.mod.common.api.habitats.NaturalHabitatSpawn
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.IntRanges
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.adapters.IntRangesAdapter
import net.minecraft.network.RegistryFriendlyByteBuf

class HabitatSpawnSettingsDTO() {
    lateinit var species: Species
    lateinit var spawnablePositionType: String
    var weight: Float = 1F
    var levelRange: IntRange = 1..Int.MAX_VALUE // I can't access the up-to-date config value here.
    var modifiers: PokemonProperties? = null
    var phases: IntRanges? = null
    var timeRange: TimeRange? = null
    var minLight: Int? = null
    var maxLight: Int? = null
    var bucket: String? = null

    fun initCommon(spawn: HabitatSpawn) {
        species = spawn.species
        spawnablePositionType = spawn.spawnablePositionType
        weight = spawn.weight
        levelRange = spawn.levelRange
        modifiers = spawn.modifiers
        phases = spawn.phases
        timeRange = spawn.timeRange
        minLight = spawn.minLight
        maxLight = spawn.maxLight
    }

    constructor(activatedHabitatSpawn: ActivatedHabitatSpawn): this() {
        initCommon(activatedHabitatSpawn)
    }

    constructor(naturalHabitatSpawn: NaturalHabitatSpawn): this() {
        initCommon(naturalHabitatSpawn)
        bucket = naturalHabitatSpawn.bucket.name
    }

    fun <T : HabitatSpawn> toHabitatSpawn(habitatPool: HabitatPool<T>, spawnInitializer: () -> T): T {
        val spawn = spawnInitializer()
        spawn.species = species
        spawn.spawnablePositionType = spawnablePositionType
        spawn.weight = weight
        spawn.levelRange = levelRange
        spawn.modifiers = modifiers
        spawn.phases = phases
        spawn.timeRange = timeRange
        spawn.minLight = minLight
        spawn.maxLight = maxLight
        if (spawn is NaturalHabitatSpawn) {
            spawn.bucket = Cobblemon.bestSpawner.config.buckets.find { it.name == bucket!! }!!
        }
        return spawn
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(species.resourceIdentifier)
        buffer.writeUtf(spawnablePositionType)
        buffer.writeFloat(weight)
        buffer.writeUtf(IntRangeAdapter.serialize(levelRange))
        buffer.writeNullable(modifiers) { _, value -> buffer.writeUtf(value.asString()) }
        buffer.writeNullable(phases) { _, value -> buffer.writeUtf(IntRangesAdapter.basic.serialize(value)) }
        buffer.writeNullable(timeRange) { _, value -> buffer.writeUtf(TimeRange.adapter.serialize(value)) }
        buffer.writeNullable(minLight) { _, value -> buffer.writeInt(value) }
        buffer.writeNullable(maxLight) { _, value -> buffer.writeInt(value) }
        buffer.writeNullable(bucket) { _, value -> buffer.writeUtf(value) }
    }

    fun decode(buffer: RegistryFriendlyByteBuf) {
        species = PokemonSpecies.getByIdentifier(buffer.readResourceLocation())!!
        spawnablePositionType = buffer.readUtf()
        weight = buffer.readFloat()
        levelRange = IntRangeAdapter.deserialize(buffer.readUtf())
        modifiers = buffer.readNullable { _ -> PokemonProperties.parse(buffer.readUtf()) }
        phases = buffer.readNullable { _ -> IntRangesAdapter.basic.deserialize(buffer.readUtf()) }
        timeRange = buffer.readNullable { _ -> TimeRange.adapter.deserialize(buffer.readUtf()) }
        minLight = buffer.readNullable { _ -> buffer.readInt() }
        maxLight = buffer.readNullable { _ -> buffer.readInt() }
        bucket = buffer.readNullable { _ -> buffer.readUtf() }
    }
}