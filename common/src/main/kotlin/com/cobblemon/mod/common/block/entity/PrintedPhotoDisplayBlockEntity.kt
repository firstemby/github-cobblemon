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
import com.cobblemon.mod.common.item.components.PrintedPhotoComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class PrintedPhotoDisplayBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonBlockEntities.PRINTED_PHOTO_DISPLAY, pos, state) {

    companion object {
        private const val PRINTED_PHOTO_TAG = "printed_photo"
        private const val ROOT_POS_TAG = "root_pos"
        private const val GRID_SIZE_TAG = "grid_size"
        private const val CELL_X_TAG = "cell_x"
        private const val CELL_Y_TAG = "cell_y"
    }

    var component: PrintedPhotoComponent? = null
        private set
    var rootPos: BlockPos = blockPos
        private set
    var gridSize: Int = 1
        private set
    var cellX: Int = 0
        private set
    var cellY: Int = 0
        private set

    fun setComponent(component: PrintedPhotoComponent?) {
        this.component = component
        setChanged()
        val currentLevel = level ?: return
        currentLevel.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    fun setTileData(rootPos: BlockPos, gridSize: Int, cellX: Int, cellY: Int) {
        this.rootPos = rootPos
        this.gridSize = gridSize.coerceIn(1, 3)
        this.cellX = cellX
        this.cellY = cellY
        setChanged()
        val currentLevel = level ?: return
        currentLevel.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    fun createDroppedStack(): ItemStack {
        val value = component
        if (value == null) return ItemStack.EMPTY
        val stack = ItemStack(CobblemonItems.PRINTED_PHOTO)
        PrintedPhotoComponent.set(stack, value)
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.cobblemon.printed_photo.named", value.index))
        return stack
    }

    override fun saveAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(compound, registries)
        component?.let { value ->
            PrintedPhotoComponent.CODEC.encodeStart(NbtOps.INSTANCE, value).result().ifPresent {
                compound.put(PRINTED_PHOTO_TAG, it)
            }
        }
        compound.putLong(ROOT_POS_TAG, rootPos.asLong())
        compound.putInt(GRID_SIZE_TAG, gridSize)
        compound.putInt(CELL_X_TAG, cellX)
        compound.putInt(CELL_Y_TAG, cellY)
    }

    override fun loadAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(compound, registries)
        component = compound.get(PRINTED_PHOTO_TAG)?.let { tag ->
            PrintedPhotoComponent.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null)
        }
        rootPos = if (compound.contains(ROOT_POS_TAG)) {
            BlockPos.of(compound.getLong(ROOT_POS_TAG))
        } else {
            blockPos
        }
        gridSize = compound.getInt(GRID_SIZE_TAG).coerceIn(1, 3).let { if (it == 0) 1 else it }
        cellX = compound.getInt(CELL_X_TAG).coerceIn(0, 2)
        cellY = compound.getInt(CELL_Y_TAG).coerceIn(0, 2)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }
}
