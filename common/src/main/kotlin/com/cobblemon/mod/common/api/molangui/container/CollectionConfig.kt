/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack

data class CollectionConfig(
    val size: Int,
    val allowedItems: List<ItemFilterEntry> = emptyList(),
    val takeOnly: Boolean = false,
    val source: CollectionSource = CollectionSource.SESSION,
    val slots: List<Int>? = null,
    val layout: SlotLayout
) {
    init {
        require(size in 1..MAX_COLLECTION_SIZE) { "collection size must be in 1..$MAX_COLLECTION_SIZE" }
        require(layout.slotCount == size) { "collection size must match its grid dimensions" }
        require(slots == null || slots.size == size) { "block collection slot mapping must match collection size" }
        require(slots == null || slots.distinct().size == slots.size) { "block collection slot mapping contains duplicates" }
        require(source == CollectionSource.BLOCK_ENTITY || slots == null) { "slot mapping is only valid for block_entity sources" }
    }

    fun isItemAllowed(stack: ItemStack): Boolean {
        if (takeOnly) return false
        if (allowedItems.isEmpty()) return true
        return allowedItems.any { it.matches(stack) }
    }

    companion object { const val MAX_COLLECTION_SIZE = 256 }
}

enum class CollectionSource(val id: String) {
    SESSION("session"), BLOCK_ENTITY("block_entity");
    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } }
}

data class ShiftClickRule(
    val from: List<String>,
    val to: List<String>
) {
    companion object {
        const val PLAYER_INVENTORY = "player_inventory"
        const val PLAYER_HOTBAR = "player_hotbar"
        val PLAYER_SIDE_NAMES = listOf(PLAYER_INVENTORY, PLAYER_HOTBAR)
    }
}

sealed interface ItemFilterEntry {
    fun matches(stack: ItemStack): Boolean

    data class Item(val id: ResourceLocation) : ItemFilterEntry {
        override fun matches(stack: ItemStack): Boolean {
            if (!BuiltInRegistries.ITEM.containsKey(id)) return false
            return stack.`is`(BuiltInRegistries.ITEM.get(id))
        }
    }

    data class Tag(val id: ResourceLocation) : ItemFilterEntry {
        override fun matches(stack: ItemStack): Boolean = stack.`is`(TagKey.create(Registries.ITEM, id))
    }

    companion object {
        fun parse(value: String): ItemFilterEntry? {
            val trimmed = value.trim()
            val isTag = trimmed.startsWith('#')
            val id = ResourceLocation.tryParse(if (isTag) trimmed.substring(1) else trimmed) ?: return null
            return if (isTag) Tag(id) else Item(id)
        }
    }
}

enum class CloseItemsPolicy(val id: String) {
    DROP("drop"),
    RETURN("return"),
    DESTROY("destroy");

    companion object {
        fun fromId(id: String): CloseItemsPolicy? = entries.firstOrNull { it.id == id }
    }
}
