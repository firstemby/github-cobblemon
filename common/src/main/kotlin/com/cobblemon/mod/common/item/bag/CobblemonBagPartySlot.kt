package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class CobblemonBagPartySlot(
    container: SimpleContainer,
    index: Int,
    val activeGetter: () -> Boolean,
    val itemGetter: () -> ItemStack,
    x: Int,
    y: Int
): Slot(container, index, x, y) {
    override fun getMaxStackSize(): Int = 1

    override fun mayPlace(stack: ItemStack): Boolean = false
    override fun mayPickup(player: Player): Boolean = false

    override fun getItem(): ItemStack {
        return itemGetter.invoke()
    }

    override fun isActive(): Boolean {
        return activeGetter.invoke()
    }
}