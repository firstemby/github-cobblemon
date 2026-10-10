/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Equipable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.Level

interface WearableItem : Equipable {
    val name: String

    companion object {
        const val MODEL_PATH = "item/wearable"
    }

    fun getModel3d(): ResourceLocation = cobblemonResource("${MODEL_PATH}/${this.name}")
    fun getModel2d(): ResourceLocation = cobblemonResource(this.name)

    override fun getEquipmentSlot(): EquipmentSlot {
        return EquipmentSlot.HEAD
    }

    /**
     * Swap stack with equipment slot
     * Copied from vanilla, modified to account for stackable items
     * @see Equipable.swapWithEquipmentSlot
     *
     * @param item
     * @param level
     * @param player
     * @param hand
     * @return the interaction result holder
     */
    fun swapStackWithEquipmentSlot(
        level: Level,
        player: Player,
        hand: InteractionHand
    ): InteractionResultHolder<ItemStack> {
        var itemStack = player.getItemInHand(hand)
        val equipmentSlot = player.getEquipmentSlotForItem(itemStack)

        if (!player.canUseSlot(equipmentSlot)) return InteractionResultHolder.pass(itemStack)

        val slotStack = player.getItemBySlot(equipmentSlot)

        if (!(!EnchantmentHelper.has(slotStack, PREVENT_ARMOR_CHANGE) || player.isCreative) || ItemStack.matches(itemStack, slotStack)) return InteractionResultHolder.fail(itemStack)
        if (!level.isClientSide()) player.awardStat(Stats.ITEM_USED.get(itemStack.item))

        val equipStack = itemStack.split(1)
        when {
            itemStack.isEmpty -> itemStack = slotStack.copyAndClear()
            else -> player.addItem(slotStack.copyAndClear())
        }

        player.setItemSlot(equipmentSlot, equipStack)
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide())
    }
}
