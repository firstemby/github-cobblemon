/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
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

    fun fromTag(tag: ListTag, levelRegistry: HolderLookup.Provider) {
        items.clear()

        tag.forEach {
            val item = ItemStack.parse(levelRegistry, it)
            item.ifPresent { theItem ->
                theItem.count = (it as? CompoundTag)?.getInt("actCount") ?: 1
                items.add(theItem)
            }
        }
    }

    fun createTag(levelRegistry: HolderLookup.Provider): ListTag {
        val tag = ListTag()

        items.forEach {
            val compoundTag = CompoundTag()
            compoundTag.putInt("actCount", it.count) // ItemStack does not save count above 99 for some reason, so we save it ourselves
            val fakeItem = it.copyWithCount(1)
            tag.add(fakeItem.save(levelRegistry, compoundTag))
        }

        return tag
    }
}