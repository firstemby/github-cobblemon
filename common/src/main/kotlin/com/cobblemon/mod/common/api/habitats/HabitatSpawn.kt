/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.BestSpawner
import com.cobblemon.mod.common.api.spawning.IntRanges
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.api.spawning.condition.AppendageCondition
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.adapters.IntRangesAdapter
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.math.intersection
import com.cobblemon.mod.common.util.math.intersects
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * A kind of simplified spawn definition for the purposes of spawning from [HabitatBlockEntity] instances. This is a
 * very shallow class tree, with [ActivatedHabitatSpawn] being a trivial subclass but the other subclass being
 * [NaturalHabitatSpawn] which introduces the need to supply a spawn bucket.
 *
 * These have only a small subset of the properties that a regular [PokemonSpawnDetail] would have to align with the
 * fact that these are configured from a simple GUI. There are limits to how many properties and conditions could be
 * exposed in a GUI before it becomes too difficult for the average user to understand what's going on.
 *
 * [HabitatSpawn]s are loaded from datapacks as pools of spawns but can also be modified from in-game, though those
 * made from in-game are stored separately to datapacks. Only non-datapacked habitat spawns can be modified.
 *
 * Upon edits to a habitat block, the spawn details are regenerated based off of these definitions.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
sealed class HabitatSpawn {
    /** The species that will be spawned. */
    lateinit var species: Species
    /** The kind of position the Pokémon will spawn in. Things like "grounded", "seafloor", "fishing". */
    lateinit var spawnablePositionType: String

    /** Selection weight. */
    var weight: Float = 1F
    /** The possible levels for this spawn. */
    var levelRange: IntRange = 1..Int.MAX_VALUE // I can't access the up-to-date config value here.
    /** Additional modifiers to apply to the Pokémon, such as alolan=true, ability=intimidate, etc. */
    var modifiers: PokemonProperties? = null
    /** Which phases of the habitat this spawn will run in. See [HabitatBlockEntity] for more information. */
    var phases: IntRanges? = null
    /** The Minecraft time ranges for this spawn. */
    var timeRange: TimeRange? = null
    /** The minimum light level that the Pokémon can spawn in. */
    var minLight: Int? = null
    /** The maximum light level that the Pokémon can spawn in. */
    var maxLight: Int? = null

    open fun writeToNBT(nbt: CompoundTag) {
        nbt.putString(DataKeys.HABITAT_POOL_SPAWN_SPECIES, species.resourceIdentifier.toString())
        nbt.putString(DataKeys.HABITAT_POOL_SPAWN_POSITION_TYPE, spawnablePositionType)
        nbt.putFloat(DataKeys.HABITAT_POOL_SPAWN_WEIGHT, weight)
        nbt.putString(DataKeys.HABITAT_POOL_SPAWN_LEVEL_RANGE, IntRangeAdapter.serialize(levelRange))
        modifiers?.let { nbt.putString(DataKeys.HABITAT_POOL_SPAWN_MODIFIERS, it.asString()) }
        phases?.let { nbt.putString(DataKeys.HABITAT_POOL_SPAWN_PHASES, IntRangesAdapter.basic.serialize(it)) }
        timeRange?.let { nbt.putString(DataKeys.HABITAT_POOL_SPAWN_TIMES, TimeRange.adapter.serialize(it)) }
        minLight?.let { nbt.putInt(DataKeys.HABITAT_POOL_SPAWN_MIN_LIGHT, it) }
        maxLight?.let { nbt.putInt(DataKeys.HABITAT_POOL_SPAWN_MAX_LIGHT, it) }
    }

    open fun readFromNBT(nbt: CompoundTag) {
        species = PokemonSpecies.getByIdentifier(nbt.getString(DataKeys.HABITAT_POOL_SPAWN_SPECIES).asIdentifierDefaultingNamespace())!!
        spawnablePositionType = nbt.getString(DataKeys.HABITAT_POOL_SPAWN_POSITION_TYPE)
        weight = nbt.getFloat(DataKeys.HABITAT_POOL_SPAWN_WEIGHT)
        levelRange = IntRangeAdapter.deserialize(nbt.getString(DataKeys.HABITAT_POOL_SPAWN_LEVEL_RANGE))
        modifiers = if (nbt.contains(DataKeys.HABITAT_POOL_SPAWN_MODIFIERS)) PokemonProperties.parse(nbt.getString(DataKeys.HABITAT_POOL_SPAWN_MODIFIERS)) else null
        phases = if (nbt.contains(DataKeys.HABITAT_POOL_SPAWN_PHASES)) IntRangesAdapter.basic.deserialize(nbt.getString(DataKeys.HABITAT_POOL_SPAWN_PHASES)) else null
        timeRange = if (nbt.contains(DataKeys.HABITAT_POOL_SPAWN_TIMES)) TimeRange.adapter.deserialize(nbt.getString(DataKeys.HABITAT_POOL_SPAWN_TIMES)) else null
        minLight = if (nbt.contains(DataKeys.HABITAT_POOL_SPAWN_MIN_LIGHT)) nbt.getInt(DataKeys.HABITAT_POOL_SPAWN_MIN_LIGHT) else null
        maxLight = if (nbt.contains(DataKeys.HABITAT_POOL_SPAWN_MAX_LIGHT)) nbt.getInt(DataKeys.HABITAT_POOL_SPAWN_MAX_LIGHT) else null
    }

    open fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(species.resourceIdentifier)
        buffer.writeUtf(spawnablePositionType)
        buffer.writeFloat(weight)
        buffer.writeUtf(IntRangeAdapter.serialize(levelRange))
        buffer.writeNullable(modifiers) { _, value -> buffer.writeUtf(value.asString()) }
        buffer.writeNullable(phases) { _, value -> buffer.writeUtf(IntRangesAdapter.basic.serialize(value)) }
        buffer.writeNullable(timeRange) { _, value -> buffer.writeUtf(TimeRange.adapter.serialize(value)) }
        buffer.writeNullable(minLight) { _, value -> buffer.writeInt(value) }
        buffer.writeNullable(maxLight) { _, value -> buffer.writeInt(value) }
    }

    open fun decode(
        buffer: RegistryFriendlyByteBuf,
        buckets: List<SpawnBucket>
    ) {
        species = PokemonSpecies.getByIdentifier(buffer.readResourceLocation())!!
        spawnablePositionType = buffer.readUtf()
        weight = buffer.readFloat()
        levelRange = IntRangeAdapter.deserialize(buffer.readUtf())
        modifiers = buffer.readNullable { _ -> PokemonProperties.parse(buffer.readUtf()) }
        phases = buffer.readNullable { _ -> IntRangesAdapter.basic.deserialize(buffer.readUtf()) }
        timeRange = buffer.readNullable { _ -> TimeRange.adapter.deserialize(buffer.readUtf()) }
        minLight = buffer.readNullable { _ -> buffer.readInt() }
        maxLight = buffer.readNullable { _ -> buffer.readInt() }
    }

    /** Creates the [PokemonSpawnDetail] that will be used in the spawner. This is done per habitat block. */
    open fun createSpawnDetail(habitatBlockEntity: HabitatBlockEntity): PokemonSpawnDetail? {
        val spawnDetail = PokemonSpawnDetail()

        // If the habitat block specifies a level range, it restricts how and if this spawn exists
        if (!levelRange.intersects(habitatBlockEntity.levelRange)) {
            return null
        }

        val spawnablePositionType = SpawnablePosition.getByName(this.spawnablePositionType) ?: return null // How'd they get bad data into here

        val levelRange = levelRange.intersection(habitatBlockEntity.levelRange)

        val condition = createCondition(habitatBlockEntity)
        val properties = modifiers?.copy() ?: PokemonProperties()
        properties.species = species.resourceIdentifier.path

        spawnDetail.apply {
            this.id = "habitat_${species.resourceIdentifier}"
            this.pokemon = properties
            this.bucket = BestSpawner.config.buckets.first { it.weight > 0 }
            this.weight = this@HabitatSpawn.weight
            this.levelRange = levelRange
            this.conditions = mutableListOf(condition)
            this.spawnablePositionType = spawnablePositionType
        }

        return spawnDetail
    }

    fun createCondition(habitatBlockEntity: HabitatBlockEntity): SpawningCondition<*> {
        val clazz = SpawningCondition.getByName(SpawnablePosition.getByName(this.spawnablePositionType)!!.defaultCondition)!!
        val condition = clazz.getConstructor().newInstance()
        condition.timeRange = timeRange
        condition.minLight = minLight
        condition.maxLight = maxLight
        val phases = phases
        if (phases != null) {
            condition.appendages.add(PhaseAppendageCondition(habitatBlockEntity, phases))
        }
        return condition
    }

    class PhaseAppendageCondition(val habitatBlockEntity: HabitatBlockEntity, val phases: IntRanges) : AppendageCondition {
        override fun fits(spawnablePosition: SpawnablePosition) = habitatBlockEntity.currentPhase in phases
    }
}