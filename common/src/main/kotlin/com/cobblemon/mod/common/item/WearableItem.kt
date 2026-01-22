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
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Equipable
import net.minecraft.world.item.ItemNameBlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

class WearableItem(val name: String, block: Block = Blocks.AIR): ItemNameBlockItem(block, Properties()), Equipable {
    companion object {
        const val MODEL_PATH = "item/wearable"
    }

    fun getModel3d(): ResourceLocation = cobblemonResource("${MODEL_PATH}/${this.name}")
    fun getModel2d(): ResourceLocation = cobblemonResource(this.name)

    override fun getEquipmentSlot(): EquipmentSlot { return EquipmentSlot.HEAD }

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        return this.swapWithEquipmentSlot(this, level, player, usedHand)
    }

    override fun getDescriptionId(): String {
        return if (block == Blocks.AIR) this.getOrCreateDescriptionId() else super.getDescriptionId()
    }

    override fun canPlace(context: BlockPlaceContext, state: BlockState): Boolean {
        //not all wearable items are placeable, so we default it to AIR and prevent placement in that case
        return if (state.block == Blocks.AIR) false else super.canPlace(context, state)
    }
}