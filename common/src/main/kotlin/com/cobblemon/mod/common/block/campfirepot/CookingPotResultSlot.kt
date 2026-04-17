/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.campfirepot

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class CookingPotResultSlot(
    container: CraftingContainer,
    index: Int,
    x: Int,
    y: Int
) : Slot(container, index, x, y) {

    var isPreview = false

    override fun mayPickup(player: Player): Boolean {
        if (isPreview) return false
        return super.mayPickup(player)
    }

    override fun onTake(player: Player, stack: ItemStack) {
        if (isPreview) return
        if (stack.has(CobblemonItemComponents.FLAVOUR)) {
            stack.set(CobblemonItemComponents.CRAFTED, true)
        }

        val menu = player.containerMenu
        if (menu is CookingPotMenu) {
            menu.broadcastChanges()
            val campfire = menu.container as? CampfireBlockEntity
            if (campfire != null) {
                player.level().playSound(null, campfire.blockPos, CobblemonSounds.CAMPFIRE_POT_TAKE_ITEM, player.soundSource, 1.0f, 1.0f)
            } else {
                player.playSound(CobblemonSounds.CAMPFIRE_POT_TAKE_ITEM, 1.0f, 1.0f)
            }
        }

        super.onTake(player, stack)
    }

    override fun set(stack: ItemStack) {
        isPreview = false
        super.set(stack)
    }

    fun setPreview(stack: ItemStack) {
        isPreview = true
        super.set(stack)
    }

    override fun mayPlace(stack: ItemStack): Boolean = false
}
