/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.stadium

import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.battles.stadium.StadiumSetup
import com.cobblemon.mod.common.battles.stadium.StadiumSpectatorArea
import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.cobblemon.mod.common.battles.stadium.StadiumBlockPlacement
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.net.messages.client.stadium.OpenStadiumConfigPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class StadiumPlacementWandItem : CobblemonItem(Properties().stacksTo(1)) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val stack = context.itemInHand
        val nbt = getWandData(stack) ?: return InteractionResult.PASS
        if (player.level().isClientSide) return InteractionResult.SUCCESS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        val block = stadium(serverPlayer, nbt) ?: return InteractionResult.FAIL
        if (player.isShiftKeyDown) {
            nbt.remove("FirstCorner")
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
            openConsole(serverPlayer, block, nbt)
            return InteractionResult.SUCCESS
        }
        if (context.clickedFace != Direction.UP) {
            player.displayClientMessage(lang("stadium.wand.click_ground"), true)
            return InteractionResult.FAIL
        }
        val position = Vec3(context.clickedPos.x + 0.5, context.clickLocation.y, context.clickedPos.z + 0.5)
        if (position.distanceToSqr(player.position()) > 64.0) return InteractionResult.FAIL
        val origin = Vec3.atLowerCornerOf(block.blockPos)
        val layoutType = nbt.getString("LayoutType")
        val bleachers = nbt.getString("SlotType") == "bleachers"
        if (nbt.getString("SlotType") == "arena" || bleachers) {
            val first = firstCorner(nbt)
            if (first == null) {
                if (!StadiumConfiguration.validPosition(position, origin)) return InteractionResult.FAIL
                nbt.put("FirstCorner", CompoundTag().apply {
                    putDouble("X", position.x); putDouble("Y", position.y); putDouble("Z", position.z)
                })
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
                player.displayClientMessage(lang("stadium.wand.first_corner_set"), true)
                return InteractionResult.SUCCESS
            }
            if (bleachers) {
                val area = StadiumSpectatorArea.fromCorners(first, position)
                val index = nbt.getInt("SlotIndex")
                if (!area.valid(origin) || index !in 0..block.data.spectatorAreas.size || index >= StadiumSpectatorArea.MAX_AREAS) {
                    player.displayClientMessage(lang("stadium.wand.invalid_bleachers", StadiumSpectatorArea.MAX_AREAS), true)
                    return InteractionResult.FAIL
                }
                if (index == block.data.spectatorAreas.size) block.data.spectatorAreas.add(area) else block.data.spectatorAreas[index] = area
            } else {
                val area = StadiumSetup.area(first, position)
                val error = StadiumSetup.areaError(block.data, area, origin)
                if (error != null) { player.displayClientMessage(error, true); return InteractionResult.FAIL }
                block.data.arena = area
            }
            nbt.remove("FirstCorner")
        } else {
            val slot = target(nbt)
            val error = StadiumSetup.placementError(block.data, layoutType, slot, position, origin)
            if (error != null) { player.displayClientMessage(error, true); return InteractionResult.FAIL }
            if (block.data.layout(layoutType) == null) block.data.setLayout(layoutType, StadiumBlockLayout.createDefault())
            val placements = slot.placements(block.data, layoutType)!!
            val placement = StadiumBlockPlacement(position)
            if (slot.index == placements.size) placements.add(placement) else placements[slot.index] = placement
        }
        block.markUpdated()
        if (block.data.isConfigured) block.registerDynamic()
        if (nbt.getBoolean("Guided")) {
            val next = StadiumSetup.firstMissing(block.data, layoutType)
            if (next == null) {
                nbt.putBoolean("Guided", false)
                nbt.putString("SlotType", "complete")
                openConsole(serverPlayer, block, nbt)
            } else {
                setTarget(nbt, next)
                player.displayClientMessage(lang("stadium.wand.next_slot", next.label), true)
            }
        } else if (nbt.getString("SlotType") == "arena" || bleachers) {
            openConsole(serverPlayer, block, nbt)
        } else player.displayClientMessage(lang("stadium.wand.slot_saved", target(nbt).label), true)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
        return InteractionResult.SUCCESS
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        val nbt = getWandData(stack) ?: return InteractionResultHolder.pass(stack)
        if (!level.isClientSide && player is ServerPlayer && player.isShiftKeyDown) {
            stadium(player, nbt)?.let { block ->
                nbt.remove("FirstCorner")
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
                openConsole(player, block, nbt)
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    private fun stadium(player: ServerPlayer, nbt: CompoundTag): StadiumBlockEntity? {
        val pos = stadiumPos(nbt)
        if (player.blockPosition().distSqr(pos) > 10000 || !player.level().hasChunk(pos.x shr 4, pos.z shr 4)) return null
        if (nbt.getString("Dimension") != player.level().dimension().location().toString()) return null
        val block = player.level().getBlockEntity(pos) as? StadiumBlockEntity ?: return null
        if (!nbt.hasUUID("StadiumId") || nbt.getUUID("StadiumId") != block.data.stadiumId) return null
        return block.takeIf { it.data.ownerUUID == player.uuid || player.hasPermissions(2) }
    }

    private fun openConsole(player: ServerPlayer, block: StadiumBlockEntity, nbt: CompoundTag) {
        player.sendPacket(OpenStadiumConfigPacket(block.blockPos, block.data.toNbt(), initialTab = 1,
            initialLayout = nbt.getString("LayoutType")))
    }

    override fun isFoil(stack: ItemStack): Boolean = true

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide || entity !is Player) return
        val nbt = getWandData(stack) ?: return

        val stadiumPos = BlockPos(nbt.getInt("StadiumX"), nbt.getInt("StadiumY"), nbt.getInt("StadiumZ"))

        if (isSelected && level.gameTime % 30L == 0L) entity.displayClientMessage(
            if (entity.blockPosition().distSqr(stadiumPos) > 10000) lang("stadium.wand.return_to_stadium") else hint(nbt), true)
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

        fun getWandData(stack: ItemStack): CompoundTag? {
            if (stack.item !is StadiumPlacementWandItem) return null
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
            return customData.copyTag()
        }

        fun stadiumPos(nbt: CompoundTag) = BlockPos(nbt.getInt("StadiumX"), nbt.getInt("StadiumY"), nbt.getInt("StadiumZ"))
        fun target(nbt: CompoundTag) = StadiumSetup.Slot(nbt.getInt("SideIndex"), nbt.getString("SlotType"), nbt.getInt("SlotIndex"))
        fun firstCorner(nbt: CompoundTag): Vec3? = if (nbt.contains("FirstCorner")) nbt.getCompound("FirstCorner").let {
            Vec3(it.getDouble("X"), it.getDouble("Y"), it.getDouble("Z"))
        } else null

        fun setTarget(nbt: CompoundTag, target: StadiumSetup.Slot) {
            nbt.putInt("SideIndex", target.side); nbt.putString("SlotType", target.type); nbt.putInt("SlotIndex", target.index)
        }

        fun hint(nbt: CompoundTag): Component = when (nbt.getString("SlotType")) {
            "bleachers" -> if (firstCorner(nbt) == null) lang("stadium.wand.bleachers_first") else lang("stadium.wand.bleachers_second")
            "arena" -> if (firstCorner(nbt) == null) lang("stadium.wand.arena_first") else lang("stadium.wand.arena_second")
            "complete" -> lang("stadium.wand.complete")
            else -> lang("stadium.wand.place_slot", target(nbt).label)
        }
    }
}
