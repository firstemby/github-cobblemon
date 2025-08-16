/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

object PickUpItemTask {
    val runtime = MoLangRuntime().setup()

    fun create(condition: Expression, maxReach: Expression): OneShot<PokemonEntity> = BehaviorBuilder.create {
        it.group(
            it.absent(MemoryModuleType.WALK_TARGET),
            it.absent(CobblemonMemories.IS_CONSUMING_ITEM),
            it.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
        ).apply(it) { walkTarget, isConsumingItem, nearbyItem ->
            Trigger { _, entity, _ ->

                val itemEntity = it.get(nearbyItem)
                if (itemEntity == null || !itemEntity.isAlive || (entity is PokemonEntity && !entity.pokemon.canDropHeldItem)) {
                    return@Trigger false
                }

                runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                val condition = runtime.resolveBoolean(condition)
                if (!condition) {
                    return@Trigger false
                }

                val maxReach = runtime.resolveDouble(maxReach)

                if (itemEntity.distanceTo(entity) > maxReach) {
                    return@Trigger false
                }

                entity.take(itemEntity, 1) // This creates the effect of the item going into the entity along with the item pickup sound
                val stack = entity.pokemon.swapHeldItem(itemEntity.item)
                if (!stack.isEmpty && !entity.level().isClientSide) {
                    dropItem(entity, stack)
                }

                return@Trigger true
            }
        }
    }

    fun dropItem(entity: Entity, stack: ItemStack) {
        val itemEntity = ItemEntity(
            entity.level(),
            entity.x + entity.lookAngle.x,
            entity.y + 1.0,
            entity.z + entity.lookAngle.z,
            stack
        )
        itemEntity.setPickUpDelay(40)
        itemEntity.setThrower(entity)
        entity.playSound(SoundEvents.FOX_SPIT, 1.0f, 1.0f) // TODO: customize this sound?
        entity.level().addFreshEntity(itemEntity)
    }
}