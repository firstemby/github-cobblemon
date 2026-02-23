/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.dto

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawn
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatPools
import com.cobblemon.mod.common.api.habitats.HabitatSpawn
import com.cobblemon.mod.common.api.habitats.NaturalHabitatPool
import com.cobblemon.mod.common.api.habitats.NaturalHabitatSpawn
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class HabitatPoolSettingsDTO() {
    lateinit var id: ResourceLocation
    var isReference = false
    var isActivated = false

    // Below properties are only there when it's not a reference, otherwise the handler will have to look them up from the id
    var name: String? = null
    var spawns: MutableList<HabitatSpawn>? = null

    constructor(habitatPool: HabitatPool<*>): this() {
        this.id = habitatPool.id
        this.isReference = !id.path.startsWith("custom__")
        this.isActivated = habitatPool is ActivatedHabitatPool
        if (!this.isReference) {
            this.name = habitatPool.name
            this.spawns = habitatPool.spawns.toMutableList()
        }
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(id)
        buffer.writeBoolean(isReference)
        buffer.writeBoolean(isActivated)
        if (!isReference) {
            buffer.writeUtf(name!!)
            buffer.writeCollection(spawns!!) { _, spawn ->
                spawn.encode(buffer)
            }
        }
    }

    fun decode(
        buffer: RegistryFriendlyByteBuf,
        buckets: List<SpawnBucket>
    ) {
        this.id = buffer.readResourceLocation()
        this.isReference = buffer.readBoolean()
        this.isActivated = buffer.readBoolean()
        if (!isReference) {
            this.name = buffer.readUtf()
            this.spawns = buffer.readList {
                val spawn = if (isActivated) {
                    ActivatedHabitatSpawn()
                } else {
                    NaturalHabitatSpawn()
                }
                spawn.decode(buffer, buckets)
                spawn
            }.toMutableList()
        }
    }

    fun toHabitatPool(): HabitatPool<*>? {
        if (isReference) {
            val pool = HabitatPools.habitatPoolsById[id]
            if (pool == null) {
                Cobblemon.LOGGER.error("Could not find habitat pool with id $id")
            } else if (pool is ActivatedHabitatPool && !isActivated) {
                Cobblemon.LOGGER.error("Pool with id $id is an activated habitat pool but was deserialized as a natural pool")
            } else if (pool !is ActivatedHabitatPool && isActivated) {
                Cobblemon.LOGGER.error("Pool with id $id is a natural habitat pool but was deserialized as an activated pool")
            }
            return pool
        }
        return if (isActivated) {
            val pool = ActivatedHabitatPool()
            pool.id = id
            pool.name = name!!
            pool.spawns = spawns!!.map { it as ActivatedHabitatSpawn }
            pool
        } else {
            val pool = NaturalHabitatPool()
            pool.id = id
            pool.name = name!!
            pool.spawns = spawns!!.map { it as NaturalHabitatSpawn }
            pool
        }
    }
}