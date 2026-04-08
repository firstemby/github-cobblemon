/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMenuType
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import kotlin.math.PI
import kotlin.math.min

class CobblemonBagMenu: AbstractContainerMenu {
    private val playerInventory: Inventory
    private val bagContainer: CobblemonBagContainer

    companion object {
        val BAG_SLOTS = 0..31
        val INVENTORY_SLOTS = 32..58
        val HOTBAR_SLOTS = 59..67
    }

    constructor(containerId: Int, playerInventory: Inventory): super(CobblemonMenuType.COBBLEMON_BAG, containerId) {
        this.playerInventory = playerInventory
        this.bagContainer = CobblemonBagContainer()
        initializeSlots(playerInventory)
    }

    constructor(containerId: Int, playerInventory: Inventory, bagContainer: CobblemonBagContainer): super(CobblemonMenuType.COBBLEMON_BAG, containerId) {
        this.playerInventory = playerInventory
        this.bagContainer = bagContainer
        this.bagContainer.items.add(ItemStack(CobblemonItems.APICOT_BERRY, 50))
        this.bagContainer.items.add(ItemStack(CobblemonItems.EVIOLITE, 12))
        this.bagContainer.items.add(ItemStack(CobblemonItems.FIRE_GEM, 5))
        initializeSlots(playerInventory)
    }

    private fun initializeSlots(playerInventory: Inventory) {
        for ((index, _) in BAG_SLOTS.withIndex()) {
            val i = index / 8
            val j = index % 8
            addSlot(CobblemonBagSlot(bagContainer, index, 12 + j * 18, 23 + i * 18))
        }

        for ((index, _) in INVENTORY_SLOTS.withIndex()) {
            val i = index / 9
            val j = index % 9
            addSlot(Slot(playerInventory, 9 + index, 12 + j * 18, 115 + i * 18))
        }

        for ((index, _) in HOTBAR_SLOTS.withIndex()) {
            addSlot(Slot(playerInventory, index, 12 + index * 18, 173))
        }
    }

    override fun stillValid(player: Player): Boolean {
        return true //Add some extra logic like checking for maybe bag item existance
    }

    override fun sendAllDataToRemote() { //We want to send more info than just the slots
        val items = NonNullList.create<ItemStack>()
        for (i in INVENTORY_SLOTS.first..HOTBAR_SLOTS.last) {
            this.remoteSlots[i] = this.slots[i].item.copy()
            items.add(this.remoteSlots[i].copy())
        }

        this.remoteCarried = this.carried.copy()

        bagContainer.items.forEach {
            items.add(it.copy())
        }

        if (this.synchronizer != null) {
            this.synchronizer!!.sendInitialData(this, items, this.remoteCarried, IntArray(0))
        }
    }

    override fun initializeContents(stateId: Int, items: List<ItemStack>, carried: ItemStack) {
        bagContainer.items.clear()
        items.forEachIndexed { index, stack ->
            val adjustedIndex = index + INVENTORY_SLOTS.first
            if ((INVENTORY_SLOTS.first..HOTBAR_SLOTS.last).contains(adjustedIndex)) {
                this.slots[adjustedIndex].set(stack)
            } else {
                bagContainer.items.add(stack)
            }
        }

        this.carried = carried
        this.stateId = stateId
    }

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (BAG_SLOTS.contains(slotId)) {
            try {
                doBagClick(slotId, button, clickType, player)
            } catch (exception: Exception) {
                val crashReport = CrashReport.forThrowable(exception, "Container click")
                val crashReportCategory = crashReport.addCategory("Click info")
                crashReportCategory.setDetail("Menu Type") {
                    if (this.menuType != null) BuiltInRegistries.MENU.getKey(this.menuType!!)
                        .toString() else "<no type>"
                }
                crashReportCategory.setDetail("Menu Class") { this.javaClass.getCanonicalName() }
                crashReportCategory.setDetail("Slot Count", this.slots.size)
                crashReportCategory.setDetail("Slot", slotId)
                crashReportCategory.setDetail("Button", button)
                crashReportCategory.setDetail("Type", clickType)
                throw ReportedException(crashReport)
            }
            return
        }
        super.clicked(slotId, button, clickType, player)
    }

    // We handle the bag slots completely differently than normal slots, but a lot of default logic is cut
    private fun doBagClick(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        val slot = (getSlot(slotId) as? CobblemonBagSlot) ?: return
        val slotItem = slot.item

        if (clickType == ClickType.PICKUP && (button == 0 || button == 1)) {
            val primaryButton = if (button == 0) ClickAction.PRIMARY else ClickAction.SECONDARY
            val carriedStack = carried

            if (carriedStack.isEmpty) {
                if (!slotItem.isEmpty) {
                    val slotCount = min(slotItem.count, slotItem.maxStackSize)
                    val pickupCount = if (primaryButton == ClickAction.PRIMARY) slotCount else (slotCount + 1) / 2

                    val removedItemStack = slot.remove(pickupCount)
                    if (!removedItemStack.isEmpty) {
                        carried = removedItemStack
                        slot.onTake(player, removedItemStack)
                    }
                }
            } else {
                val transferCount = if (primaryButton == ClickAction.PRIMARY) carriedStack.count else 1
                var transferred = false

                bagContainer.items.forEachIndexed { index, slotStack ->
                    if (ItemStack.isSameItemSameComponents(carriedStack, slotStack) && !tryItemClickBehaviourOverride(player, primaryButton, CobblemonBagSlot(bagContainer, index, 0, 0), slotStack, carriedStack)
                    ) { // Provide a slot with index equal to the item, as there is not really any 'real' slots
                        // TODO: Add checks for capacity
                        slotStack.grow(transferCount)
                        carriedStack.shrink(transferCount)
                        transferred = true
                        return@forEachIndexed
                    }
                }

                if (!transferred) {
                    bagContainer.items.add(carriedStack.copyAndClear())
                }
            }
        } else if (clickType == ClickType.QUICK_MOVE && (button == 0 || button == 1)) {
            quickMoveStackFromBag(player, slot)
        } else if (clickType == ClickType.CLONE && player.hasInfiniteMaterials() && carried.isEmpty && !slotItem.isEmpty) {
            carried = slotItem.copyWithCount(slotItem.maxStackSize)
        } else if (clickType == ClickType.THROW && carried.isEmpty && !slotItem.isEmpty) {
            val throwCount = if (button == 0) 1 else min(slotItem.count, slotItem.maxStackSize)
            val removedItemStack = slot.remove(throwCount)
            player.drop(removedItemStack, true)
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack { // This will only be called by base inventory
        if (index < 0 || index >= slots.size) return ItemStack.EMPTY
        val slot = slots[index]
        val itemStack = slot.item

        if (slot.hasItem()) {
            bagContainer.items.forEach { slotStack ->
                if (ItemStack.isSameItemSameComponents(slotStack, itemStack)) {
                    slotStack.grow(itemStack.count)
                    itemStack.shrink(itemStack.count)
                    return ItemStack.EMPTY
                }
            }

            bagContainer.items.add(itemStack.copy())
            slot.set(ItemStack.EMPTY)
        }
        return ItemStack.EMPTY
    }

    fun quickMoveStackFromBag(player: Player, slot: CobblemonBagSlot) { //Called by bag items
        val slotStack = slot.item
        val transferStack = slotStack.copyWithCount(min(slotStack.maxStackSize, slotStack.count))
        val startingCount = transferStack.count

        if (moveItemStackTo(transferStack, INVENTORY_SLOTS.first, HOTBAR_SLOTS.last + 1, false)) {
            val itemsMoved = startingCount - transferStack.count
            slot.remove(itemsMoved)
        }
    }

    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean {
        return !BAG_SLOTS.contains(slot.index)
    }
}