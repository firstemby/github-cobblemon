/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.campfirepot

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.api.cooking.Seasonings
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.item.crafting.CookingPotRecipe
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import net.minecraft.recipebook.ServerPlaceRecipe
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.StackedContents
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerListener
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.RecipeBookMenu
import net.minecraft.world.inventory.RecipeBookType
import net.minecraft.world.inventory.ResultContainer
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

class CookingPotMenu : RecipeBookMenu<CraftingInput, CookingPotRecipeBase>, ContainerListener {
    private val player: Player
    private val level: Level
    private val playerInventory: Inventory
    private val container: CraftingContainer
    private val resultContainer: ResultContainer
    val containerData: ContainerData
    private val recipeType: RecipeType<CookingPotRecipe> = CobblemonRecipeTypes.COOKING_POT_COOKING
    private val quickCheck = RecipeManager.createCheck(CobblemonRecipeTypes.COOKING_POT_COOKING)

    constructor(containerId: Int, playerInventory: Inventory) : super(CobblemonMenuType.COOKING_POT, containerId) {
        this.playerInventory = playerInventory
        this.container = CookingPotContainer(this,
            CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH,
            CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH
        )
        this.resultContainer = ResultContainer()
        this.containerData = SimpleContainerData(3)
        this.addDataSlots(containerData)
        this.player = playerInventory.player
        this.level = playerInventory.player.level()
        initializeSlots(playerInventory)
    }

    constructor(containerId: Int, playerInventory: Inventory, container: CraftingContainer, containerData: ContainerData) : super(
        CobblemonMenuType.COOKING_POT, containerId) {
        this.playerInventory = playerInventory
        this.container = container
        this.containerData = containerData
        this.addDataSlots(containerData)
        this.resultContainer = ResultContainer()
        this.player = playerInventory.player
        this.level = playerInventory.player.level()
        container.startOpen(playerInventory.player)
        initializeSlots(playerInventory)
        this.addSlotListener(this)
    }

    private fun initializeSlots(playerInventory: Inventory) {
        val resultSlotX = 128
        val resultSlotY = 55

        addSlot(CookingPotResultSlot(this.container, CampfireBlockEntity.Companion.RESULT_SLOT, resultSlotX, resultSlotY))

        for ((index, slotIndex) in CampfireBlockEntity.Companion.CRAFTING_GRID_SLOTS.withIndex()) {
            val i = index / CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH
            val j = index % CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH
            addSlot(Slot(this.container, slotIndex, 33 + j * 18, 18 + i * 18))
        }

        for ((index, slotIndex) in CampfireBlockEntity.Companion.SEASONING_SLOTS.withIndex()) {
            addSlot(SeasoningSlot(this.container, slotIndex, 110 + index * 18, 18))
        }

        addSlot(CookingPotPreviewSlot(this.container,
            CampfireBlockEntity.Companion.PREVIEW_ITEM_SLOT, resultSlotX, resultSlotY))

        for ((index, _) in CampfireBlockEntity.Companion.PLAYER_INVENTORY_SLOTS.withIndex()) {
            val i = index / CampfireBlockEntity.Companion.PLAYER_INVENTORY_WIDTH
            val j = index % CampfireBlockEntity.Companion.PLAYER_INVENTORY_WIDTH
            addSlot(Slot(playerInventory, index + 9, 8 + j * 18, 84 + i * 18))
        }

        for ((index, _) in CampfireBlockEntity.Companion.PLAYER_HOTBAR_SLOTS.withIndex()) {
            addSlot(Slot(playerInventory, index, 8 + index * 18, 142))
        }
    }

    override fun broadcastChanges() {
        super.broadcastChanges()
    }

    override fun handlePlacement(placeAll: Boolean, recipe: RecipeHolder<*>, player: ServerPlayer) {
        // Check if the recipe value implements CookingPotRecipeBase
        val recipeValue = recipe.value()
        if (recipeValue is CookingPotRecipeBase) {
            @Suppress("UNCHECKED_CAST")
            val castedRecipe = recipe as RecipeHolder<CookingPotRecipeBase>
            this.beginPlacingRecipe()
            try {
                val serverPlaceRecipe = ServerPlaceRecipe(this)
                serverPlaceRecipe.recipeClicked(player, castedRecipe, placeAll)
            } finally {
                this.finishPlacingRecipe(castedRecipe)
            }
        } else {
            throw IllegalArgumentException("Unsupported recipe type: ${recipeValue::class.java.name}")
        }
    }

    override fun removed(player: Player) {
        super.removed(player)
    }

    override fun fillCraftSlotsStackedContents(itemHelper: StackedContents) {
        this.container.fillStackedContents(itemHelper)
    }

    override fun clearCraftingContent() {
        container.clearContent()
    }

    override fun recipeMatches(recipe: RecipeHolder<CookingPotRecipeBase>): Boolean {
        val recipeValue = recipe.value()
        return if (recipeValue is CookingPotRecipeBase) {
            recipeValue.matches(container.asCraftInput(), level)
        } else {
            false
        }
    }

    override fun getResultSlotIndex(): Int {
        return CampfireBlockEntity.Companion.RESULT_SLOT
    }

    override fun getGridWidth(): Int {
        return CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH
    }

    override fun getGridHeight(): Int {
        return CampfireBlockEntity.Companion.CRAFTING_GRID_WIDTH
    }

    override fun getSize(): Int {
        return CampfireBlockEntity.Companion.ITEMS_SIZE
    }

    fun getBurnProgress(): Float {
        val i = this.containerData.get(0)
        val j = this.containerData.get(1)
        return if (j != 0 && i != 0) {
            Mth.clamp((i.toFloat() / j.toFloat()), 0.0F, 1.0F)
        } else {
            0.0F;
        }
    }

    override fun getRecipeBookType(): RecipeBookType {
        return RecipeBookType.valueOf("COOKING_POT")
    }

    override fun shouldMoveToInventory(slotIndex: Int): Boolean {
        return slotIndex != CampfireBlockEntity.Companion.RESULT_SLOT && slotIndex != CampfireBlockEntity.Companion.PREVIEW_ITEM_SLOT
    }

    override fun quickMoveStack(
        player: Player,
        index: Int
    ): ItemStack {
        var itemStack = ItemStack.EMPTY
        val slot = slots[index]

        if (slot.hasItem()) {
            val slotItemStack = slot.item
            itemStack = slotItemStack.copy()

            if (index == CampfireBlockEntity.Companion.RESULT_SLOT) {
                if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.Companion.PLAYER_INVENTORY_SLOTS.first, CampfireBlockEntity.Companion.PLAYER_HOTBAR_SLOTS.last + 1, false)) {
                    return ItemStack.EMPTY
                }

                slot.onQuickCraft(slotItemStack, itemStack);
            } else if (index in CampfireBlockEntity.Companion.PLAYER_INVENTORY_SLOTS || index in CampfireBlockEntity.Companion.PLAYER_HOTBAR_SLOTS) {
                if (Seasonings.isSeasoning(slotItemStack)) {
                    if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.Companion.SEASONING_SLOTS.first, CampfireBlockEntity.Companion.SEASONING_SLOTS.last + 1, false) &&
                        !this.moveItemStackTo(slotItemStack, CampfireBlockEntity.Companion.CRAFTING_GRID_SLOTS.first, CampfireBlockEntity.Companion.CRAFTING_GRID_SLOTS.last + 1, false)
                    ) {
                        return ItemStack.EMPTY
                    }
                } else if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.Companion.CRAFTING_GRID_SLOTS.first, CampfireBlockEntity.Companion.CRAFTING_GRID_SLOTS.last + 1, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index in CampfireBlockEntity.Companion.CRAFTING_GRID_SLOTS || index in CampfireBlockEntity.Companion.SEASONING_SLOTS) {
                if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.Companion.PLAYER_INVENTORY_SLOTS.first, CampfireBlockEntity.Companion.PLAYER_HOTBAR_SLOTS.last + 1, false)) {
                    return ItemStack.EMPTY
                }
            }

            if (slotItemStack.isEmpty) {
                slot.setByPlayer(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }

            if (slotItemStack.count == itemStack.count) {
                return ItemStack.EMPTY
            }

            slot.onTake(player, slotItemStack)
        }

        return itemStack
    }

    override fun stillValid(player: Player): Boolean {
        return this.container.stillValid(player)
    }

    override fun slotChanged(containerToSend: AbstractContainerMenu, dataSlotIndex: Int, stack: ItemStack) {
        broadcastChanges()
    }

    override fun dataChanged(
        containerMenu: AbstractContainerMenu,
        dataSlotIndex: Int,
        value: Int
    ) {
    }
}