/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.google.common.collect.ImmutableSet
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.item.Items
import net.minecraft.world.entity.player.Player

class PlayerDebuggingSensor : Sensor<LivingEntity>(SCAN_INTERVAL) {

    override fun doTick(level: ServerLevel, entity: LivingEntity) {
        var diamondHolder: LivingEntity? = null
        diamondHolder = entity.brain.getMemory(CobblemonMemories.DIAMOND_DEBUGGER).orElse(null)?.let {
            if(it.mainHandItem.`is`(Items.DIAMOND)) {
                it
            } else {
                null
            }
        }

        if (diamondHolder == null) {
            val nearEntities = entity.brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(mutableListOf())
            diamondHolder = nearEntities?.find { entity ->
                entity.mainHandItem.`is`(Items.DIAMOND)
            }
        }

        if (diamondHolder != null && diamondHolder is Player) {
            entity.brain.setMemory(CobblemonMemories.DIAMOND_DEBUGGER, diamondHolder)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.DIAMOND_DEBUGGER)
        }
    }

    override fun requires(): Set<MemoryModuleType<*>> {
            return setOf(
                CobblemonMemories.DIAMOND_DEBUGGER,
                MemoryModuleType.NEAREST_LIVING_ENTITIES
            )
        }

    companion object {
            const val SCAN_INTERVAL = 10
        }
}
