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
        val functions = mutableMapOf<String, (MoParams) -> Any>()

        functions["item"] = { holder.asMoLangValue(Registries.ITEM) }
        functions["count"] = { DoubleValue(stack.count.toDouble()) }
        functions["damage_value"] = { DoubleValue(stack.damageValue) }
        functions["max_damage"] = { DoubleValue(stack.maxDamage) }
        functions["is_empty"] = { DoubleValue(stack.isEmpty) }
        functions["shrink"] = { params -> stack.shrink(params.getInt(0)) }
        functions["grow"] = { params -> stack.grow(params.getInt(0)) }
        functions["is_of"] = { params ->
            DoubleValue(
                holder.`is`(
                    params.getString(0).asIdentifierDefaultingNamespace(),
                ),
            )
        }
        functions["is_in"] = { params ->
            DoubleValue(
                holder.`is`(
                    TagKey.create(
                        Registries.ITEM,
                        params.getString(0).replace("#", "").asIdentifierDefaultingNamespace(),
                    ),
                ),
            )
        }
        functions["is_food"] = { DoubleValue(stack.has(DataComponents.FOOD)) }
        functions["is_enchanted"] = { DoubleValue(stack.hasFoil()) }
        functions["has_enchantment"] = hasEnchantment@{ params ->
            val enchantmentId = params.getString(0).asIdentifierDefaultingNamespace()
            val minLevel = params.getIntOrNull(1) ?: 1
            val enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT)
            val holder =
                enchantmentRegistry.getHolder(enchantmentId).orElse(null) ?: return@hasEnchantment DoubleValue.ZERO
            val level = EnchantmentHelper.getItemEnchantmentLevel(holder, stack)
            DoubleValue(level >= minLevel)
        }

        return functions
    }
}
