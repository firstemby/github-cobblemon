/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.modificationstation

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.api.modification.ModificationVariants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.DataSlot
import net.minecraft.world.inventory.ResultContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class ModificationStationMenu(
    containerId: Int,
    playerInventory: Inventory,
    private val access: ContainerLevelAccess
) : AbstractContainerMenu(CobblemonMenuType.MODIFICATION_STATION, containerId) {

    private val selectedOutputIndex = DataSlot.standalone()
    private var outputs: List<ItemStack> = emptyList()
    private var lastInput: ItemStack = ItemStack.EMPTY
    private var lastSoundTime: Long = 0L
    private var slotUpdateListener: Runnable = Runnable {}

    val container: Container = object : SimpleContainer(1) {
        override fun setChanged() {
            super.setChanged()
            this@ModificationStationMenu.slotsChanged(this)
            slotUpdateListener.run()
        }
    }

    private val resultContainer = ResultContainer()
    val inputSlot: Slot
    val resultSlot: Slot

    init {
        inputSlot = addSlot(object : Slot(container, INPUT_SLOT, 20, 33) {
            override fun mayPlace(stack: ItemStack): Boolean = ModificationVariants.hasVariantsFor(stack)
        })

        resultSlot = addSlot(object : Slot(resultContainer, RESULT_SLOT, 143, 33) {
            override fun mayPlace(stack: ItemStack): Boolean = false

            override fun onTake(player: Player, stack: ItemStack) {
                stack.onCraftedBy(player.level(), player, stack.count)
                inputSlot.remove(1)

                if (inputSlot.hasItem()) {
                    setupResultSlot()
                } else {
                    selectedOutputIndex.set(-1)
                    this.set(ItemStack.EMPTY)
                }

                access.execute { level, pos ->
                    val gameTime = level.gameTime
                    if (lastSoundTime != gameTime) {
                        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F)
                        lastSoundTime = gameTime
                    }
                }

                super.onTake(player, stack)
            }
        })

        for (row in 0 until 3) {
            for (column in 0 until 9) {
                addSlot(Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18))
            }
        }

        for (hotbarSlot in 0 until 9) {
            addSlot(Slot(playerInventory, hotbarSlot, 8 + hotbarSlot * 18, 142))
        }

        addDataSlot(selectedOutputIndex)
    }

    constructor(containerId: Int, playerInventory: Inventory) : this(containerId, playerInventory, ContainerLevelAccess.NULL)

    fun getSelectedOutputIndex(): Int = selectedOutputIndex.get()

    fun getOutputs(): List<ItemStack> = outputs

    fun hasInputItem(): Boolean = inputSlot.hasItem() && outputs.isNotEmpty()

    fun registerUpdateListener(listener: Runnable) {
        slotUpdateListener = listener
    }

    override fun stillValid(player: Player): Boolean = stillValid(access, player, CobblemonBlocks.MODIFICATION_STATION)

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (isValidOutputIndex(id)) {
            selectedOutputIndex.set(id)
            setupResultSlot()
        }
        return true
    }

    override fun slotsChanged(container: Container) {
        val inputStack = inputSlot.item
        if (!ItemStack.isSameItemSameComponents(inputStack, lastInput)) {
            lastInput = inputStack.copy()
            setupOutputs(inputStack)
        }
    }

    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean {
        return slot.container !== resultContainer && super.canTakeItemForPickAll(stack, slot)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (!slot.hasItem()) {
            return ItemStack.EMPTY
        }
        val movedStack = slot.item
        val copiedStack = movedStack.copy()

        val wasMoved = when {
            index == RESULT_SLOT -> {
                movedStack.item.onCraftedBy(movedStack, player.level(), player)
                moveItemStackTo(movedStack, INV_SLOT_START, USE_ROW_SLOT_END, true).also { if (it) slot.onQuickCraft(movedStack, copiedStack) }
            }
            index == INPUT_SLOT -> moveItemStackTo(movedStack, INV_SLOT_START, USE_ROW_SLOT_END, false)
            ModificationVariants.hasVariantsFor(movedStack) -> moveItemStackTo(movedStack, INPUT_SLOT, RESULT_SLOT, false)
            index in INV_SLOT_START until INV_SLOT_END -> moveItemStackTo(movedStack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)
            index in USE_ROW_SLOT_START until USE_ROW_SLOT_END -> moveItemStackTo(movedStack, INV_SLOT_START, INV_SLOT_END, false)
            else -> false
        }
        if (!wasMoved) {
            return ItemStack.EMPTY
        }

        if (movedStack.isEmpty) {
            slot.setByPlayer(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }
        if (movedStack.count == copiedStack.count) {
            return ItemStack.EMPTY
        }

        slot.onTake(player, movedStack)
        broadcastChanges()
        return copiedStack
    }

    override fun removed(player: Player) {
        super.removed(player)
        resultContainer.removeItemNoUpdate(1)
        access.execute { _, _ -> clearContainer(player, container) }
    }

    private fun isValidOutputIndex(index: Int): Boolean = index in outputs.indices

    private fun setupOutputs(inputStack: ItemStack) {
        outputs = emptyList()
        selectedOutputIndex.set(-1)
        resultSlot.set(ItemStack.EMPTY)
        if (inputStack.isEmpty) {
            return
        }

        val parent = ModificationVariants.parentOf(inputStack)
        val variantOutputs = ModificationVariants.forParent(parent).map { it.createStack() }.filterNot { it.isEmpty }
        if (variantOutputs.isEmpty()) {
            return
        }
        outputs = (listOfNotNull(stockOutputFor(parent)) + variantOutputs)
            .filterNot { ItemStack.isSameItemSameComponents(it, inputStack) }
    }

    private fun stockOutputFor(parent: ResourceLocation): ItemStack? = BuiltInRegistries.ITEM.getOptional(parent).map(::ItemStack).orElse(null)

    private fun setupResultSlot() {
        val selectedIndex = selectedOutputIndex.get()
        resultSlot.set(if (isValidOutputIndex(selectedIndex)) outputs[selectedIndex].copy() else ItemStack.EMPTY)
        broadcastChanges()
    }

    companion object {
        const val INPUT_SLOT = 0
        const val RESULT_SLOT = 1
        private const val INV_SLOT_START = 2
        private const val INV_SLOT_END = 29
        private const val USE_ROW_SLOT_START = 29
        private const val USE_ROW_SLOT_END = 38
    }
}