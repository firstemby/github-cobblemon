/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.multiblock.MultiblockEntity
import com.cobblemon.mod.common.api.multiblock.MultiblockStructure
import com.cobblemon.mod.common.api.multiblock.builder.MultiblockStructureBuilder
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.block.MonitorBlock
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.tmList
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtUtils
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.gameevent.GameEvent

open class FossilMultiblockEntity(
    pos: BlockPos,
    state: BlockState,
    multiblockBuilder: MultiblockStructureBuilder,
    type: BlockEntityType<*> = CobblemonBlockEntities.FOSSIL_MULTIBLOCK
) : MultiblockEntity(type, pos, state, multiblockBuilder) {
    private val musicDiscs = setOf(
        Items.MUSIC_DISC_13,
        Items.MUSIC_DISC_CAT,
        Items.MUSIC_DISC_BLOCKS,
        Items.MUSIC_DISC_CHIRP,
        Items.MUSIC_DISC_FAR,
        Items.MUSIC_DISC_MALL,
        Items.MUSIC_DISC_MELLOHI,
        Items.MUSIC_DISC_STAL,
        Items.MUSIC_DISC_STRAD,
        Items.MUSIC_DISC_WARD,
        Items.MUSIC_DISC_11,
        Items.MUSIC_DISC_WAIT,
        Items.MUSIC_DISC_OTHERSIDE,
        Items.MUSIC_DISC_5,
        Items.MUSIC_DISC_RELIC,
        Items.MUSIC_DISC_PRECIPICE,
        Items.MUSIC_DISC_CREATOR,
        Items.MUSIC_DISC_CREATOR_MUSIC_BOX,
        Items.MUSIC_DISC_PIGSTEP
    )

    override var masterBlockPos: BlockPos? = null
    private var diskStack: ItemStack = ItemStack.EMPTY

    override var multiblockStructure: MultiblockStructure? = null
        set(structure) {
            field = structure
            if (structure != null) {
                masterBlockPos = structure.controllerBlockPos
            }
        }
        get() {
            if(masterBlockPos != null && masterBlockPos != blockPos) {
                val chunkPos = ChunkPos(masterBlockPos)
                if (level?.chunkSource?.hasChunk(chunkPos.x, chunkPos.z) == true) {
                    val entity: FossilMultiblockEntity? = level?.getBlockEntity(masterBlockPos) as? FossilMultiblockEntity?
                    field = entity?.multiblockStructure
                }
            }
            return field
        }

    override fun setRemoved() {
        super.setRemoved()
        if (this.multiblockStructure != null && level != null) {
            this.multiblockStructure!!.setRemoved(level!!)
        }
    }

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        val oldMultiblockStructure = this.multiblockStructure as? FossilMultiblockStructure
        multiblockStructure = if (nbt.contains(DataKeys.MULTIBLOCK_STORAGE)) {
            if (oldMultiblockStructure?.fossilState != null) {
                // Copy the fossilState's previous animation time to the new instance
                // Otherwise the fetus animation gets interrupted on every block update
                val animAge = oldMultiblockStructure.fossilState.peekAge() // If someone knows a better way to fetch the age, please do.
                val partialTicks = oldMultiblockStructure.fossilState.getPartialTicks()
                FossilMultiblockStructure.fromNbt(nbt.getCompound(DataKeys.MULTIBLOCK_STORAGE), registryLookup, animAge, partialTicks)
            } else {
                FossilMultiblockStructure.fromNbt(nbt.getCompound(DataKeys.MULTIBLOCK_STORAGE), registryLookup)
            }
        } else {
            null
        }
        masterBlockPos = if (nbt.contains(DataKeys.CONTROLLER_BLOCK)) {
            NbtUtils.readBlockPos(nbt, DataKeys.CONTROLLER_BLOCK).get()
        } else {
            null
        }
        diskStack = if (nbt.contains(DataKeys.MONITOR_DISK)) {
            ItemStack.parse(registryLookup, nbt.get(DataKeys.MONITOR_DISK)).orElse(ItemStack.EMPTY)
        } else {
            ItemStack.EMPTY
        }
        updateMonitorScreen()
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        if (!diskStack.isEmpty) {
            nbt.put(DataKeys.MONITOR_DISK, ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, diskStack).orThrow)
        }
    }

    fun handleUseItem(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand
    ): ItemInteractionResult {
        val handStack = player.getItemInHand(hand)
        if (!isValidDisk(handStack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        if (multiblockStructure != null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        if (level.isClientSide) return ItemInteractionResult.SUCCESS

        val newDisk = handStack.copyWithCount(1)
        // In creative, prevent duplicate ejection spam when repeatedly inserting the same disk.
        if (player.isCreative && !diskStack.isEmpty && ItemStack.isSameItemSameComponents(diskStack, newDisk)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide)
        }

        val oldStack = diskStack
        if (!oldStack.isEmpty) {
            if (player is ServerPlayer) {
                unlockTmForPlayer(player, oldStack)
            }
            ejectDiskStack(level, pos, state, oldStack)
        }

        diskStack = newDisk
        if (!player.isCreative) {
            handStack.shrink(1)
        }

        if (player is ServerPlayer) {
            unlockTmForPlayer(player, diskStack)
        }

        updateMonitorScreen()
        markUpdated(level, pos, state)

        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }

    fun handleUseWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player
    ): InteractionResult {
        if (diskStack.isEmpty) return InteractionResult.PASS
        if (multiblockStructure != null) return InteractionResult.PASS

        if (player is ServerPlayer) {
            unlockTmForPlayer(player, diskStack)
        }
        ejectDiskStack(level, pos, state, diskStack)
        diskStack = ItemStack.EMPTY
        updateMonitorScreen()
        markUpdated(level, pos, state)

        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    fun dropDisk(level: Level, pos: BlockPos, state: BlockState) {
        if (diskStack.isEmpty) return
        ejectDiskStack(level, pos, state, diskStack)
        diskStack = ItemStack.EMPTY
    }

    private fun ejectDiskStack(level: Level, pos: BlockPos, state: BlockState, stack: ItemStack) {
        val facing = if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state.getValue(HorizontalDirectionalBlock.FACING)
        } else {
            null
        }
        if (facing == null) {
            Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack)
            return
        }

        val ejectFacing = facing.opposite
        val x = pos.x + 0.5 + ejectFacing.stepX * 0.6
        val y = pos.y + 0.5
        val z = pos.z + 0.5 + ejectFacing.stepZ * 0.6
        val itemEntity = ItemEntity(level, x, y, z, stack)
        itemEntity.setDeltaMovement(ejectFacing.stepX * 0.15, 0.05, ejectFacing.stepZ * 0.15)
        level.addFreshEntity(itemEntity)
    }

    private fun isValidDisk(stack: ItemStack): Boolean {
        return stack.item is TechnicalMachineItem || musicDiscs.contains(stack.item)
    }

    private fun unlockTmForPlayer(player: ServerPlayer, stack: ItemStack) {
        if (stack.item !is TechnicalMachineItem) return
        val move = TMMoveComponent.getTMMove(stack) ?: return
        val tmId = TechnicalMachines.moveToTM[move]?.id ?: return
        player.tmList()?.learn(listOf(tmId))
    }

    private fun updateMonitorScreen() {
        val level = level ?: return
        if (level.isClientSide) return
        if (multiblockStructure != null) return

        val state = blockState
        if (!state.hasProperty(MonitorBlock.SCREEN)) return
        val targetScreen = getDiskScreen()
        if (state.getValue(MonitorBlock.SCREEN) != targetScreen) {
            level.setBlockAndUpdate(blockPos, state.setValue(MonitorBlock.SCREEN, targetScreen))
        }
    }

    private fun getDiskScreen(): MonitorBlock.MonitorScreen {
        if (diskStack.isEmpty) return MonitorBlock.MonitorScreen.OFF
        if (musicDiscs.contains(diskStack.item)) return MonitorBlock.MonitorScreen.MUSIC
        val move = TMMoveComponent.getTMMove(diskStack) ?: return MonitorBlock.MonitorScreen.TM_NORMAL
        return when (move.elementalType) {
            ElementalTypes.FIRE -> MonitorBlock.MonitorScreen.TM_FIRE
            ElementalTypes.WATER -> MonitorBlock.MonitorScreen.TM_WATER
            ElementalTypes.GRASS -> MonitorBlock.MonitorScreen.TM_GRASS
            ElementalTypes.ELECTRIC -> MonitorBlock.MonitorScreen.TM_ELECTRIC
            ElementalTypes.ICE -> MonitorBlock.MonitorScreen.TM_ICE
            ElementalTypes.FIGHTING -> MonitorBlock.MonitorScreen.TM_FIGHTING
            ElementalTypes.POISON -> MonitorBlock.MonitorScreen.TM_POISON
            ElementalTypes.GROUND -> MonitorBlock.MonitorScreen.TM_GROUND
            ElementalTypes.FLYING -> MonitorBlock.MonitorScreen.TM_FLYING
            ElementalTypes.PSYCHIC -> MonitorBlock.MonitorScreen.TM_PSYCHIC
            ElementalTypes.BUG -> MonitorBlock.MonitorScreen.TM_BUG
            ElementalTypes.ROCK -> MonitorBlock.MonitorScreen.TM_ROCK
            ElementalTypes.GHOST -> MonitorBlock.MonitorScreen.TM_GHOST
            ElementalTypes.DRAGON -> MonitorBlock.MonitorScreen.TM_DRAGON
            ElementalTypes.DARK -> MonitorBlock.MonitorScreen.TM_DARK
            ElementalTypes.STEEL -> MonitorBlock.MonitorScreen.TM_STEEL
            ElementalTypes.FAIRY -> MonitorBlock.MonitorScreen.TM_FAIRY
            else -> MonitorBlock.MonitorScreen.TM_NORMAL
        }
    }

    private fun markUpdated(level: Level, pos: BlockPos, state: BlockState) {
        level.sendBlockUpdated(pos, state, state, 3)
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state))
        setChanged()
    }

}
