/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.getIntOrNull
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper

object ItemStackMoLangFunctions : AbstractMoLangFunctionHolder<Pair<ItemStack, RegistryAccess>>() {
    override fun Pair<ItemStack, RegistryAccess>.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val stack = this.first
        val registryAccess = this.second
        val itemRegistry = registryAccess.registryOrThrow(Registries.ITEM)
        val holder = itemRegistry.wrapAsHolder(stack.item)

        val map = hashMapOf<String, (MoParams) -> Any>()
        map["item"] = { _ -> holder.asMoLangValue(Registries.ITEM) }
        map["count"] = { _ -> DoubleValue(stack.count.toDouble()) }
        map["damage_value"] = { _ -> DoubleValue(stack.damageValue) }
        map["max_damage"] = { _ -> DoubleValue(stack.maxDamage) }
        map["is_empty"] = { _ -> DoubleValue(stack.isEmpty) }
        map["shrink"] = { params -> stack.shrink(params.getInt(0)) }
        map["grow"] = { params -> stack.grow(params.getInt(0)) }
        map["is_of"] = { params ->
            DoubleValue(
                holder.`is`(
                    params.getString(0).asIdentifierDefaultingNamespace()
                )
            )
        }
        map["is_in"] = { params ->
            DoubleValue(
                holder.`is`(
                    TagKey.create(
                        Registries.ITEM,
                        params.getString(0).replace("#", "").asIdentifierDefaultingNamespace()
                    )
                )
            )
        }
        map["is_food"] = { _ -> DoubleValue(stack.has(DataComponents.FOOD)) }
        map["is_enchanted"] = { _ -> DoubleValue(stack.hasFoil()) }
        map["has_enchantment"] = put@{ params ->
            val enchantmentId = params.getString(0).asIdentifierDefaultingNamespace()
            val minLevel = params.getIntOrNull(1) ?: 1
            val enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT)
            val holder =
                enchantmentRegistry.getHolder(enchantmentId).orElse(null) ?: return@put DoubleValue.ZERO
            val level = EnchantmentHelper.getItemEnchantmentLevel(holder, stack)
            return@put DoubleValue(level >= minLevel)
        }
        return map
    }
}
