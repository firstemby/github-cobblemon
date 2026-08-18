/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.function.GeneralMoLangFunctions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

internal object MolangUiServerRuntimeFactory {
    private val SAFE_GENERAL_FUNCTIONS = setOf("format", "to_string", "to_number", "to_int", "is_number", "is_int", "is_blank")

    fun create(session: MolangUiSession): MoLangRuntime = MoLangRuntime().also { runtime ->
        runtime.environment.query.addFunctions(GeneralMoLangFunctions.holder.filterKeys { it in SAFE_GENERAL_FUNCTIONS })
        runtime.environment.query.addFunction("parameter") { params ->
            session.getParameter(params.getString(0))?.asMoLangValue() ?: DoubleValue.ZERO
        }
        runtime.environment.query.addFunction("update_parameter") { params ->
            val name = params.getString(0)
            val raw = params.get<MoValue>(1).asString()
            val type = session.contract.parameters[name]?.type
            DoubleValue(type != null && ParameterValue.parse(type, raw)?.let { session.updateParameters(mapOf(name to it)) } == true)
        }
        runtime.environment.query.addFunction("update_variable") { params ->
            val value = params.getDouble(1)
            DoubleValue(value.isFinite() && session.updateVariables(mapOf(params.getString(0) to value)))
        }
        runtime.environment.query.addFunction("close_ui") {
            session.close()
            DoubleValue.ONE
        }
        runtime.environment.query.addFunction("count_item") { params ->
            val item = validatedItem(params.getString(0)) ?: return@addFunction DoubleValue.ZERO
            DoubleValue(session.player.inventory.items.filter { it.`is`(item) }.sumOf(ItemStack::getCount).toDouble())
        }
        runtime.environment.query.addFunction("remove_items") { params ->
            val item = validatedItem(params.getString(0)) ?: return@addFunction DoubleValue.ZERO
            val count = params.getDouble(1).toInt()
            DoubleValue(count > 0 && removeAtomically(session, item, count))
        }
        runtime.environment.query.addFunction("slot") { params ->
            val stack = session.containerMenu?.getStack(params.getString(0), params.getDouble(1).toInt())
            if (stack == null || stack.isEmpty) StringValue("") else StringValue(BuiltInRegistries.ITEM.getKey(stack.item).toString())
        }
        runtime.environment.query.addFunction("replace_slot") { params ->
            val itemId = params.getString(5)
            validatedItem(itemId) ?: return@addFunction DoubleValue.ZERO
            val result = session.containerMenu?.replaceSlotAtomically(
                params.getString(0), params.getDouble(1).toInt(), params.getDouble(2).toInt(),
                params.getString(3), params.getDouble(4).toInt(), itemId, params.getDouble(6).toInt()
            ) ?: false
            DoubleValue(result)
        }
    }

    private fun validatedItem(raw: String) = ResourceLocation.tryParse(raw)
        ?.takeIf(BuiltInRegistries.ITEM::containsKey)
        ?.let(BuiltInRegistries.ITEM::get)

    private fun removeAtomically(session: MolangUiSession, item: net.minecraft.world.item.Item, count: Int): Boolean {
        val stacks = session.player.inventory.items.filter { it.`is`(item) }
        if (stacks.sumOf(ItemStack::getCount) < count) return false
        var remaining = count
        stacks.forEach { stack ->
            if (remaining == 0) return@forEach
            val removed = minOf(stack.count, remaining)
            stack.shrink(removed)
            remaining -= removed
        }
        session.player.inventory.setChanged()
        return true
    }
}

internal fun ParameterValue.asMoLangValue(): MoValue = when (this) {
    is ParameterValue.StringValue -> StringValue(value)
    is ParameterValue.NumberValue -> DoubleValue(value)
    is ParameterValue.BooleanValue -> DoubleValue(value)
    is ParameterValue.Vec3Value -> StringValue(asWireValue())
}
