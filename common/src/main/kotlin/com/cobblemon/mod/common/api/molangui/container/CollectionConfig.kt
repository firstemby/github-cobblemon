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

/**
 * Configuration for a single slot collection parsed from the manifest JSON.
 */
data class CollectionConfig(
    val size: Int = 0,
    val allowedItems: String? = null,
    val takeOnly: Boolean = false,
    val source: String = "session",
    val slots: List<Int>? = null
) {
    /** Returns true if the given item is allowed in this collection's slots. */
    fun isItemAllowed(stack: ItemStack): Boolean {
        if (takeOnly) return false
        val filter = allowedItems ?: return true
        return ItemFilter.matches(stack, filter)
    }
}

/**
 * A shift-click rule mapping items from one set of collections to another.
 */
data class ShiftClickRule(
    val from: List<String>,
    val to: List<String>
)

/**
 * Utility for matching items against filter strings.
 * Supports: item IDs ("minecraft:diamond"), tags ("#minecraft:furnace_fuel"),
 * and comma-separated lists ("minecraft:diamond, minecraft:emerald").
 */
object ItemFilter {
    fun matches(stack: ItemStack, filter: String): Boolean {
        return filter.split(",").map { it.trim() }.any { matchSingle(stack, it) }
    }

    private fun matchSingle(stack: ItemStack, filter: String): Boolean {
        if (filter.startsWith("#")) {
            val tagId = ResourceLocation.tryParse(filter.substring(1)) ?: return false
            val tagKey = TagKey.create(Registries.ITEM, tagId)
            return stack.`is`(tagKey)
        }
        val itemId = ResourceLocation.tryParse(filter) ?: return false
        val item = BuiltInRegistries.ITEM.get(itemId)
        return stack.`is`(item)
    }
}

/**
 * Behavior for items remaining in session-owned collections when the GUI closes.
 */
enum class CloseItemsBehavior(val id: String) {
    DROP("drop"),
    RETURN("return"),
    DESTROY("destroy");

    companion object {
        fun fromId(id: String): CloseItemsBehavior = entries.firstOrNull { it.id == id } ?: DROP
    }
}
