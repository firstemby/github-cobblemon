package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMenuType
import net.minecraft.core.NonNullList
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
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

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        TODO("Not yet implemented")
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
}