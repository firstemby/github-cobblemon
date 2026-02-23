/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.spawningstyle

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.habitats.HabitatSpawnActivatedEvent
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawn
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawningInfluence
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.spawner.FixedAreaSpawner
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel

/**
 * A habitat spawning style defined by being triggered by something. The activation trigger, [trigger], is either
 * a redstone pulse, every tick, or every random tick.
 *
 * The spawn details are generated from the [pool] every time there is some change to the habitat block's settings,
 * then those spawn details are used for spawning Pokémon when the habitat block is triggered.
 *
 * The [chance] property is applied after the trigger logic so that not every activation necessarily results in a spawn
 * attempt.
 *
 * @author Hiroku
 * @since February 14th, 2026
 */
class ActivatedHabitatSpawning(val habitatBlockEntity: HabitatBlockEntity) : HabitatSpawningStyle {
    enum class Trigger {
        /** Requires a redstone pulse. */
        REDSTONE,
        /** Tries every tick. */
        TICK,
        /** Tries every random tick. */
        RANDOM_TICK
    }

    override val type = ActivatedHabitatSpawn.TYPE

    /** The chance that an activation by the [trigger] will result in a spawn attempt. The spawn attempt may still fail. */
    var chance = 1F
    /** The thing that will activate the habitat block into attempting to spawn something. */
    var trigger = Trigger.REDSTONE
    /** What range, in blocks, around the habitat block in which natural spawning will not occur. */
    var cancelledNaturalSpawningRange = -1
    /** The range, in blocks, around the habitat block in which spawning will be attempted. */
    var spawnRange = 16
    /** The maximum number of Pokémon that can exist from this block's spawning before it pauses. -1 for infinite. */
    var maxSpawns = -1
    /** The maximum number of Pokémon that can spawn in a single activation. This will still be capped by [maxSpawns]. */
    var maxSpawnsPerActivation = 1
    /** The pool of spawns to choose from. */
    var pool: ActivatedHabitatPool = ActivatedHabitatPool.default()

    // This is cached here so that it doesn't have to be regenerated every time the block is triggered, updated when settings change.
    var spawnDetails: List<SpawnDetail> = listOf()
    lateinit var spawner: FixedAreaSpawner

    override fun writeToNBT(nbt: CompoundTag) {
        nbt.putString(DataKeys.HABITAT_SPAWNING_STYLE, type.toString())
        nbt.putFloat(DataKeys.HABITAT_ACTIVATED_CHANCE, chance)
        nbt.putString(DataKeys.HABITAT_ACTIVATED_TRIGGER, trigger.name)
        nbt.putInt(DataKeys.HABITAT_ACTIVATED_CANCEL_RANGE, cancelledNaturalSpawningRange)
        nbt.putInt(DataKeys.HABITAT_ACTIVATED_SPAWN_RANGE, spawnRange)
        nbt.putInt(DataKeys.HABITAT_ACTIVATED_MAX_SPAWNS, maxSpawns)
        nbt.putInt(DataKeys.HABITAT_ACTIVATED_MAX_SPAWNS_PER_ACTIVATION, maxSpawnsPerActivation)

        val poolNBT = CompoundTag()
        writePoolToNBT(pool, poolNBT)
        nbt.put(DataKeys.HABITAT_POOL, poolNBT)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        chance = nbt.getFloat(DataKeys.HABITAT_ACTIVATED_CHANCE)
        trigger = Trigger.valueOf(nbt.getString(DataKeys.HABITAT_ACTIVATED_TRIGGER) ?: Trigger.REDSTONE.name)
        cancelledNaturalSpawningRange = nbt.getInt(DataKeys.HABITAT_ACTIVATED_CANCEL_RANGE)
        spawnRange = nbt.getInt(DataKeys.HABITAT_ACTIVATED_SPAWN_RANGE)
        maxSpawns = nbt.getInt(DataKeys.HABITAT_ACTIVATED_MAX_SPAWNS)
        maxSpawnsPerActivation = nbt.getInt(DataKeys.HABITAT_ACTIVATED_MAX_SPAWNS_PER_ACTIVATION)

        pool = HabitatSpawningStyle.readPoolFromNBT(
            nbt = nbt.getCompound(DataKeys.HABITAT_POOL),
            poolInitializer = ::ActivatedHabitatPool,
            spawnInitializer = ::ActivatedHabitatSpawn,
            defaultPool = ActivatedHabitatPool::default
        )
    }

    fun generateSpawnDetails(world: ServerLevel, pos: BlockPos) {
        spawnDetails = pool.createSpawnDetails(habitatBlockEntity)
        spawner = FixedAreaSpawner(
            name = "habitat_spawner_$pos",
            world = world,
            position = pos,
            spawnPool = SpawnPool("habitat_block").also {
                it.details.addAll(spawnDetails)
                it.precalculate()
            },
            maxPokemonPerChunk = 16F,
            verticalRadius = spawnRange,
            horizontalRadius = spawnRange
        )
        spawner.influences.add(ActivatedHabitatSpawningInfluence(this))
    }

    fun activate(world: ServerLevel, pos: BlockPos) {
        // Check if the spawn attempt should even happen based on chance.
        if (chance < 1F && world.random.nextFloat() >= chance) {
            return
        } else if (spawnDetails.isEmpty()) {
            // If there are no spawn details, then there's no point in trying to spawn anything.
            return
        }

        // Check if the max spawns has been reached.
        if (maxSpawns != -1) {
            val existingSpawns = habitatBlockEntity.spawnedEntityIDs.size
            if (existingSpawns >= maxSpawns) {
                return
            }
        }

        val cause = SpawnCause(
            spawner = spawner,
            entity = world.getNearestPlayer(
                pos.x.toDouble(),
                pos.y.toDouble(),
                pos.z.toDouble(),
                64.0,
                true
            )
        )

        val remainingUntilMaxSpawns = if (maxSpawns == -1) Int.MAX_VALUE else maxSpawns - habitatBlockEntity.spawnedEntityIDs.size
        val maxSpawnsForActivation = if (maxSpawnsPerActivation == -1) Int.MAX_VALUE else maxSpawnsPerActivation
        val maxSpawns = minOf(remainingUntilMaxSpawns, maxSpawnsForActivation)

        val event = HabitatSpawnActivatedEvent(
            habitatBlockEntity = habitatBlockEntity,
            activatedHabitatSpawning = this,
            cause = cause,
            maxSpawns = maxSpawns
        )

        CobblemonEvents.HABITAT_SPAWN_ACTIVATED.postThen(event = event) {
            spawner.run(
                cause = event.cause,
                maxSpawns = event.maxSpawns
            )
        }
    }
}