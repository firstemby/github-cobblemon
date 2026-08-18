/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.function.GeneralMoLangFunctions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

internal object MolangUiClientRuntimeFactory {
    private val SAFE_FUNCTIONS = setOf("format", "to_string", "to_number", "to_int", "is_number", "is_int", "is_blank", "clamp")

    fun create(guiId: ResourceLocation, parameters: Map<String, String>, variables: Map<String, Double>) =
        MoLangRuntime().also { runtime ->
            runtime.environment.query.addFunctions(GeneralMoLangFunctions.holder.filterKeys { it in SAFE_FUNCTIONS })
            runtime.environment.query.addFunction("gui_id") { StringValue(guiId.toString()) }
            runtime.environment.query.addFunction("item_name") { params ->
                val id = ResourceLocation.tryParse(params.getString(0))
                val item = id?.takeIf(BuiltInRegistries.ITEM::containsKey)?.let(BuiltInRegistries.ITEM::get)
                StringValue(item?.description?.string ?: "")
            }
            updateParameters(runtime, parameters)
            updateVariables(runtime, variables)
        }

    fun updateFrame(runtime: MoLangRuntime, guiTime: Float, delta: Float, width: Int, height: Int, mouseX: Double, mouseY: Double) {
        runtime.environment.query.addFunction("gui_time") { DoubleValue(guiTime.toDouble()) }
        runtime.environment.query.addFunction("delta") { DoubleValue(delta.toDouble()) }
        runtime.environment.query.addFunction("screen_width") { DoubleValue(width.toDouble()) }
        runtime.environment.query.addFunction("screen_height") { DoubleValue(height.toDouble()) }
        runtime.environment.query.addFunction("mouse_x") { DoubleValue(mouseX) }
        runtime.environment.query.addFunction("mouse_y") { DoubleValue(mouseY) }
    }

    fun updateParameters(runtime: MoLangRuntime, parameters: Map<String, String>) = parameters.forEach { (key, value) ->
        val number = value.toDoubleOrNull()
        runtime.environment.query.addFunction(key) { if (number != null) DoubleValue(number) else StringValue(value) }
    }

    fun updateVariables(runtime: MoLangRuntime, variables: Map<String, Double>) = variables.forEach { (key, value) ->
        runtime.environment.setSimpleVariable(key.removePrefix("v."), DoubleValue(value))
    }
}
