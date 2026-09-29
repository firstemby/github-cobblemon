/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.widget

import com.cobblemon.mod.common.api.molangui.layout.SizeExpression
import com.cobblemon.mod.common.api.molangui.ActionKey
import com.cobblemon.mod.common.api.molangui.requireUiString
import com.cobblemon.mod.common.api.molangui.template.TemplateDefinition
import com.cobblemon.mod.common.api.molangui.template.TemplateInput
import com.cobblemon.mod.common.api.molangui.template.TemplateInputType
import com.cobblemon.mod.common.api.molangui.template.TemplateResolver
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object WidgetTree {
    const val MAX_DEPTH = 32
    const val MAX_NODES = 2048
    private val UNSUPPORTED_PROPERTIES = setOf(
        "transition", "enter", "exit", "static", "on_submit", "on_submit_client", "on_slot_change", "on_slot_change_client"
    )
    private val EVENT_NAMES = setOf(
        "on_click", "on_change", "on_hover_enter", "on_hover_exit", "on_scroll", "on_key"
    )

    fun parse(guiJson: JsonObject): WidgetDefinition {
        val localTemplates = parseLocalTemplates(guiJson)
        val rootJson = guiJson.getAsJsonObject("root")
            ?: throw IllegalArgumentException("UI definition is missing 'root'")
        val resolved = TemplateResolver.resolve(rootJson, localTemplates)
        val ids = mutableSetOf<String>()
        val count = intArrayOf(0)
        return parseWidget("root", resolved, ids, 1, count)
    }

    fun parseWidget(id: String, json: JsonObject): WidgetDefinition =
        parseWidget(id, json, mutableSetOf(), 1, intArrayOf(0))

    private fun parseWidget(
        id: String,
        json: JsonObject,
        ids: MutableSet<String>,
        depth: Int,
        count: IntArray
    ): WidgetDefinition {
        require(id.isNotBlank() && id.length <= 128) { "invalid widget id '$id'" }
        require(ids.add(id)) { "duplicate widget id '$id'" }
        require(depth <= MAX_DEPTH) { "widget tree exceeds maximum depth $MAX_DEPTH at '$id'" }
        count[0]++
        require(count[0] <= MAX_NODES) { "widget tree exceeds maximum node count $MAX_NODES" }

        val typeName = json.get("type")?.asString ?: "panel"
        val type = WidgetType.fromId(typeName)
            ?: throw IllegalArgumentException("unsupported widget renderer '$typeName' at '$id'")
        val properties = linkedMapOf<String, WidgetProperty>()
        val events = linkedMapOf<String, ServerWidgetEvent>()
        val clientEvents = linkedMapOf<String, String>()
        var hoverOverrides = emptyMap<String, WidgetProperty>()
        val reserved = setOf("type", "controls", "item_template", "hover", "inputs", "extends")
        json.entrySet().forEach { (key, value) ->
            require(key !in UNSUPPORTED_PROPERTIES) { "unsupported widget property '$id.$key'" }
            when {
                key.endsWith("_client") && key.removeSuffix("_client") in EVENT_NAMES ->
                    clientEvents[key] = readExpression(value, "$id.$key")
                key in EVENT_NAMES -> {
                    events[key] = parseServerEvent(value, "$id.$key")
                }
                key == "hover" && value.isJsonObject -> hoverOverrides = parsePropertyMap(value.asJsonObject, "$id.hover")
                key !in reserved -> properties[key] = parseProperty(value, "$id.$key")
            }
        }

        val children = mutableListOf<WidgetDefinition>()
        json.getAsJsonArray("controls")?.forEach { element ->
            require(element.isJsonObject && element.asJsonObject.size() == 1) { "controls under '$id' must be single-entry objects" }
            val (childId, childValue) = element.asJsonObject.entrySet().single()
            require(childValue.isJsonObject) { "widget '$childId' must be an object" }
            children += parseWidget(childId, childValue.asJsonObject, ids, depth + 1, count)
        }

        val itemTemplate = json.getAsJsonObject("item_template")?.let { template ->
            parseWidget("$id#item_template", template, ids, depth + 1, count)
        }
        validateGeometry(id, type, properties)
        return WidgetDefinition(
            id, type, properties.toMap(), children.toList(), events.toMap(), clientEvents.toMap(),
            hoverOverrides.toMap(), itemTemplate
        )
    }

    private fun parseLocalTemplates(guiJson: JsonObject): Map<String, TemplateDefinition> {
        val result = linkedMapOf<String, TemplateDefinition>()
        guiJson.getAsJsonObject("templates")?.entrySet()?.forEach { (name, element) ->
            val body = element.asJsonObject
            val inputs = linkedMapOf<String, TemplateInput>()
            body.getAsJsonObject("inputs")?.entrySet()?.forEach { (inputName, inputElement) ->
                val input = inputElement.asJsonObject
                val type = TemplateInputType.fromId(input.get("type")?.asString ?: "string")
                    ?: throw IllegalArgumentException("unknown template input type at '$name.$inputName'")
                val default = input.get("default")
                require(default == null || type.accepts(default)) { "invalid template default at '$name.$inputName'" }
                inputs[inputName] = TemplateInput(type, default)
            }
            result[name] = TemplateDefinition(name, "", inputs.toMap(), body.get("extends")?.asString, body)
        }
        return result.toMap()
    }

    private fun parsePropertyMap(json: JsonObject, path: String) = json.entrySet().associate { (key, value) ->
        key to parseProperty(value, "$path.$key")
    }

    private fun parseProperty(value: JsonElement, path: String): WidgetProperty {
        if (value.isJsonObject && value.asJsonObject.has("molang")) {
            require(value.asJsonObject.size() == 1) {
                "expression '$path' must contain only a string 'molang' field"
            }
            return WidgetProperty.molang(value.asJsonObject.get("molang").requireUiString("$path.molang"))
        }
        return WidgetProperty.literal(jsonToLiteral(value, path))
    }

    private fun jsonToLiteral(element: JsonElement, path: String): Any = when {
        element.isJsonPrimitive -> element.asJsonPrimitive.let { when {
            it.isNumber -> it.asDouble.also { number -> require(number.isFinite()) { "non-finite number at '$path'" } }
            it.isBoolean -> it.asBoolean
            else -> it.asString
        } }
        element.isJsonArray -> element.asJsonArray.mapIndexed { index, item ->
            if (item.isJsonObject && item.asJsonObject.has("molang")) {
                require(item.asJsonObject.size() == 1) { "expression '$path[$index]' contains unknown fields" }
                WidgetExpression(item.asJsonObject.get("molang").requireUiString("$path[$index].molang"))
            } else jsonToLiteral(item, "$path[$index]")
        }.toList()
        element.isJsonObject -> element.asJsonObject.entrySet().associate { (key, item) -> key to jsonToLiteral(item, "$path.$key") }.toMap()
        else -> throw IllegalArgumentException("null is not a supported widget value at '$path'")
    }

    private fun readExpression(element: JsonElement, path: String): String {
        require(element.isJsonObject && element.asJsonObject.size() == 1) { "client event '$path' must be an explicit molang object" }
        return element.asJsonObject.get("molang").requireUiString("$path.molang").also {
            require(!it.contains("send_action")) { "client event '$path' may not dispatch server actions" }
        }
    }

    private fun parseServerEvent(element: JsonElement, path: String): ServerWidgetEvent {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val action = element.asString
            require(action.startsWith("action@") && action.length > 7) { "server event '$path' must declare exactly one action" }
            return ServerWidgetEvent(ActionKey(action.substringAfter("action@")))
        }
        require(element.isJsonObject) { "server event '$path' must be an object" }
        val obj = element.asJsonObject
        val action = ActionKey(obj.get("action")?.asString
            ?: throw IllegalArgumentException("server event '$path' is missing action"))
        val parameters = obj.getAsJsonObject("parameters")?.entrySet()?.associate { (name, value) ->
            name to parseProperty(value, "$path.parameters.$name")
        }?.toMap() ?: emptyMap()
        require(obj.keySet().all { it == "action" || it == "parameters" }) { "server event '$path' contains unknown fields" }
        return ServerWidgetEvent(action, parameters)
    }

    private fun validateGeometry(id: String, type: WidgetType, properties: Map<String, WidgetProperty>) {
        listOf("size", "offset", "grid_dimensions", "slot_size", "slot_spacing").forEach { key ->
            val raw = properties[key]?.raw ?: return@forEach
            require(raw is List<*> && raw.size == 2) { "'$id.$key' must contain exactly two values" }
            raw.filterIsInstance<Double>().forEach { require(it.isFinite()) { "non-finite geometry at '$id.$key'" } }
        }
        listOf("size", "offset").forEach { key ->
            (properties[key]?.raw as? List<*>)?.forEach { SizeExpression.parse(requireNotNull(it)) }
        }
        properties["padding"]?.raw?.let { padding ->
            require(padding is List<*> && padding.size == 4 && padding.all { it is Number || it is WidgetExpression }) {
                "'$id.padding' must contain four numbers or explicit Molang expressions"
            }
            padding.filterIsInstance<Number>().forEach {
                require(it.toFloat().isFinite() && it.toFloat() >= 0f) { "invalid padding at '$id'" }
            }
        }
        if (type == WidgetType.SLOT_GRID) {
            val dimensions = properties["grid_dimensions"]?.raw as? List<*>
                ?: throw IllegalArgumentException("slot grid '$id' is missing grid_dimensions")
            require(dimensions.all { it is Double && it in 1.0..16.0 && it == it.toInt().toDouble() }) {
                "slot grid '$id' must have integer dimensions in 1..16"
            }
            val collectionName = properties["collection_name"]?.raw
            require(collectionName is String && collectionName.isNotBlank()) { "slot grid '$id' is missing collection_name" }
        }
    }
}
