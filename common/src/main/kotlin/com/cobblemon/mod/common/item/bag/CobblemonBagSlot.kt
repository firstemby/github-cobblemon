/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.Optional
import kotlin.math.min

class CobblemonBagSlot(
    container: CobblemonBagContainer,
    var simulatedSlot: Int, //Cant override base slot variable so it is forever -1
    x: Int,
    y: Int
): Slot(container, 0, x, y) {
    override fun set(stack: ItemStack) {}

    override fun getItem(): ItemStack {
        return container.getItem(simulatedSlot)
    }

    override fun remove(amount: Int): ItemStack {
        return container.removeItem(simulatedSlot, amount)
    }

    override fun mayPlace(stack: ItemStack): Boolean = false
    override fun mayPickup(player: Player): Boolean = false
}