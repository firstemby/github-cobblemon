/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.dto

import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import net.minecraft.network.RegistryFriendlyByteBuf

class ActivatedHabitatSettingsDTO() {
    var chance = 1F
    var trigger: ActivatedHabitatSpawning.Trigger = ActivatedHabitatSpawning.Trigger.REDSTONE
    var cancelledNaturalSpawningRange = -1
    var spawnRange = 16
    var maxSpawns = -1
    var maxSpawnsPerActivation = 1
    var habitatPool: HabitatPoolSettingsDTO = HabitatPoolSettingsDTO(ActivatedHabitatPool.default())

    constructor(activatedHabitatSpawning: ActivatedHabitatSpawning): this() {
        this.chance = activatedHabitatSpawning.chance
        this.trigger = activatedHabitatSpawning.trigger
        this.cancelledNaturalSpawningRange = activatedHabitatSpawning.cancelledNaturalSpawningRange
        this.spawnRange = activatedHabitatSpawning.spawnRange
        this.maxSpawns = activatedHabitatSpawning.maxSpawns
        this.maxSpawnsPerActivation = activatedHabitatSpawning.maxSpawnsPerActivation
        this.habitatPool = HabitatPoolSettingsDTO(activatedHabitatSpawning.pool)
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeFloat(chance)
        buffer.writeEnum(trigger)
        buffer.writeInt(cancelledNaturalSpawningRange)
        buffer.writeInt(spawnRange)
        buffer.writeInt(maxSpawns)
        buffer.writeInt(maxSpawnsPerActivation)
        habitatPool.encode(buffer)
    }

    fun decode(
        buffer: RegistryFriendlyByteBuf,
        buckets: List<SpawnBucket>
    ) {
        this.chance = buffer.readFloat()
        this.trigger = buffer.readEnum(ActivatedHabitatSpawning.Trigger::class.java)
        this.cancelledNaturalSpawningRange = buffer.readInt()
        this.spawnRange = buffer.readInt()
        this.maxSpawns = buffer.readInt()
        this.maxSpawnsPerActivation = buffer.readInt()
        this.habitatPool = HabitatPoolSettingsDTO()
        habitatPool.decode(buffer, buckets)
    }
}