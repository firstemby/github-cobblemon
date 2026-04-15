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
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiActionPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object MolangUiContext {

    fun createClientRuntime(
        guiId: ResourceLocation,
        sessionId: UUID,
        parameters: Map<String, String>,
        variables: MutableMap<String, Double>
    ): MoLangRuntime {
        val runtime = MoLangRuntime().setup().setupClient()
        runtime.environment.query.addFunction("gui_id") { StringValue(guiId.toString()) }
        for ((key, value) in parameters) {
            val numValue = value.toDoubleOrNull()
            if (numValue != null) {
                runtime.environment.query.addFunction(key) { DoubleValue(numValue) }
            } else {
                runtime.environment.query.addFunction(key) { StringValue(value) }
            }
        }
        for ((key, value) in variables) {
            val varName = if (key.startsWith("v.")) key.substring(2) else key
            runtime.environment.setSimpleVariable(varName, DoubleValue(value))
        }
        runtime.environment.query.addFunction("send_action") { params ->
            val action = params.getString(0)
            val data = mutableMapOf<String, String>()
            for (i in 1 until params.params.size) {
                data[(i - 1).toString()] = params.get<MoValue>(i).asString()
            }
            Cobblemon.LOGGER.info("[MolangUi] send_action: action='$action' data=$data sessionId=$sessionId")
            MolangUiActionPacket(
                sessionId = sessionId,
                action = action,
                data = data
            ).sendToServer()
            DoubleValue(1.0)
        }
        return runtime
    }

    fun updateClientFrame(runtime: MoLangRuntime, guiTime: Float, delta: Float, screenWidth: Int, screenHeight: Int, mouseX: Double, mouseY: Double) {
        runtime.environment.query.addFunction("gui_time") { DoubleValue(guiTime.toDouble()) }
        runtime.environment.query.addFunction("delta") { DoubleValue(delta.toDouble()) }
        runtime.environment.query.addFunction("screen_width") { DoubleValue(screenWidth.toDouble()) }
        runtime.environment.query.addFunction("screen_height") { DoubleValue(screenHeight.toDouble()) }
        runtime.environment.query.addFunction("mouse_x") { DoubleValue(mouseX) }
        runtime.environment.query.addFunction("mouse_y") { DoubleValue(mouseY) }
    }

    fun updateClientParameters(runtime: MoLangRuntime, parameters: Map<String, String>) {
        for ((key, value) in parameters) {
            val numValue = value.toDoubleOrNull()
            if (numValue != null) {
                runtime.environment.query.addFunction(key) { DoubleValue(numValue) }
            } else {
                runtime.environment.query.addFunction(key) { StringValue(value) }
            }
        }
    }

    fun createServerRuntime(
        guiId: ResourceLocation,
        sessionId: UUID,
        player: ServerPlayer,
        parameters: Map<String, String>
    ): MoLangRuntime {
        val runtime = MoLangRuntime().setup()
        GuiFunctions.registerInto(runtime, player)
        runtime.environment.query.addFunction("gui_id") { StringValue(guiId.toString()) }
        for ((key, value) in parameters) {
            val numValue = value.toDoubleOrNull()
            if (numValue != null) {
                runtime.environment.query.addFunction(key) { DoubleValue(numValue) }
            } else {
                runtime.environment.query.addFunction(key) { StringValue(value) }
            }
        }
        return runtime
    }

    fun updateClientVariables(runtime: MoLangRuntime, variables: Map<String, Double>) {
        for ((key, value) in variables) {
            val varName = if (key.startsWith("v.")) key.substring(2) else key
            runtime.environment.setSimpleVariable(varName, DoubleValue(value))
        }
    }

    fun setEventContext(runtime: MoLangRuntime, widgetId: String, eventData: Map<String, String>) {
        runtime.environment.query.addFunction("event_widget") { StringValue(widgetId) }
        runtime.environment.query.addFunction("event_data") { params ->
            val key = params.getString(0)
            val value = eventData[key]
            if (value != null) StringValue(value) else DoubleValue(0.0)
        }
    }
}
