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
import com.cobblemon.mod.common.api.molang.function.GeneralMoLangFunctions
import com.cobblemon.mod.common.api.molangui.MolangUiParameters
import com.cobblemon.mod.common.api.molangui.ParameterValue
import com.cobblemon.mod.common.api.molangui.asMoLangValue
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation

internal object MolangUiClientRuntimeFactory {
    fun create(guiId: ResourceLocation, parameters: Map<String, ParameterValue>, variables: Map<String, Double>) =
        MoLangRuntime().also { runtime ->
            GeneralMoLangFunctions.functions.filterKeys { it in MolangUiParameters.GENERAL_FUNCTIONS }.forEach { (name, function) ->
                runtime.environment.query.addFunction(name) { function(it) }
            }
            runtime.environment.query.addFunction("gui_id") { StringValue(guiId.toString()) }
            runtime.environment.query.addFunction("translate") { params ->
                StringValue(I18n.get(params.getString(0), *params.params.drop(1).map { it.asString() }.toTypedArray()))
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

    fun updateParameters(runtime: MoLangRuntime, parameters: Map<String, ParameterValue>) {
        MolangUiParameters.validate(parameters)
        parameters.forEach { (key, value) ->
            val resolved = value.asMoLangValue()
            runtime.environment.query.addFunction(key) { resolved }
        }
    }

    fun updateVariables(runtime: MoLangRuntime, variables: Map<String, Double>) {
        MolangUiParameters.validateVariables(variables)
        variables.forEach { (key, value) ->
            runtime.environment.setSimpleVariable(key.removePrefix("v."), DoubleValue(value))
        }
    }
}
