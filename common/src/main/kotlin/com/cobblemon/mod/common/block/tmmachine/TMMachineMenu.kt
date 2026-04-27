/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.tmmachine

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.util.itemRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack

class TMMachineMenu(menuType: MenuType<*>, syncId: Int) : AbstractContainerMenu(menuType, syncId), ContainerListener {
    companion object {
        const val RESULT_SLOT = 0
        const val BLANK_TM_SLOT = 1
        val INGREDIENT_SLOTS = 2..4
    }

    var playerInventory: Inventory? = null
    var tmMachineEntity: TMMachineBlockEntity? = null
    var inventory: Container? = null
    var containerData: ContainerData? = null

    constructor(syncId: Int, playerInventory: Inventory) : this(CobblemonMenuType.TM_MACHINE, syncId) {
        this.playerInventory = playerInventory
        this.inventory = SimpleContainer(5)
        this.containerData = SimpleContainerData(6)
        this.tmMachineEntity = (inventory as? TMMachineBlockEntity.TMMachineBlockInventory)?.blockEntity

        this.addDataSlots(containerData!!)
        inventory?.startOpen(playerInventory.player)

        val startX = 15
        val startY = 144
        val slotWidth = 18
        val slotHeight = 18

        for (row in 0..2) {
            for (col in 0..8) {
                this.addSlot(Slot(playerInventory, 9 + (row * 9) + col, startX + (slotWidth * col), startY + (slotHeight * row)))
            }
        }
        for (col in 0..8) {
            this.addSlot(Slot(playerInventory, col, startX + (slotWidth * col), startY + 58))
        }

        val container = inventory
        container?.let {
            this.addSlot(TMResultSlot(container, RESULT_SLOT, startX + 37, startY - 28)) // Output slot
            this.addSlot(BlankTMSlot(container, BLANK_TM_SLOT, startX + 90, startY - 28))  // Blank TM
            for (index in INGREDIENT_SLOTS) {
                this.addSlot(Slot(container, index, startX + 114 + (18 * (index - INGREDIENT_SLOTS.first)), startY - 28))
            }
        }
        this.addSlotListener(this)
    }

    constructor(syncId: Int, playerInventory: Inventory, inventory: Container, containerData: ContainerData) : this(CobblemonMenuType.TM_MACHINE, syncId) {
        this.playerInventory = playerInventory
        this.inventory = inventory
        this.containerData = containerData
        this.tmMachineEntity = (inventory as? TMMachineBlockEntity.TMMachineBlockInventory)?.blockEntity

        this.addDataSlots(containerData)
        inventory.startOpen(playerInventory.player)

        val startX = 15
        val startY = 144
        val slotWidth = 18
        val slotHeight = 18

        for (row in 0..2) {
            for (col in 0..8) {
                this.addSlot(Slot(playerInventory, 9 + (row * 9) + col, startX + (slotWidth * col), startY + (slotHeight * row)))
            }
        }
        for (col in 0..8) {
            this.addSlot(Slot(playerInventory, col, startX + (slotWidth * col), startY + 58))
        }

        val container = inventory
        container.let {
            this.addSlot(TMResultSlot(container, RESULT_SLOT, startX + 37, startY - 28)) // Output slot
            this.addSlot(BlankTMSlot(container, BLANK_TM_SLOT, startX + 90, startY - 28))  // Blank TM
            for (index in INGREDIENT_SLOTS) {
                this.addSlot(Slot(container, index, startX + 114 + (18 * (index - INGREDIENT_SLOTS.first)), startY - 28))
            }
        }
        this.addSlotListener(this)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var itemStack = ItemStack.EMPTY
        val slot = this.slots[index]
        val maxPlayerInventoryIndex = Inventory.INVENTORY_SIZE - 1

        if (slot.hasItem()) {
            val movedStack = slot.item
            itemStack = movedStack.copy()

            // If in container slots, move to player inventory
            // 36: Output slot, 37: Blank TM slot, 38-40: Ingredient slots
            if (index in 36..40) {
                if (!this.moveItemStackTo(movedStack, 0, maxPlayerInventoryIndex, false)) return ItemStack.EMPTY
            }
            // If in player inventory, move to container slots. 'endIndex' is non-inclusive
            else if (index <= maxPlayerInventoryIndex) {
                if (!this.moveItemStackTo(movedStack, 37, 40 + 1, false)) return ItemStack.EMPTY
            }

            if (movedStack.isEmpty) {
                slot.setByPlayer(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
        }
        return itemStack
    }

    override fun stillValid(player: Player): Boolean {
        return player.let { inventory?.stillValid(it) } ?: false
    }

    fun getBurnProgressValue(): Int = this.containerData?.get(TMMachineBlockEntity.BURN_PROGRESS_INDEX) ?: 0

    fun getBurnProgressRatio(): Float {
        val amount = getBurnProgressValue()
        val max = TMMachineBlockEntity.BURN_TOTAL_TIME
        return if (amount != 0) {
            Mth.clamp((amount.toFloat() / max.toFloat()), 0.0F, 1.0F)
        } else {
            0.0F
        }
    }

    fun getPostCraftTicks(): Int {
        return if (getBurnProgressValue() >= TMMachineBlockEntity.BURN_TOTAL_TIME) {
            getBurnProgressValue() - TMMachineBlockEntity.BURN_TOTAL_TIME
        } else 0
    }

    override fun removed(player: Player) {
        super.removed(player)
        tmMachineEntity?.setChanged()
    }

    class TMResultSlot(container: Container, slot: Int, x: Int, y: Int) : Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
    }

    class BlankTMSlot(container: Container, slot: Int, x: Int, y: Int) : Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = super.mayPlace(stack) && (stack.item == CobblemonItems.BLANK_TM)
    }

    override fun slotChanged(containerToSend: AbstractContainerMenu, dataSlotIndex: Int, stack: ItemStack) {
        val level = playerInventory?.player?.level()

        // If ingredient slots have been modified and level is server side and process is active
        // 36: Output slot, 37: Blank TM slot, 38-40: Ingredient slots
        if ((dataSlotIndex in 37 .. 40) && (level?.isClientSide == false) && (containerData?.get(TMMachineBlockEntity.BURN_ACTIVE_INDEX) == 1)) {
            // Stop process if there are not enough ingredients after slots have been modified
            var validCost = !(inventory?.getItem(BLANK_TM_SLOT)?.isEmpty ?: true)

            if (validCost) {
                Moves.getByName(tmMachineEntity?.activeMove ?: "")?.let { moveTemplate ->
                    TechnicalMachines.moveToTM[moveTemplate]?.let { tm ->
                        val recipes = tm.getClampedRecipe() ?: emptyList()
                        for ((index, recipe) in recipes.withIndex()) {
                            val slot = INGREDIENT_SLOTS.first + index
                            val provided = inventory?.getItem(slot) ?: ItemStack.EMPTY

                            if (!recipe.ingredient.test(provided) || provided.count < recipe.count) {
                                validCost = false
                                break
                            }
                        }
                    }
                }
            }

            if (!validCost) {
                containerData?.set(TMMachineBlockEntity.BURN_ACTIVE_INDEX, 0)
                containerData?.set(TMMachineBlockEntity.BURN_PROGRESS_INDEX, 0)
            }
        }

        tmMachineEntity?.setChanged()
        inventory?.setChanged()
        playerInventory?.setChanged()
        broadcastChanges()
    }

    override fun dataChanged(containerMenu: AbstractContainerMenu, dataSlotIndex: Int, value: Int) {
        broadcastChanges()
    }
}
