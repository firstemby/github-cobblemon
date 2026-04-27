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
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.block.tmmachine.TMMachineBlock
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen
import com.cobblemon.mod.common.item.components.TMMoveComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.ContainerHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.level.Level

class TMMachineBlockEntity(pos: BlockPos, state: BlockState) :
    BaseContainerBlockEntity(CobblemonBlockEntities.TM_MACHINE, pos, state), TintBlockEntity {
    companion object {
        const val BURN_ACTIVE_TAG = "burnActive"
        const val BURN_PROGRESS_TAG = "burnProgress"
        const val REPEAT_PROCESS_TAG = "repeatProcess"
        const val ACTIVE_MOVE_TAG = "activeMove"

        const val BURN_PROGRESS_PER_TICK = 2
        const val BURN_TOTAL_TIME = 200
        const val TOTAL_PROCESS_TIME =
            BURN_TOTAL_TIME + ((TMMachineScreen.CRAFT_TICKS + TMMachineScreen.RESET_DISC_TICKS) * BURN_PROGRESS_PER_TICK)

        // Container data IDs
        const val BURN_PROGRESS_INDEX = 0
        const val BURN_ACTIVE_INDEX = 1
        const val REPEAT_PROCESS_INDEX = 2
        const val POSITION_X_INDEX = 3
        const val POSITION_Y_INDEX = 4
        const val POSITION_Z_INDEX = 5

        fun serverTick(level: Level, pos: BlockPos, state: BlockState, blockEntity: TMMachineBlockEntity) {
            if (level.isClientSide) return

            // check if the TMM is powered by redstone. For toggling automation
            val powered = level.hasNeighborSignal(pos)
            val data = blockEntity.containerData

            // Progress while active
            if (data.get(BURN_ACTIVE_INDEX) == 1) {
                val currentProgress = data.get(BURN_PROGRESS_INDEX)
                if (currentProgress < TOTAL_PROCESS_TIME) {
                    val progressPerTick = if (currentProgress >= BURN_TOTAL_TIME) 1 else BURN_PROGRESS_PER_TICK
                    data.set(BURN_PROGRESS_INDEX, currentProgress + progressPerTick)

                    // comparator progress bar updates
                    level.updateNeighbourForOutputSignal(pos, state.block)
                }
            }

            // auto start with batching when powered
            if (powered && data.get(BURN_ACTIVE_INDEX) == 0) {
                if (blockEntity.canCraftSelectedTM()) {
                    data.set(BURN_ACTIVE_INDEX, 1)
                    data.set(BURN_PROGRESS_INDEX, 0)
                    blockEntity.setChanged()
                    level.updateNeighbourForOutputSignal(pos, state.block)
                }
            }

            val burnProgressValue = data.get(BURN_PROGRESS_INDEX)
            val postCraftTicks = if (burnProgressValue >= BURN_TOTAL_TIME) (burnProgressValue - BURN_TOTAL_TIME) else 0

            if (postCraftTicks > 0) {
                if (postCraftTicks == TMMachineScreen.CRAFT_TICKS) {
                    blockEntity.craftTM()
                    level.updateNeighbourForOutputSignal(pos, state.block)
                }

                if (postCraftTicks >= (TMMachineScreen.CRAFT_TICKS + TMMachineScreen.RESET_DISC_TICKS)) {
                    data.set(BURN_PROGRESS_INDEX, 0)

                    // use batch processing while powered with redstone
                    val keepRunning = powered && blockEntity.canCraftSelectedTM()
                    data.set(BURN_ACTIVE_INDEX, if (keepRunning) 1 else 0)

                    blockEntity.setChanged()
                    level.updateNeighbourForOutputSignal(pos, state.block)
                }
            }
        }
    }

    override var tint: Int? = null

    var tmMachineInventory = TMMachineBlockInventory(this)
    var partialTicks: Float = 0F

    var burnProgress: Int = 0
    var burnActive: Boolean = false
    var repeatProcess: Boolean = false
    var activeMove: String = ""

    var containerData: ContainerData = object : ContainerData {
        override fun getCount() = 6
        override fun get(index: Int): Int {
            return when (index) {
                BURN_PROGRESS_INDEX -> burnProgress
                BURN_ACTIVE_INDEX -> if (burnActive) 1 else 0
                REPEAT_PROCESS_INDEX -> if (repeatProcess) 1 else 0
                POSITION_X_INDEX -> blockPos.x
                POSITION_Y_INDEX -> blockPos.y
                POSITION_Z_INDEX -> blockPos.z
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                BURN_PROGRESS_INDEX -> burnProgress = value
                BURN_ACTIVE_INDEX -> burnActive = value == 1
                REPEAT_PROCESS_INDEX -> repeatProcess = value == 1
            }
        }

    }

    private fun canCraftSelectedTM(): Boolean {
        val move = Moves.getByName(activeMove) ?: return false
        val tm = TechnicalMachines.moveToTM[move] ?: return false

        // We must have the Output be empty or stackable and not full
        val resultStack = getItem(TMMachineMenu.RESULT_SLOT)
        val crafted = ItemStack(CobblemonItems.TECHNICAL_MACHINE).also { TMMoveComponent.setTMMove(it, tm.moveName) }
        val outputOk = resultStack.isEmpty || (
                resultStack.count < resultStack.maxStackSize &&
                        ItemStack.isSameItemSameComponents(resultStack, crafted)
                )
        if (!outputOk) return false

        // Blank disc slot
        if (getItem(TMMachineMenu.BLANK_TM_SLOT).item != CobblemonItems.BLANK_TM) return false

        // Ingredient slots
        val recipes = tm.getClampedRecipe() ?: emptyList()
        for ((index, recipe) in recipes.withIndex()) {
            val slot = TMMachineMenu.INGREDIENT_SLOTS.first + index
            val provided = getItem(slot)
            if (!recipe.ingredient.test(provided) || provided.count < recipe.count) return false
        }

        return true
    }

    private fun craftTM() {
        if (!(level?.isClientSide ?: true)) {
            Moves.getByName(activeMove)?.let { move ->
                TechnicalMachines.moveToTM[move]?.let { tm ->
                    val resultStack = getItem(TMMachineMenu.RESULT_SLOT)
                    if (!resultStack.isEmpty && resultStack.count >= resultStack.maxStackSize) return

                    // Craft the TM
                    val craftedTmStack = ItemStack(CobblemonItems.TECHNICAL_MACHINE)
                        .also { TMMoveComponent.setTMMove(it, tm.moveName) }

                    if (!resultStack.isEmpty && !ItemStack.isSameItemSameComponents(resultStack, craftedTmStack)) return

                    val blankTmStack = getItem(TMMachineMenu.BLANK_TM_SLOT)
                    if (blankTmStack.item != CobblemonItems.BLANK_TM) return

                    val recipes = tm.getClampedRecipe() ?: emptyList()
                    for ((index, recipe) in recipes.withIndex()) {
                        val slot = TMMachineMenu.INGREDIENT_SLOTS.first + index
                        val provided = getItem(slot)
                        if (!recipe.ingredient.test(provided) || provided.count < recipe.count) return
                    }

                    // Consume ingredients
                    blankTmStack.shrink(1)
                    for ((index, ingredient) in recipes.withIndex()) {
                        getItem(TMMachineMenu.INGREDIENT_SLOTS.first + index).shrink(ingredient.count)
                    }

                    // Add crafted TM to result slot
                    if (resultStack.isEmpty) {
                        setItem(TMMachineMenu.RESULT_SLOT, craftedTmStack)
                        setTint(move.elementalType.hue, 0.8F)
                    } else if (resultStack.count < resultStack.maxStackSize) {
                        resultStack.grow(1)
                    }

                    tmMachineInventory.setChanged()
                    setChanged()
                }
            }
        }
    }

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu {
        return TMMachineMenu(containerId, inventory, tmMachineInventory, containerData)
    }

    override fun saveAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(compound, registries)
        saveTint(compound)
        compound.putBoolean(BURN_ACTIVE_TAG, burnActive)
        compound.putBoolean(REPEAT_PROCESS_TAG, repeatProcess)
        compound.putInt(BURN_PROGRESS_TAG, burnProgress)
        compound.putString(ACTIVE_MOVE_TAG, activeMove)
        ContainerHelper.saveAllItems(compound, tmMachineInventory.items, registries)
    }

    override fun loadAdditional(compound: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(compound, registries)
        loadTint(compound)
        burnActive = compound.getBoolean(BURN_ACTIVE_TAG)
        repeatProcess = compound.getBoolean(REPEAT_PROCESS_TAG)
        burnProgress = compound.getInt(BURN_PROGRESS_TAG)
        activeMove = compound.getString(ACTIVE_MOVE_TAG)
        ContainerHelper.loadAllItems(compound, tmMachineInventory.items, registries)
    }

    override fun getDisplayName(): Component = Component.translatable("block.cobblemon.tm_machine")

    override fun getDefaultName(): Component = Component.translatable("cobblemon.container.tm_machine")

    override fun getItems(): NonNullList<ItemStack> = tmMachineInventory.items

    override fun setItems(items: NonNullList<ItemStack>) {
        for (i in items.indices) {
            tmMachineInventory.setItem(i, items[i])
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registryLookup)

    override fun getContainerSize(): Int = tmMachineInventory.containerSize

    override fun isEmpty(): Boolean = tmMachineInventory.isEmpty

    override fun getItem(slot: Int): ItemStack = tmMachineInventory.getItem(slot)

    override fun removeItem(slot: Int, amount: Int): ItemStack = tmMachineInventory.removeItem(slot, amount)

    override fun removeItemNoUpdate(slot: Int): ItemStack = tmMachineInventory.removeItemNoUpdate(slot)

    override fun setItem(slot: Int, stack: ItemStack) = tmMachineInventory.setItem(slot, stack)

    override fun setChanged() {
        var currentState = blockState
        level?.getBlockState(worldPosition)?.let { state ->
            currentState = state
            var updated = false

            if (state.hasProperty(TMMachineBlock.EMPTY)) {
                val isEmpty = getItem(TMMachineMenu.BLANK_TM_SLOT).isEmpty
                if (currentState.getValue(TMMachineBlock.EMPTY) != isEmpty) {
                    if (!isEmpty) playSound(CobblemonSounds.TM_MACHINE_PLACE_DISC, 0.5F)
                    currentState = currentState.setValue(TMMachineBlock.EMPTY, isEmpty)
                    updated = true
                }
            }

            if (state.hasProperty(TMMachineBlock.DISPENSED)) {
                val isDispensed = !getItem(TMMachineMenu.RESULT_SLOT).isEmpty
                if (currentState.getValue(TMMachineBlock.DISPENSED) != isDispensed) {
                    currentState = currentState.setValue(TMMachineBlock.DISPENSED, isDispensed)
                    updated = true
                }
            }

            if (state.hasProperty(TMMachineBlock.ACTIVE)) {
                val isActive = containerData.get(BURN_ACTIVE_INDEX) == 1
                if (currentState.getValue(TMMachineBlock.ACTIVE) != isActive) {
                    currentState = currentState.setValue(TMMachineBlock.ACTIVE, isActive)
                    updated = true
                }
            }
            if (updated) level?.setBlockAndUpdate(worldPosition, currentState)
        }

        // Notify the block entity's level that this block entity has changed
        level?.blockEntityChanged(worldPosition)

        // Mark the chunk containing this block entity as dirty, ensuring it is saved
        level?.getChunkAt(worldPosition)?.isUnsaved = true

        // Update Neighbours
        level?.updateNeighborsAt(blockPos, currentState.block)
    }

    override fun stillValid(player: Player): Boolean = tmMachineInventory.stillValid(player)

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = tmMachineInventory.canPlaceItem(slot, stack)

    override fun canTakeItem(target: Container, slot: Int, stack: ItemStack): Boolean =
        tmMachineInventory.canTakeItem(target, slot, stack)

    fun playSound(soundEvent: SoundEvent, volume: Float = 1F, pitch: Float = 1F) {
        level?.playSound(null, worldPosition, soundEvent, SoundSource.BLOCKS, volume, pitch)
    }

    fun isReadyToCraft(): Boolean = canCraftSelectedTM()

    fun isOutputBlocked(): Boolean {
        val level = level ?: return false
        val move = Moves.getByName(activeMove) ?: return false
        val tm = TechnicalMachines.moveToTM[move] ?: return false

        val resultStack = getItem(TMMachineMenu.RESULT_SLOT)
        if (resultStack.isEmpty) return false

        val crafted = ItemStack(CobblemonItems.TECHNICAL_MACHINE)
            .also { TMMoveComponent.setTMMove(it, tm.moveName) }

        // If wrong TM in output OR output is full
        if (!ItemStack.isSameItemSameComponents(resultStack, crafted)) return true
        return resultStack.count >= resultStack.maxStackSize
    }

    class TMMachineBlockInventory(val blockEntity: TMMachineBlockEntity) : SimpleContainer(6) {
        override fun canTakeItem(target: Container, slot: Int, stack: ItemStack) = slot == 0
        override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
            val powered = blockEntity.level?.hasNeighborSignal(blockEntity.blockPos) == true
            val allowAutomation = blockEntity.containerData.get(BURN_ACTIVE_INDEX) == 1 || powered

            if (!allowAutomation) return false

            Moves.getByName(blockEntity.activeMove)?.let {
                val tm = TechnicalMachines.moveToTM[it] ?: return false
                val item = stack.item

                return when (slot) {
                    1 -> item == CobblemonItems.BLANK_TM
                    2, 3, 4 -> {
                        val recipe = tm.getClampedRecipe()
                        val recipeIndex = slot - 2
                        if (recipe == null || recipeIndex >= recipe.size) {
                            return false
                        }
                        recipe[recipeIndex].ingredient.test(stack)
                    }
                    else -> false
                }
            }

            return false
        }

        override fun setChanged() {
            super.setChanged()
            blockEntity.setChanged()

            val allowAutomation =
                blockEntity.containerData.get(BURN_ACTIVE_INDEX) == 1 || blockEntity.containerData.get(
                    REPEAT_PROCESS_INDEX
                ) == 1

            // force comparators to refresh if inventory changes and batch mode is on
            if (allowAutomation) {
                blockEntity.level?.updateNeighbourForOutputSignal(blockEntity.blockPos, blockEntity.blockState.block)
            }
        }

        override fun startOpen(player: Player) {
            blockEntity.startOpen(player)
        }

        override fun stopOpen(player: Player) {
            blockEntity.stopOpen(player)
        }
    }
}
