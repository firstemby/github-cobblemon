/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.dto

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatPools
import com.cobblemon.mod.common.api.habitats.HabitatSpawn
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class HabitatPoolSettingsDTO() {
    lateinit var id: ResourceLocation
    var isReference = false

    // Below properties are only there when it's not a reference, otherwise the handler will have to look them up from the id
    var name: String? = null
    var spawns: MutableList<HabitatSpawn>? = null

    constructor(habitatPool: HabitatPool): this() {
        this.id = habitatPool.id
        this.isReference = !id.path.startsWith("custom__")
        if (!this.isReference) {
            this.name = habitatPool.name
            this.spawns = habitatPool.spawns.toMutableList()
        }
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(id)
        buffer.writeBoolean(isReference)
        if (!isReference) {
            buffer.writeUtf(name!!)
            buffer.writeCollection(spawns!!) { _, spawn ->
                spawn.encode(buffer)
            }
        }
    }

    fun decode(
        buffer: RegistryFriendlyByteBuf
    ) {
        this.id = buffer.readResourceLocation()
        this.isReference = buffer.readBoolean()
        if (!isReference) {
            this.name = buffer.readUtf()
            this.spawns = buffer.readList {
                val spawn = HabitatSpawn()
                spawn.decode(buffer)
                spawn
            }.toMutableList()
        }
    }

    fun toHabitatPool(): HabitatPool? {
        if (isReference) {
            val pool = HabitatPools.habitatPoolsById[id]
            if (pool == null) {
                Cobblemon.LOGGER.error("Could not find habitat pool with id $id")
            }
            return pool
        }
        val pool = HabitatPool()
        pool.id = id
        pool.name = name!!
        pool.spawns = spawns!!
        return pool
    }
}