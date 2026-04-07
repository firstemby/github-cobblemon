package com.cobblemon.mod.common.item.bag

import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.min

class CobblemonBagContainer: Container {
    val items: MutableList<ItemStack> = mutableListOf()

    override fun getContainerSize(): Int {
        return items.size
    }

    override fun getItem(slot: Int): ItemStack {
        if (slot >= items.size) return ItemStack.EMPTY
        return items[slot]
    }

    override fun isEmpty(): Boolean {
        return items.isEmpty()
    }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot >= items.size) return ItemStack.EMPTY
        val itemStack = items[slot].split(amount)
        if (items[slot].isEmpty) {
            items.removeAt(slot)
        }
        return itemStack
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack { return ItemStack.EMPTY }

    override fun clearContent() {
        items.clear()
        setChanged()
    }

    override fun setChanged() {
    }

    override fun setItem(slot: Int, stack: ItemStack) {} // Obsolete

    override fun stillValid(player: Player): Boolean {
        return true
    }

    override fun getMaxStackSize(): Int {
        return 99999
    }
}