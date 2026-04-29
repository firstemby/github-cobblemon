/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.DiscShelfBlock
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.gameevent.GameEvent
import java.util.OptionalInt
import kotlin.math.pow

class DiscShelfBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.DISC_SHELF, pos, state), WorldlyContainer {
    val items: NonNullList<ItemStack> = NonNullList.withSize(14, ItemStack.EMPTY)
    var lastInteractedSlot: Int = -1
    private val accessibleSlots = IntArray(14) { it }
    private var lastNotePulseTick: Long = Long.MIN_VALUE

    fun handleUseItem(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): ItemInteractionResult {
        if (stack.isEmpty) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        val slot = getHitSlot(hit, state).orElse(-1)
        if (slot !in 0 until 14) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        if (!isValidItem(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        val oldStack = items[slot]
        if (!oldStack.isEmpty) {
            if (!player.addItem(oldStack)) {
                Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, oldStack)
            }
        }

        items[slot] = stack.copyWithCount(1)
        if (!player.isCreative) {
            stack.shrink(1)
        }
        lastInteractedSlot = slot
        updateBlockState(level, pos)
        markUpdated()

        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }

    fun handleUseWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val slot = getHitSlot(hit, state).orElse(-1)
        if (slot !in 0 until 14 || items[slot].isEmpty) return InteractionResult.PASS

        val removed = items[slot]
        items[slot] = ItemStack.EMPTY
        if (!player.addItem(removed)) {
            Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, removed)
        }

        lastInteractedSlot = slot
        updateBlockState(level, pos)

        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    private fun updateBlockState(level: Level, pos: BlockPos) {
        val newState = DiscShelfBlock.SLOT_OCCUPIED_PROPERTIES.fold(blockState) { acc, prop ->
            val index = DiscShelfBlock.SLOT_OCCUPIED_PROPERTIES.indexOf(prop)
            acc.setValue(prop, !items[index].isEmpty)
        }
        level.setBlock(pos, newState, 3)
        level.updateNeighbourForOutputSignal(pos, newState.block)
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(newState))
    }

    private fun getHitSlot(hit: BlockHitResult, state: BlockState): OptionalInt {
        val facing = state.getValue(DiscShelfBlock.FACING)
        if (hit.direction != facing) {
            return OptionalInt.empty()
        }

        val relative = hit.location.subtract(hit.blockPos.x.toDouble(), hit.blockPos.y.toDouble(), hit.blockPos.z.toDouble())

        val (x, y) = when (facing) {
            // Face-local X where 0 = visual left and 1 = visual right.
            Direction.NORTH -> 1.0 - relative.x to relative.y
            Direction.SOUTH -> relative.x to relative.y
            Direction.WEST  -> 1.0 - relative.z to relative.y
            Direction.EAST  -> relative.z to relative.y
            else -> return OptionalInt.empty()
        }

        // Have interaction segments match the renderer areas visually
        val leftSlotMinX = 1.0 / 16.0
        val leftSlotMaxX = 7.0 / 16.0
        val rightSlotMinX = 9.0 / 16.0
        val rightSlotMaxX = 15.0 / 16.0
        val slotMinY = 1.0 / 16.0
        val slotMaxY = 15.0 / 16.0
        val slotHeight = 2.0 / 16.0

        val col = when {
            x in leftSlotMinX..leftSlotMaxX -> 0
            x in rightSlotMinX..rightSlotMaxX -> 1
            else -> return OptionalInt.empty()
        }

        if (y < slotMinY || y > slotMaxY) {
            return OptionalInt.empty()
        }

        val rowFromBottom = ((y - slotMinY) / slotHeight).toInt().coerceIn(0, 6)
        val row = 6 - rowFromBottom

        return OptionalInt.of(row * 2 + col)
    }

    fun isValidItem(stack: ItemStack): Boolean {
        return stack.item is TechnicalMachineItem
                || stack.item == CobblemonItems.UPGRADE
                || stack.item == CobblemonItems.DUBIOUS_DISC
                || stack.item == Items.MUSIC_DISC_13
                || stack.item == Items.MUSIC_DISC_CAT
                || stack.item == Items.MUSIC_DISC_BLOCKS
                || stack.item == Items.MUSIC_DISC_CHIRP
                || stack.item == Items.MUSIC_DISC_FAR
                || stack.item == Items.MUSIC_DISC_MALL
                || stack.item == Items.MUSIC_DISC_MELLOHI
                || stack.item == Items.MUSIC_DISC_STAL
                || stack.item == Items.MUSIC_DISC_STRAD
                || stack.item == Items.MUSIC_DISC_WARD
                || stack.item == Items.MUSIC_DISC_11
                || stack.item == Items.MUSIC_DISC_WAIT
                || stack.item == Items.MUSIC_DISC_OTHERSIDE
                || stack.item == Items.MUSIC_DISC_5
                || stack.item == Items.MUSIC_DISC_RELIC
                || stack.item == Items.MUSIC_DISC_PRECIPICE
                || stack.item == Items.MUSIC_DISC_CREATOR
                || stack.item == Items.MUSIC_DISC_CREATOR_MUSIC_BOX
                || stack.item == Items.MUSIC_DISC_PIGSTEP
    }

    fun onNoteBlockPulse(level: Level, noteBlockPos: BlockPos, soundEvent: SoundEvent) {
        val gameTime = level.gameTime
        if (lastNotePulseTick == gameTime) {
            return
        }
        lastNotePulseTick = gameTime

        val octaveUp = !items[1].isEmpty
        val octaveDown = !items[12].isEmpty
        val octaveShift = when {
            octaveUp && !octaveDown -> 12
            octaveDown && !octaveUp -> -12
            else -> 0
        }

        NOTE_SLOT_TO_SEMITONE.forEach { (slot, semitone) ->
            if (items[slot].isEmpty) return@forEach
            val pitch = 2.0.pow((semitone + octaveShift) / 12.0).toFloat()
            level.playSound(null, noteBlockPos, soundEvent, SoundSource.BLOCKS, 3.0f, pitch)
        }
    }

    fun markUpdated() {
        level?.setBlock(blockPos, blockState, 3)
        level?.sendBlockUpdated(blockPos, blockState, blockState, 3)
        setChanged()
    }

    override fun clearContent() {
        var changed = false
        for (index in items.indices) {
            if (!items[index].isEmpty) {
                items[index] = ItemStack.EMPTY
                changed = true
            }
        }
        if (changed) {
            level?.let { updateBlockState(it, blockPos) }
            markUpdated()
        }
    }

    override fun getContainerSize(): Int = items.size

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack {
        if (slot !in items.indices) return ItemStack.EMPTY
        return items[slot]
    }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val removed = ContainerHelper.removeItem(items, slot, amount)
        if (!removed.isEmpty) {
            if (items[slot].isEmpty) {
                lastInteractedSlot = slot
            }
            level?.let { updateBlockState(it, blockPos) }
            markUpdated()
        }
        return removed
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        val removed = ContainerHelper.takeItem(items, slot)
        if (!removed.isEmpty) {
            level?.let { updateBlockState(it, blockPos) }
            markUpdated()
        }
        return removed
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot !in items.indices) return
        if (!stack.isEmpty && !isValidItem(stack)) return

        items[slot] = if (stack.isEmpty) ItemStack.EMPTY else stack.copyWithCount(1)
        lastInteractedSlot = slot
        level?.let { updateBlockState(it, blockPos) }
        markUpdated()
    }

    override fun stillValid(player: Player): Boolean {
        return level?.getBlockEntity(blockPos) === this &&
                player.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) <= 64.0
    }

    override fun getMaxStackSize(): Int = 1

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        return slot in items.indices && items[slot].isEmpty && isValidItem(stack)
    }

    override fun getSlotsForFace(side: Direction): IntArray = accessibleSlots

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, direction: Direction?): Boolean {
        return canPlaceItem(slot, stack)
    }

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean {
        return slot in items.indices
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        ContainerHelper.saveAllItems(tag, items, true, registries)
        tag.putInt("LastInteractedSlot", lastInteractedSlot)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        items.clear()
        ContainerHelper.loadAllItems(tag, items, registries)
        lastInteractedSlot = tag.getInt("LastInteractedSlot")
    }

    companion object {
        // C=0 chromatic semitone mapping.
        private val NOTE_SLOT_TO_SEMITONE = linkedMapOf(
            0 to 5,   // F
            2 to 4,   // E
            4 to 3,   // D#
            6 to 2,   // D
            8 to 1,   // C#
            10 to 0,  // C
            3 to 11,  // B
            5 to 10,  // A#
            7 to 9,   // A
            9 to 8,   // G#
            11 to 7,  // G
            13 to 6   // F#
        )
    }

}
