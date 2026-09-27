/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class CobblemonBagPartySlot(
    container: SimpleContainer,
    index: Int,
    val activeGetter: () -> Boolean,
    val itemGetter: () -> ItemStack,
    x: Int,
    y: Int
): Slot(container, index, x, y) {
    override fun getMaxStackSize(): Int = 1

    override fun mayPlace(stack: ItemStack): Boolean = false
    override fun mayPickup(player: Player): Boolean = false

    override fun getItem(): ItemStack {
        return itemGetter.invoke()
    }

    override fun isActive(): Boolean {
        return activeGetter.invoke()
    }
}