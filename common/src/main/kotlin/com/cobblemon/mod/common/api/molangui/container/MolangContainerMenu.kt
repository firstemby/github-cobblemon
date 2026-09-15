/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.api.molangui.MolangUiSession
import com.cobblemon.mod.common.api.molangui.SessionState
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

private data class CollectionSlotRange(
    val name: String,
    val startIndex: Int,
    val endIndex: Int
)

class MolangContainerMenu(
    containerId: Int,
    private val playerInventory: Inventory
) : AbstractContainerMenu(CobblemonMenuType.MOLANG_UI, containerId) {

    private val collections: MutableMap<String, Container> = mutableMapOf()

    private val collectionRanges: MutableList<CollectionSlotRange> = mutableListOf()
    private var playerInventoryStart: Int = 0
    private var playerHotbarStart: Int = 0
    private var playerHotbarEnd: Int = 0

    private var shiftClickRules: List<ShiftClickRule> = emptyList()

    private val collectionConfigs: MutableMap<String, CollectionConfig> = mutableMapOf()
    private val sessionOwnedCollections: MutableSet<String> = mutableSetOf()
    private var closeItemsPolicy: CloseItemsPolicy = CloseItemsPolicy.RETURN
    private var owner: ServerPlayer? = null
    private var owningSession: MolangUiSession? = null
    private var blockPos: BlockPos? = null
    private var originalBlockContainer: Container? = null
    private var ownedItemsReleased = false

    internal fun initializeServer(
        collectionSetup: List<ServerCollectionSetup>,
        shiftClickRules: List<ShiftClickRule> = emptyList(),
        sessionOwned: Set<String> = emptySet(),
        closeItemsPolicy: CloseItemsPolicy = CloseItemsPolicy.RETURN,
        owner: ServerPlayer,
        session: MolangUiSession,
        blockPos: BlockPos? = null
    ) {
        this.shiftClickRules = shiftClickRules
        this.sessionOwnedCollections.addAll(sessionOwned)
        this.closeItemsPolicy = closeItemsPolicy
        this.owner = owner
        this.owningSession = session
        this.blockPos = blockPos

        for (setup in collectionSetup) {
            val (name, config, container) = setup
            collections[name] = container
            if (config.source == CollectionSource.BLOCK_ENTITY) originalBlockContainer = container
            collectionConfigs[name] = config

            val startIndex = slots.size
            addCollectionSlots(name, config, container)
            val endIndex = slots.size
            collectionRanges.add(CollectionSlotRange(name, startIndex, endIndex))
        }

        addPlayerSlots()
    }

    fun initializeClient(
        collectionLayouts: Map<String, SlotLayout>
    ) {
        SlotLayout.validateCollections(collectionLayouts)
        for ((name, layout) in collectionLayouts) {
            val config = CollectionConfig(size = layout.slotCount, layout = layout)
            val container = SimpleContainer(layout.slotCount)
            collections[name] = container
            collectionConfigs[name] = config

            val startIndex = slots.size
            addCollectionSlots(name, config, container)
            val endIndex = slots.size
            collectionRanges.add(CollectionSlotRange(name, startIndex, endIndex))
        }

        addPlayerSlots()
    }

    private fun addCollectionSlots(
        name: String,
        config: CollectionConfig,
        container: Container
    ) {
        val layout = config.layout
        var containerIndex = 0
        for (row in 0 until layout.rows) {
            for (col in 0 until layout.columns) {
                val x = layout.offsetX + col * (layout.slotWidth + layout.spacingX)
                val y = layout.offsetY + row * (layout.slotHeight + layout.spacingY)
                val mappedIndex = config.slots?.getOrNull(containerIndex) ?: containerIndex
                require(mappedIndex in 0 until container.containerSize) { "collection '$name' maps outside its container" }
                addSlot(MolangSlot(
                    container = container,
                    index = mappedIndex,
                    x = x,
                    y = y,
                    collectionConfig = config
                ))
                containerIndex++
            }
        }
    }

    private fun addPlayerSlots() {
        playerInventoryStart = slots.size

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val inventoryIndex = 9 + row * 9 + col
                val x = 8 + col * 18
                val y = 84 + row * 18
                addSlot(Slot(playerInventory, inventoryIndex, x, y))
            }
        }

        playerHotbarStart = slots.size

        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
        }

        playerHotbarEnd = slots.size
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY

        val slotItem = slot.item
        val original = slotItem.copy()

        val clickedCollection = collectionRanges.firstOrNull { index >= it.startIndex && index < it.endIndex }
        val isPlayerInventory = index >= playerInventoryStart && index < playerInventoryEnd()
        val isHotbar = index >= playerHotbarStart && index < playerHotbarEnd

        val sourceName = clickedCollection?.name ?: when {
            isPlayerInventory -> ShiftClickRule.PLAYER_INVENTORY
            isHotbar -> ShiftClickRule.PLAYER_HOTBAR
            else -> return ItemStack.EMPTY
        }
        val destinations = findShiftClickRule(sourceName)?.to ?: if (clickedCollection != null) {
            ShiftClickRule.PLAYER_SIDE_NAMES
        } else {
            collectionRanges.map { it.name }
        }
        if (!tryMoveToCollections(slotItem, slot, destinations)) return ItemStack.EMPTY

        if (slotItem.isEmpty) {
            slot.setByPlayer(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }

        if (slotItem.count == original.count) return ItemStack.EMPTY

        slot.onTake(player, slotItem)
        return original
    }

    override fun stillValid(player: Player): Boolean {
        val expectedOwner = owner ?: return true // client menu
        if (player.uuid != expectedOwner.uuid || owningSession?.state != SessionState.ACTIVE) return false
        val pos = blockPos ?: return true
        if (player.distanceToSqr(pos.x + .5, pos.y + .5, pos.z + .5) > 64.0) return false
        return player.level().getBlockEntity(pos) === originalBlockContainer
    }

    override fun removed(player: Player) {
        try {
            super.removed(player)
        } finally {
            try {
                releaseOwnedItemsOnce(player)
            } finally {
                owningSession?.closeFromMenu()
            }
        }
    }

    fun releaseOwnedItemsOnce(player: Player) {
        if (ownedItemsReleased) return
        ownedItemsReleased = true
        for (name in sessionOwnedCollections) {
            val container = collections[name] ?: continue
            when (closeItemsPolicy) {
                CloseItemsPolicy.DROP -> clearContainer(player, container)
                CloseItemsPolicy.RETURN -> returnItemsToPlayer(player, container)
                CloseItemsPolicy.DESTROY -> container.clearContent()
            }
        }
    }

    fun getStack(collection: String, index: Int): ItemStack? {
        return collectionSlot(collection, index)?.item
    }

    fun replaceSlotAtomically(
        inputCollection: String,
        inputIndex: Int,
        consumeCount: Int,
        outputCollection: String,
        outputIndex: Int,
        outputItemId: String,
        outputCount: Int
    ): Boolean {
        if (consumeCount <= 0 || outputCount <= 0) return false
        val input = collectionSlot(inputCollection, inputIndex) ?: return false
        val output = collectionSlot(outputCollection, outputIndex) ?: return false
        val id = ResourceLocation.tryParse(outputItemId)?.takeIf(BuiltInRegistries.ITEM::containsKey) ?: return false
        val produced = ItemStack(BuiltInRegistries.ITEM.get(id), outputCount)
        val outputConfig = collectionConfigs[outputCollection] ?: return false
        if (!outputConfig.matchesFilter(produced)) return false
        val source = input.item
        if (source.count < consumeCount) return false
        val remaining = source.copyWithCount(source.count - consumeCount)
        val sameSlot = input.container === output.container && input.containerSlot == output.containerSlot
        val existing = if (sameSlot) remaining else output.item
        if (!existing.isEmpty && !ItemStack.isSameItemSameComponents(existing, produced)) return false
        val limit = output.getMaxStackSize(produced)
        if (existing.count > limit || outputCount > limit - existing.count) return false
        val result = if (existing.isEmpty) produced else existing.copyWithCount(existing.count + outputCount)
        if (!sameSlot) input.set(remaining)
        output.set(result)
        return true
    }

    private fun playerInventoryEnd() = playerHotbarStart

    private fun findShiftClickRule(fromCollection: String): ShiftClickRule? {
        return shiftClickRules.firstOrNull { rule ->
            rule.from.contains(fromCollection)
        }
    }

    private fun collectionSlot(name: String, index: Int): Slot? {
        val range = collectionRanges.firstOrNull { it.name == name } ?: return null
        if (index !in 0 until range.endIndex - range.startIndex) return null
        return slots[range.startIndex + index]
    }

    private fun tryMoveToCollections(stack: ItemStack, source: Slot, toNames: List<String>): Boolean {
        val originalCount = stack.count
        for (name in toNames) {
            val indices = when (name) {
                ShiftClickRule.PLAYER_INVENTORY -> playerInventoryStart until playerHotbarStart
                ShiftClickRule.PLAYER_HOTBAR -> playerHotbarStart until playerHotbarEnd
                else -> collectionRanges.firstOrNull { it.name == name }?.let { it.startIndex until it.endIndex } ?: continue
            }
            for (merge in listOf(true, false)) {
                for (index in indices) {
                    if (stack.isEmpty) return true
                    val destination = slots[index]
                    if (destination.container === source.container && destination.containerSlot == source.containerSlot) continue
                    if (destination.hasItem() != merge || destination.item.count >= destination.getMaxStackSize(stack)) continue
                    destination.safeInsert(stack)
                }
            }
        }
        return stack.count < originalCount
    }

    private fun returnItemsToPlayer(player: Player, container: Container) {
        val inventory = player.inventory
        for (i in 0 until container.containerSize) {
            val stack = container.getItem(i)
            if (!stack.isEmpty) {
                if (!inventory.add(stack)) {
                    player.drop(stack, false)
                }
                container.setItem(i, ItemStack.EMPTY)
            }
        }
    }
}
