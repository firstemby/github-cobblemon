/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.campfirepot

import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.RESULT_SLOT
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class CookingPotPreviewSlot(
    container: CraftingContainer,
    index: Int,
    x: Int,
    y: Int
) : Slot(container, index, x, y) {
    override fun isActive(): Boolean {
        return container.getItem(RESULT_SLOT).isEmpty
    }

    override fun isFake(): Boolean = true
    override fun mayPlace(stack: ItemStack): Boolean = false
    override fun mayPickup(player: Player): Boolean = false
    override fun isHighlightable() = false
}