/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack

/**
 * Registers Molang query functions for reading and writing container slot contents
 * on a [MoLangRuntime] bound to a [MolangContainerMenu].
 *
 * Functions registered:
 * - `q.slot(collection, index)` — returns the item ID in a slot, or empty string if empty
 * - `q.slot_count(collection, index)` — returns the stack count as a double
 * - `q.set_slot(collection, index, itemId, count)` — sets slot contents; returns 1.0 on success, 0.0 on failure
 * - `q.clear_slot(collection, index)` — empties a slot; returns 1.0
 * - `q.slot_tag(collection, index, tag)` — returns 1.0 if the item has the given tag, 0.0 otherwise
 */
object SlotQueryFunctions {

    fun register(runtime: MoLangRuntime, menu: MolangContainerMenu) {

        // q.slot(collection, index) -> item ID string or empty string
        runtime.environment.query.addFunction("slot") { params ->
            val collection = params.getString(0)
            val index = params.getDouble(1).toInt()
            val container = menu.getCollection(collection)
            if (container == null || index < 0 || index >= container.containerSize) {
                return@addFunction StringValue("")
            }
            val stack = container.getItem(index)
            if (stack.isEmpty) {
                StringValue("")
            } else {
                StringValue(BuiltInRegistries.ITEM.getKey(stack.item).toString())
            }
        }

        // q.slot_count(collection, index) -> stack count as double
        runtime.environment.query.addFunction("slot_count") { params ->
            val collection = params.getString(0)
            val index = params.getDouble(1).toInt()
            val container = menu.getCollection(collection)
            if (container == null || index < 0 || index >= container.containerSize) {
                return@addFunction DoubleValue(0.0)
            }
            val stack = container.getItem(index)
            DoubleValue(stack.count.toDouble())
        }

        // q.set_slot(collection, index, itemId, count) -> 1.0 on success, 0.0 on failure
        runtime.environment.query.addFunction("set_slot") { params ->
            val collection = params.getString(0)
            val index = params.getDouble(1).toInt()
            val itemId = params.getString(2)
            val count = params.getDouble(3).toInt()

            val container = menu.getCollection(collection)
            if (container == null || index < 0 || index >= container.containerSize) {
                return@addFunction DoubleValue(0.0)
            }
            val resourceLocation = ResourceLocation.tryParse(itemId)
                ?: return@addFunction DoubleValue(0.0)
            val item = BuiltInRegistries.ITEM.get(resourceLocation)
            val newStack = ItemStack(item, count.coerceAtLeast(1))
            container.setItem(index, newStack)
            DoubleValue(1.0)
        }

        // q.clear_slot(collection, index) -> 1.0
        runtime.environment.query.addFunction("clear_slot") { params ->
            val collection = params.getString(0)
            val index = params.getDouble(1).toInt()
            val container = menu.getCollection(collection)
            if (container != null && index >= 0 && index < container.containerSize) {
                container.setItem(index, ItemStack.EMPTY)
            }
            DoubleValue(1.0)
        }

        // q.slot_tag(collection, index, tag) -> 1.0 if item has tag, 0.0 otherwise
        runtime.environment.query.addFunction("slot_tag") { params ->
            val collection = params.getString(0)
            val index = params.getDouble(1).toInt()
            val tagStr = params.getString(2)

            val container = menu.getCollection(collection)
            if (container == null || index < 0 || index >= container.containerSize) {
                return@addFunction DoubleValue(0.0)
            }
            val stack = container.getItem(index)
            if (stack.isEmpty) {
                return@addFunction DoubleValue(0.0)
            }
            val tagId = ResourceLocation.tryParse(tagStr)
                ?: return@addFunction DoubleValue(0.0)
            val tagKey = TagKey.create(Registries.ITEM, tagId)
            if (stack.`is`(tagKey)) DoubleValue(1.0) else DoubleValue(0.0)
        }
    }
}
