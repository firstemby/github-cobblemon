/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui.renderer

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.google.gson.JsonParser
import net.minecraft.client.gui.GuiGraphics

object ListRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
    }

    fun expandListChildren(listWidget: WidgetInstance, runtime: MoLangRuntime): List<WidgetDefinition> {
        val sourceType = listWidget.definition.getStringProperty("source_type") ?: "array"
        val templateDef = listWidget.definition.itemTemplate ?: return emptyList()

        return when (sourceType) {
            "count" -> {
                val count = listWidget.definition.getProperty("source")?.resolveInt(runtime) ?: 0
                (0 until count).map { index -> templateDef.copy(id = "item_$index") }
            }
            "data" -> {
                val dataItems = parseDataSource(listWidget, runtime)
                dataItems.mapIndexed { index, _ ->
                    templateDef.copy(id = "item_$index")
                }
            }
            else -> emptyList()
        }
    }

    fun parseDataSource(listWidget: WidgetInstance, runtime: MoLangRuntime): List<Map<String, String>> {
        val dataJson = listWidget.definition.getProperty("source")?.resolveString(runtime) ?: "[]"
        return try {
            val dataArray = JsonParser.parseString(dataJson).asJsonArray
            dataArray.map { element ->
                val obj = element.asJsonObject
                obj.entrySet().associate { (key, value) ->
                    key to if (value.isJsonPrimitive) value.asString else value.toString()
                }
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("[ListRenderer] Failed to parse data source: ${e.message}")
            emptyList()
        }
    }

    fun setItemContext(runtime: MoLangRuntime, index: Int, itemData: Map<String, String>?) {
        val itemStruct = QueryStruct(hashMapOf())
        itemStruct.addFunction("index") { _ -> DoubleValue(index.toDouble()) }
        itemData?.forEach { (key, value) ->
            val numValue = value.toDoubleOrNull()
            itemStruct.addFunction(key) { _ ->
                if (numValue != null) DoubleValue(numValue) else StringValue(value)
            }
        }
        runtime.environment.query.addFunction("item") { _ -> itemStruct }
    }
}
