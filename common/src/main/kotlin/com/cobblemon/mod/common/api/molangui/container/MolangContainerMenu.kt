/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

import com.cobblemon.mod.common.CobblemonMenuType
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Layout data class describing how a grid of slots is arranged in screen space.
 */
data class SlotLayout(
    val offsetX: Int,
    val offsetY: Int,
    val columns: Int,
    val rows: Int,
    val slotWidth: Int = 18,
    val slotHeight: Int = 18,
    val spacingX: Int = 0,
    val spacingY: Int = 0
) {
    val slotCount: Int get() = columns * rows
}

/**
 * Tracks slot index ranges for a named collection so shift-click rules can resolve them.
 */
private data class CollectionSlotRange(
    val name: String,
    val startIndex: Int,
    val endIndex: Int // exclusive
)

/**
 * Container menu for Molang GUIs that declare slot_grid widgets.
 *
 * Server side: call [initializeServer] with real containers, configs, and rules.
 * Client side: call [initializeClient] with layout info so vanilla slot sync works.
 */
class MolangContainerMenu(
    containerId: Int,
    private val playerInventory: Inventory
) : AbstractContainerMenu(CobblemonMenuType.MOLANG_UI, containerId) {

    /** Named containers for custom collections. */
    val collections: MutableMap<String, Container> = mutableMapOf()

    // Slot range tracking for shift-click resolution
    private val collectionRanges: MutableList<CollectionSlotRange> = mutableListOf()
    private var playerInventoryStart: Int = 0
    private var playerHotbarStart: Int = 0
    private var playerHotbarEnd: Int = 0

    // Shift-click rules from the manifest (only populated server-side)
    private var shiftClickRules: List<ShiftClickRule> = emptyList()

    // Collection configs keyed by name (for removed() behavior)
    private val collectionConfigs: MutableMap<String, CollectionConfig> = mutableMapOf()
    private val sessionOwnedCollections: MutableSet<String> = mutableSetOf()
    private var closeItemsBehavior: CloseItemsBehavior = CloseItemsBehavior.DROP

    // -------------------------------------------------
    // Server-side initialization
    // -------------------------------------------------

    /**
     * Sets up all slots from the given collection configs and containers.
     * Also adds player inventory and hotbar slots.
     *
     * @param collectionSetup List of (name, config, layout, container) tuples
     * @param shiftClickRules Shift-click rules from the manifest
     * @param sessionOwned Names of collections whose items are session-owned (returned/dropped on close)
     * @param closeItemsBehavior How to handle items in session-owned collections on close
     */
    fun initializeServer(
        collectionSetup: List<ServerCollectionSetup>,
        shiftClickRules: List<ShiftClickRule> = emptyList(),
        sessionOwned: Set<String> = emptySet(),
        closeItemsBehavior: CloseItemsBehavior = CloseItemsBehavior.DROP,
        onSlotChange: ((collectionName: String, slotIndex: Int) -> Unit)? = null
    ) {
        this.shiftClickRules = shiftClickRules
        this.sessionOwnedCollections.addAll(sessionOwned)
        this.closeItemsBehavior = closeItemsBehavior

        for (setup in collectionSetup) {
            val (name, config, layout, container) = setup
            collections[name] = container
            collectionConfigs[name] = config

            val startIndex = slots.size
            addCollectionSlots(name, config, layout, container, onSlotChange)
            val endIndex = slots.size
            collectionRanges.add(CollectionSlotRange(name, startIndex, endIndex))
        }

        addPlayerSlots()
    }

    // -------------------------------------------------
    // Client-side initialization
    // -------------------------------------------------

    /**
     * Creates empty [SimpleContainer]s matching expected slot counts so vanilla can sync them.
     *
     * @param collectionLayouts List of (name, config, layout) — no real containers yet
     */
    fun initializeClient(
        collectionLayouts: List<ClientCollectionLayout>
    ) {
        for ((name, config, layout) in collectionLayouts) {
            val container = SimpleContainer(layout.slotCount)
            collections[name] = container
            collectionConfigs[name] = config

            val startIndex = slots.size
            addCollectionSlots(name, config, layout, container)
            val endIndex = slots.size
            collectionRanges.add(CollectionSlotRange(name, startIndex, endIndex))
        }

        addPlayerSlots()
    }

    // -------------------------------------------------
    // Shared slot setup helpers
    // -------------------------------------------------

    private fun addCollectionSlots(
        name: String,
        config: CollectionConfig,
        layout: SlotLayout,
        container: Container,
        onSlotChange: ((collectionName: String, slotIndex: Int) -> Unit)? = null
    ) {
        var containerIndex = 0
        for (row in 0 until layout.rows) {
            for (col in 0 until layout.columns) {
                val x = layout.offsetX + col * (layout.slotWidth + layout.spacingX)
                val y = layout.offsetY + row * (layout.slotHeight + layout.spacingY)
                val capturedIndex = containerIndex
                addSlot(MolangSlot(
                    container = container,
                    index = capturedIndex,
                    x = x,
                    y = y,
                    collectionConfig = config,
                    onSlotChange = if (onSlotChange != null) { { onSlotChange(name, capturedIndex) } } else null
                ))
                containerIndex++
            }
        }
    }

    private fun addPlayerSlots() {
        playerInventoryStart = slots.size

        // Player inventory rows (indices 9-35 in the Inventory)
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val inventoryIndex = 9 + row * 9 + col
                val x = 8 + col * 18
                val y = 84 + row * 18
                addSlot(Slot(playerInventory, inventoryIndex, x, y))
            }
        }

        playerHotbarStart = slots.size

        // Hotbar (indices 0-8 in the Inventory)
        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
        }

        playerHotbarEnd = slots.size
    }

    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    /** Returns the container for the named collection, or null if not present. */
    fun getCollection(name: String): Container? = collections[name]

    // -------------------------------------------------
    // AbstractContainerMenu overrides
    // -------------------------------------------------

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY

        val slotItem = slot.item
        val original = slotItem.copy()

        val clickedCollection = collectionRanges.firstOrNull { index >= it.startIndex && index < it.endIndex }
        val isPlayerInventory = index >= playerInventoryStart && index < playerInventoryEnd()
        val isHotbar = index >= playerHotbarStart && index < playerHotbarEnd

        if (clickedCollection != null) {
            // Slot is in a custom collection — try to move to player inventory/hotbar
            val rule = findShiftClickRule(clickedCollection.name, playerSideNames())
            if (rule != null) {
                val moved = tryMoveToCollections(slotItem, rule.to)
                if (!moved) return ItemStack.EMPTY
            } else {
                // Default: move to player inventory then hotbar
                if (!moveItemStackTo(slotItem, playerInventoryStart, playerHotbarEnd, false)) {
                    return ItemStack.EMPTY
                }
            }
        } else if (isPlayerInventory || isHotbar) {
            // Slot is in player inventory/hotbar — try to move to custom collections
            val rule = findShiftClickRuleFromPlayer()
            if (rule != null) {
                val moved = tryMoveToCollections(slotItem, rule.to)
                if (!moved) return ItemStack.EMPTY
            } else {
                // Default: move to first custom collection
                val firstRange = collectionRanges.firstOrNull()
                if (firstRange == null || !moveItemStackTo(slotItem, firstRange.startIndex, firstRange.endIndex, false)) {
                    return ItemStack.EMPTY
                }
            }
        }

        if (slotItem.isEmpty) {
            slot.setByPlayer(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }

        if (slotItem.count == original.count) return ItemStack.EMPTY

        slot.onTake(player, slotItem)
        return original
    }

    override fun stillValid(player: Player): Boolean = true

    override fun removed(player: Player) {
        super.removed(player)

        for (name in sessionOwnedCollections) {
            val container = collections[name] ?: continue
            when (closeItemsBehavior) {
                CloseItemsBehavior.DROP -> clearContainer(player, container)
                CloseItemsBehavior.RETURN -> returnItemsToPlayer(player, container)
                CloseItemsBehavior.DESTROY -> container.clearContent()
            }
        }
    }

    // -------------------------------------------------
    // Private helpers
    // -------------------------------------------------

    private fun playerEndIndex() = playerHotbarEnd
    private fun playerInventoryEnd() = playerHotbarStart

    private fun playerSideNames(): List<String> = listOf("player_inventory", "hotbar")

    private fun findShiftClickRule(fromCollection: String, toNames: List<String>): ShiftClickRule? {
        return shiftClickRules.firstOrNull { rule ->
            rule.from.contains(fromCollection)
        }
    }

    private fun findShiftClickRuleFromPlayer(): ShiftClickRule? {
        return shiftClickRules.firstOrNull { rule ->
            rule.from.any { it == "player_inventory" || it == "hotbar" }
        }
    }

    private fun tryMoveToCollections(stack: ItemStack, toNames: List<String>): Boolean {
        for (name in toNames) {
            if (name == "player_inventory") {
                if (moveItemStackTo(stack, playerInventoryStart, playerInventoryEnd(), false)) return true
            } else if (name == "hotbar") {
                if (moveItemStackTo(stack, playerHotbarStart, playerHotbarEnd, false)) return true
            } else {
                val range = collectionRanges.firstOrNull { it.name == name } ?: continue
                if (moveItemStackTo(stack, range.startIndex, range.endIndex, false)) return true
            }
        }
        return false
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

// -------------------------------------------------
// Setup data classes
// -------------------------------------------------

/**
 * Server-side collection setup: name, config, layout, and the real container.
 */
data class ServerCollectionSetup(
    val name: String,
    val config: CollectionConfig,
    val layout: SlotLayout,
    val container: Container
)

/**
 * Client-side collection layout: name, config, and layout (container created internally).
 */
data class ClientCollectionLayout(
    val name: String,
    val config: CollectionConfig,
    val layout: SlotLayout
)
