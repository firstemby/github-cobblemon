/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.block.PhotoPrinterBlock
import com.cobblemon.mod.common.block.photoprinter.PhotoPrinterMenu
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState

class PhotoPrinterBlockEntity(pos: BlockPos, state: BlockState) : BaseContainerBlockEntity(CobblemonBlockEntities.PHOTO_PRINTER, pos, state) {
    companion object {
        const val INPUT_SLOT = 0
        const val RESULT_SLOT = 1
        const val SLOT_COUNT = 2
        const val PRINT_DURATION_TICKS = 120

        const val POSITION_X_INDEX = 0
        const val POSITION_Y_INDEX = 1
        const val POSITION_Z_INDEX = 2
        const val PRINT_PROGRESS_INDEX = 3
        const val PRINT_PROGRESS_MAX_INDEX = 4
        const val PRINTING_INDEX = 5

        private const val SELECTED_PHOTO_TAG = "selectedPhotoId"
        private const val PRINT_PROGRESS_TAG = "printProgress"
        private const val PRINT_PROGRESS_MAX_TAG = "printProgressMax"
        private const val PRINTING_STACK_TAG = "printingStack"

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PhotoPrinterBlockEntity) {
            if (blockEntity.printingStack.isEmpty) return

            if (level.isClientSide) {
                if (blockEntity.printProgress < blockEntity.printProgressMax) {
                    blockEntity.printProgress++
                }
                return
            }

            if (blockEntity.printProgress < blockEntity.printProgressMax) {
                blockEntity.printProgress++
                blockEntity.setChanged()
                return
            }

            if (!blockEntity.getItem(RESULT_SLOT).isEmpty) return

            blockEntity.setItem(RESULT_SLOT, blockEntity.printingStack.copy())
            blockEntity.printingStack = ItemStack.EMPTY
            blockEntity.printProgress = 0
            blockEntity.updatePrinterBlockState()
            blockEntity.setChanged()
            blockEntity.syncToClient()
        }
    }

    private var items: NonNullList<ItemStack> = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
    var selectedPhotoId: String? = null
    var printProgress: Int = 0
    var printProgressMax: Int = PRINT_DURATION_TICKS
    var printingStack: ItemStack = ItemStack.EMPTY
    val isPrinting: Boolean
        get() = !printingStack.isEmpty

    val containerData: ContainerData = object : ContainerData {
        override fun getCount(): Int = 6

        override fun get(index: Int): Int {
            return when (index) {
                POSITION_X_INDEX -> blockPos.x
                POSITION_Y_INDEX -> blockPos.y
                POSITION_Z_INDEX -> blockPos.z
                PRINT_PROGRESS_INDEX -> printProgress
                PRINT_PROGRESS_MAX_INDEX -> printProgressMax
                PRINTING_INDEX -> if (isPrinting) 1 else 0
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                PRINT_PROGRESS_INDEX -> printProgress = value
                PRINT_PROGRESS_MAX_INDEX -> printProgressMax = value
            }
        }
    }

    fun startPrinting(stack: ItemStack, durationTicks: Int = PRINT_DURATION_TICKS): Boolean {
        if (stack.isEmpty || isPrinting || !getItem(RESULT_SLOT).isEmpty) return false

        printingStack = stack.copy()
        printProgress = 0
        printProgressMax = durationTicks.coerceAtLeast(1)
        updatePrinterBlockState()
        setChanged()
        syncToClient()
        return true
    }

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu {
        return PhotoPrinterMenu(containerId, inventory, this, containerData)
    }

    override fun getDefaultName(): Component = Component.translatable("cobblemon.container.photo_printer")

    override fun getDisplayName(): Component = Component.translatable("block.cobblemon.photo_printer")

    override fun getItems(): NonNullList<ItemStack> = items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun getContainerSize(): Int = SLOT_COUNT

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == INPUT_SLOT

    override fun setItem(slot: Int, stack: ItemStack) {
        super.setItem(slot, stack)
        if (slot == RESULT_SLOT) {
            updatePrinterBlockState()
            syncToClient()
        }
    }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val stack = super.removeItem(slot, amount)
        if (slot == RESULT_SLOT && !stack.isEmpty) {
            updatePrinterBlockState()
            syncToClient()
        }
        return stack
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        val stack = super.removeItemNoUpdate(slot)
        if (slot == RESULT_SLOT && !stack.isEmpty) {
            updatePrinterBlockState()
            syncToClient()
        }
        return stack
    }

    override fun saveAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(compound, registries)
        ContainerHelper.saveAllItems(compound, items, registries)
        selectedPhotoId?.let { compound.putString(SELECTED_PHOTO_TAG, it) }
        compound.putInt(PRINT_PROGRESS_TAG, printProgress)
        compound.putInt(PRINT_PROGRESS_MAX_TAG, printProgressMax)
        if (!printingStack.isEmpty) {
            compound.put(PRINTING_STACK_TAG, printingStack.save(registries))
        }
    }

    override fun loadAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(compound, registries)
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(compound, items, registries)
        selectedPhotoId = compound.getString(SELECTED_PHOTO_TAG).takeIf { it.isNotEmpty() }
        printProgress = compound.getInt(PRINT_PROGRESS_TAG)
        printProgressMax = compound.getInt(PRINT_PROGRESS_MAX_TAG).takeIf { it > 0 } ?: PRINT_DURATION_TICKS
        printingStack = if (compound.contains(PRINTING_STACK_TAG)) {
            ItemStack.parseOptional(registries, compound.getCompound(PRINTING_STACK_TAG))
        } else {
            ItemStack.EMPTY
        }
    }

    override fun stillValid(player: Player): Boolean {
        return super.stillValid(player)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }

    private fun syncToClient() {
        val currentLevel = level ?: return
        currentLevel.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    private fun updatePrinterBlockState() {
        val currentLevel = level ?: return
        val state = blockState
        if (state.block !is PhotoPrinterBlock || !state.hasProperty(PhotoPrinterBlock.STATE)) return

        val visualState = if (isPrinting || !getItem(RESULT_SLOT).isEmpty) {
            PhotoPrinterBlock.PrinterState.ON
        } else {
            PhotoPrinterBlock.PrinterState.OFF
        }
        if (state.getValue(PhotoPrinterBlock.STATE) != visualState) {
            currentLevel.setBlock(blockPos, state.setValue(PhotoPrinterBlock.STATE, visualState), Block.UPDATE_CLIENTS)
        }

        val topPos = blockPos.above()
        val topState = currentLevel.getBlockState(topPos)
        if (
            topState.block is PhotoPrinterBlock &&
            topState.hasProperty(PhotoPrinterBlock.PART) &&
            topState.hasProperty(PhotoPrinterBlock.STATE) &&
            topState.getValue(PhotoPrinterBlock.PART) == PhotoPrinterBlock.PrinterPart.TOP &&
            topState.getValue(PhotoPrinterBlock.STATE) != visualState
        ) {
            currentLevel.setBlock(topPos, topState.setValue(PhotoPrinterBlock.STATE, visualState), Block.UPDATE_CLIENTS)
        }
    }
}
