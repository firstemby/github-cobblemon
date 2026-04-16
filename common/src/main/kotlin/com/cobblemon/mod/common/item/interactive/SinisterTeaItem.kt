/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.food

import com.cobblemon.mod.common.CobblemonItemComponents
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.Consumables
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

class SinisterTeaItem(properties: Properties) : Item(properties) {
    //TODO THIS CAN BE A CONSUMABLE DATA COMPONENT NOW
    override fun finishUsingItem(stack: ItemStack, world: Level, user: LivingEntity): ItemStack {
        if (!world.isClientSide) {
            val effectsComponent = stack.get(CobblemonItemComponents.MOB_EFFECTS)

            if (effectsComponent != null) {
                for (raw in effectsComponent.mobEffects) {
                    val inst = MobEffectInstance(raw)
                    val holder = inst.effect
                    val effect = holder.value()

                    if (effect.isInstantenous) {
                        if (world is ServerLevel) {
                            effect.applyInstantenousEffect(world, user as? Player, null, user, inst.amplifier, 1.0)
                        }
                    } else {
                        user.addEffect(inst)
                    }
                }
            }
        }

        return super.finishUsingItem(stack, world, user)
    }

    override fun getUseAnimation(stack: ItemStack) = ItemUseAnimation.DRINK
}
