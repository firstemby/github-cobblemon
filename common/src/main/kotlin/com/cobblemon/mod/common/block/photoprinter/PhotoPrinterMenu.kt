/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.photoprinter

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.block.entity.PhotoPrinterBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class PhotoPrinterMenu(menuType: MenuType<*>, syncId: Int) : AbstractContainerMenu(menuType, syncId) {
    companion object {
        const val INPUT_SLOT_X = 24
        const val INPUT_SLOT_Y = 157
        const val OUTPUT_SLOT_X = 184
        const val OUTPUT_SLOT_Y = 157
        const val PLAYER_INVENTORY_Y = 188
        const val HOTBAR_Y = 246
    }

    var playerInventory: Inventory? = null
    var inventory: Container? = null
    var containerData: ContainerData? = null
    var blockEntity: PhotoPrinterBlockEntity? = null

    constructor(syncId: Int, playerInventory: Inventory) : this(CobblemonMenuType.PHOTO_PRINTER, syncId) {
        this.playerInventory = playerInventory
        this.inventory = SimpleContainer(PhotoPrinterBlockEntity.SLOT_COUNT)
        this.containerData = SimpleContainerData(6)
        this.blockEntity = inventory as? PhotoPrinterBlockEntity
        setupSlots(playerInventory)
    }

    constructor(syncId: Int, playerInventory: Inventory, inventory: Container, containerData: ContainerData) : this(CobblemonMenuType.PHOTO_PRINTER, syncId) {
        this.playerInventory = playerInventory
        this.inventory = inventory
        this.containerData = containerData
        this.blockEntity = inventory as? PhotoPrinterBlockEntity
        setupSlots(playerInventory)
    }

    private fun setupSlots(playerInventory: Inventory) {
        val inventory = inventory ?: return
        val data = containerData ?: return

        addDataSlots(data)
        inventory.startOpen(playerInventory.player)

        addSlot(InputSlot(inventory, PhotoPrinterBlockEntity.INPUT_SLOT, INPUT_SLOT_X, INPUT_SLOT_Y))
        addSlot(OutputSlot(inventory, PhotoPrinterBlockEntity.RESULT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y))

        for (row in 0..2) {
            for (column in 0..8) {
                addSlot(Slot(playerInventory, 9 + row * 9 + column, 8 + column * 18, PLAYER_INVENTORY_Y + row * 18))
            }
        }

        for (column in 0..8) {
            addSlot(Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y))
        }
    }

    fun blockPos(): BlockPos {
        val data = containerData ?: return BlockPos.ZERO
        return BlockPos(
            data.get(PhotoPrinterBlockEntity.POSITION_X_INDEX),
            data.get(PhotoPrinterBlockEntity.POSITION_Y_INDEX),
            data.get(PhotoPrinterBlockEntity.POSITION_Z_INDEX)
        )
    }

    fun printProgress(): Int = containerData?.get(PhotoPrinterBlockEntity.PRINT_PROGRESS_INDEX) ?: 0

    fun printProgressMax(): Int = containerData?.get(PhotoPrinterBlockEntity.PRINT_PROGRESS_MAX_INDEX) ?: PhotoPrinterBlockEntity.PRINT_DURATION_TICKS

    fun isPrinting(): Boolean = containerData?.get(PhotoPrinterBlockEntity.PRINTING_INDEX) == 1

    fun printProgressFraction(): Float {
        val max = printProgressMax().coerceAtLeast(1)
        return (printProgress().toFloat() / max.toFloat()).coerceIn(0F, 1F)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY

        val sourceStack = slot.item
        val sourceCopy = sourceStack.copy()
        val machineSlotsEnd = PhotoPrinterBlockEntity.SLOT_COUNT

        if (index < machineSlotsEnd) {
            if (!moveItemStackTo(sourceStack, machineSlotsEnd, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            if (sourceStack.`is`(Items.PAPER)) {
                if (!moveItemStackTo(sourceStack, PhotoPrinterBlockEntity.INPUT_SLOT, PhotoPrinterBlockEntity.INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index < machineSlotsEnd + 27) {
                if (!moveItemStackTo(sourceStack, machineSlotsEnd + 27, slots.size, false)) {
                    return ItemStack.EMPTY
                }
            } else if (!moveItemStackTo(sourceStack, machineSlotsEnd, machineSlotsEnd + 27, false)) {
                return ItemStack.EMPTY
            }
        }

        if (sourceStack.isEmpty) {
            slot.setByPlayer(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }

        return sourceCopy
    }

    override fun stillValid(player: Player): Boolean {
        return inventory?.stillValid(player) ?: false
    }

    override fun removed(player: Player) {
        super.removed(player)
        inventory?.stopOpen(player)
    }

    class InputSlot(container: Container, slot: Int, x: Int, y: Int) : Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.`is`(Items.PAPER)
    }

    class OutputSlot(container: Container, slot: Int, x: Int, y: Int) : Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
    }
}

