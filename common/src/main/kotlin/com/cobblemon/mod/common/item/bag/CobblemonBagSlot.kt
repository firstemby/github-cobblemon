package com.cobblemon.mod.common.item.bag

import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.Optional
import kotlin.math.min

class CobblemonBagSlot(
    container: CobblemonBagContainer,
    var simulatedSlot: Int, //Cant override base slot variable so it is forever -1
    x: Int,
    y: Int
): Slot(container, 0, x, y) {
    override fun set(stack: ItemStack) {}

    override fun getItem(): ItemStack {
        return container.getItem(simulatedSlot)
    }

    override fun mayPlace(stack: ItemStack): Boolean {
        return (stack.isEmpty || ItemStack.isSameItemSameComponents(stack, item))
    }

    override fun safeInsert(stack: ItemStack, increment: Int): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        bagContainer().items.forEach { theStack ->
            if (ItemStack.isSameItemSameComponents(theStack, stack)) {
                val count = min(increment, stack.count)
                stack.shrink(count)
                theStack.grow(count)
                return stack
            }
        }
        bagContainer().items.add(stack)
        return ItemStack.EMPTY
    }

    private fun bagContainer(): CobblemonBagContainer = (container as CobblemonBagContainer)
}