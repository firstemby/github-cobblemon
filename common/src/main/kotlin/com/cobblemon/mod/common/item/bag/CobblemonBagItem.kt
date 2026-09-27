/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.item.CobblemonItem
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.Equipable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class CobblemonBagItem: CobblemonItem(Properties().stacksTo(1)), MenuProvider, Equipable {
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack?> {
        val itemStack = player.getItemInHand(usedHand)
        player.openMenu(this)
        return InteractionResultHolder.success(itemStack)
    }

    override fun createMenu(i: Int, inventory: Inventory, player: Player): AbstractContainerMenu {
        val bagEntity = (player as? BagEntity)
        val container = bagEntity?.`cobblemon$getBagContainer`() ?: CobblemonBagContainer()

        return CobblemonBagMenu(i, inventory, container)
    }

    override fun getDisplayName(): Component? {
        return Component.translatable("cobblemon.container.bag")
    }

    override fun getEquipmentSlot(): EquipmentSlot = EquipmentSlot.CHEST

}