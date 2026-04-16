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
import com.cobblemon.mod.common.api.modification.ModificationVariant
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
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

class ModificationStationMenu(
    containerId: Int,
    playerInventory: Inventory,
    private val access: ContainerLevelAccess
) : AbstractContainerMenu(CobblemonMenuType.MODIFICATION_STATION, containerId) {

    private val selectedVariantIndex = DataSlot.standalone()
    private val level: Level = playerInventory.player.level()
    private var variants: List<ModificationVariant> = emptyList()
    private var variantOutputs: List<ItemStack> = emptyList()
    private var input: ItemStack = ItemStack.EMPTY
    private var lastSoundTime: Long = 0L

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
    private var slotUpdateListener: Runnable = Runnable {}

    init {
        inputSlot = addSlot(object : Slot(container, INPUT_SLOT, 20, 33) {
            override fun mayPlace(stack: ItemStack): Boolean = hasVariantsFor(stack)
        })

        resultSlot = addSlot(object : Slot(resultContainer, RESULT_SLOT, 143, 33) {
            override fun mayPlace(stack: ItemStack): Boolean = false

            override fun onTake(player: Player, stack: ItemStack) {
                stack.onCraftedBy(player.level(), player, stack.count)
                inputSlot.remove(1)

                if (inputSlot.hasItem()) {
                    setupResultSlot()
                } else {
                    selectedVariantIndex.set(-1)
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
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }

        for (hotbar in 0 until 9) {
            addSlot(Slot(playerInventory, hotbar, 8 + hotbar * 18, 142))
        }

        addDataSlot(selectedVariantIndex)
    }

    constructor(containerId: Int, playerInventory: Inventory) : this(containerId, playerInventory, ContainerLevelAccess.NULL)

    fun getSelectedVariantIndex(): Int = selectedVariantIndex.get()
    fun getVariants(): List<ModificationVariant> = variants
    fun getVariantOutputs(): List<ItemStack> = variantOutputs
    fun getNumVariants(): Int = variants.size

    fun hasInputItem(): Boolean = inputSlot.hasItem() && variants.isNotEmpty()

    override fun stillValid(player: Player): Boolean {
        return stillValid(access, player, CobblemonBlocks.MODIFICATION_STATION)
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (isValidVariantIndex(id)) {
            selectedVariantIndex.set(id)
            setupResultSlot()
        }

        return true
    }

    private fun isValidVariantIndex(index: Int): Boolean = index >= 0 && index < variants.size

    override fun slotsChanged(container: Container) {
        val inputStack = inputSlot.item
        if (!inputStack.`is`(input.item)) {
            input = inputStack.copy()
            setupVariantList(inputStack)
        }
    }

    private fun setupVariantList(inputStack: ItemStack) {
        variants = emptyList()
        variantOutputs = emptyList()
        selectedVariantIndex.set(-1)
        resultSlot.set(ItemStack.EMPTY)

        if (!inputStack.isEmpty) {
            val parentId = getParentIdentifier(inputStack)
            if (parentId != null) {
                val resolvedVariants = ModificationVariants.forParent(parentId)
                    .mapNotNull { variant ->
                        val output = createVariantOutput(variant) ?: return@mapNotNull null
                        variant to output
                    }

                variants = resolvedVariants.map { it.first }
                variantOutputs = resolvedVariants.map { it.second }
            }
        }
    }

    private fun setupResultSlot() {
        if (isValidVariantIndex(selectedVariantIndex.get())) {
            resultSlot.set(variantOutputs[selectedVariantIndex.get()].copy())
        } else {
            resultSlot.set(ItemStack.EMPTY)
        }
        broadcastChanges()
    }

    fun registerUpdateListener(listener: Runnable) {
        slotUpdateListener = listener
    }

    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean {
        return slot.container !== resultContainer && super.canTakeItemForPickAll(stack, slot)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var copiedStack = ItemStack.EMPTY
        val slot = slots[index]

        if (slot.hasItem()) {
            val movedStack = slot.item
            val movedItem = movedStack.item
            copiedStack = movedStack.copy()

            if (index == RESULT_SLOT) {
                movedItem.onCraftedBy(movedStack, player.level(), player)
                if (!moveItemStackTo(movedStack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                    return ItemStack.EMPTY
                }
                slot.onQuickCraft(movedStack, copiedStack)
            } else if (index == INPUT_SLOT) {
                if (!moveItemStackTo(movedStack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY
                }
            } else if (hasVariantsFor(movedStack)) {
                if (!moveItemStackTo(movedStack, INPUT_SLOT, RESULT_SLOT, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index in INV_SLOT_START until INV_SLOT_END) {
                if (!moveItemStackTo(movedStack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index in USE_ROW_SLOT_START until USE_ROW_SLOT_END) {
                if (!moveItemStackTo(movedStack, INV_SLOT_START, INV_SLOT_END, false)) {
                    return ItemStack.EMPTY
                }
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
        }

        return copiedStack
    }

    override fun removed(player: Player) {
        super.removed(player)
        resultContainer.removeItemNoUpdate(1)
        access.execute { _, _ -> clearContainer(player, container) }
    }

    private fun hasVariantsFor(stack: ItemStack): Boolean {
        val parentId = getParentIdentifier(stack) ?: return false
        return ModificationVariants.forParent(parentId).isNotEmpty()
    }

    private fun getParentIdentifier(stack: ItemStack): ResourceLocation? {
        val blockItem = stack.item as? BlockItem
        if (blockItem != null) {
            val blockIdentifier = BuiltInRegistries.BLOCK.getKey(blockItem.block)
            return ModificationVariants.getByIdentifier(blockIdentifier)?.parent ?: blockIdentifier
        }

        val itemIdentifier = BuiltInRegistries.ITEM.getKey(stack.item)
        return itemIdentifier
    }

    private fun createVariantOutput(variant: ModificationVariant): ItemStack? {
        if (!BuiltInRegistries.BLOCK.containsKey(variant.identifier)) {
            return null
        }

        val block = BuiltInRegistries.BLOCK.get(variant.identifier) ?: return null
        val item = block.asItem()
        if (item == Items.AIR) {
            return null
        }

        return ItemStack(item)
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
