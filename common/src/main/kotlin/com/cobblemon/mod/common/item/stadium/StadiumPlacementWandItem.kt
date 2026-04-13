/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.stadium

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.net.messages.server.stadium.StadiumPlacementSetPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class StadiumPlacementWandItem : CobblemonItem(Properties().stacksTo(1)) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val stack = context.itemInHand
        val nbt = getWandData(stack) ?: return InteractionResult.PASS

        val stadiumBlockPos = BlockPos(nbt.getInt("StadiumX"), nbt.getInt("StadiumY"), nbt.getInt("StadiumZ"))
        val layoutType = nbt.getString("LayoutType")
        val sideIndex = nbt.getInt("SideIndex")
        val slotType = nbt.getString("SlotType")
        val slotIndex = nbt.getInt("SlotIndex")

        val clickedPos = context.clickedPos
        val clickedFace = context.clickedFace
        val placedPos = Vec3(
            clickedPos.x + 0.5 + clickedFace.stepX * 0.5,
            clickedPos.y.toDouble() + 1.0,
            clickedPos.z + 0.5 + clickedFace.stepZ * 0.5
        )

        if (player.level().isClientSide) {
            CobblemonNetwork.sendToServer(
                StadiumPlacementSetPacket(stadiumBlockPos, layoutType, sideIndex, slotType, slotIndex, placedPos)
            )
        }

        stack.shrink(1)
        return InteractionResult.SUCCESS
    }

    override fun isFoil(stack: ItemStack): Boolean = true

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide || entity !is Player) return
        val nbt = getWandData(stack) ?: return

        val stadiumPos = BlockPos(nbt.getInt("StadiumX"), nbt.getInt("StadiumY"), nbt.getInt("StadiumZ"))

        // Remove if too far from stadium
        if (entity.blockPosition().distSqr(stadiumPos) > 10000) { // 100 blocks
            stack.shrink(1)
            return
        }

        // Remove if player switched away from wand (no longer holding it)
        if (!isSelected) {
            stack.shrink(1)
        }
    }

    companion object {
        fun createWandStack(
            wandItem: StadiumPlacementWandItem,
            stadiumBlockPos: BlockPos,
            layoutType: String,
            sideIndex: Int,
            slotType: String,
            slotIndex: Int
        ): ItemStack {
            val stack = ItemStack(wandItem)
            val nbt = CompoundTag()
            nbt.putInt("StadiumX", stadiumBlockPos.x)
            nbt.putInt("StadiumY", stadiumBlockPos.y)
            nbt.putInt("StadiumZ", stadiumBlockPos.z)
            nbt.putString("LayoutType", layoutType)
            nbt.putInt("SideIndex", sideIndex)
            nbt.putString("SlotType", slotType)
            nbt.putInt("SlotIndex", slotIndex)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
            return stack
        }

        private fun getWandData(stack: ItemStack): CompoundTag? {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
            return customData.copyTag()
        }
    }
}
