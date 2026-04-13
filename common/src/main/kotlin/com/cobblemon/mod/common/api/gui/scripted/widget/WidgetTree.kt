/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.widget

import com.cobblemon.mod.common.api.gui.scripted.template.TemplateDefinition
import com.cobblemon.mod.common.api.gui.scripted.template.TemplateInput
import com.cobblemon.mod.common.api.gui.scripted.template.TemplateResolver
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object WidgetTree {

    fun parse(guiJson: JsonObject, isClient: Boolean): WidgetDefinition {
        val localTemplates = parseLocalTemplates(guiJson)
        val rootJson = guiJson.getAsJsonObject("root")
            ?: throw IllegalArgumentException("GUI definition missing 'root'")
        val resolved = TemplateResolver.resolve(rootJson, localTemplates, isClient)
        return parseWidget("root", resolved)
    }

    private fun parseLocalTemplates(guiJson: JsonObject): Map<String, TemplateDefinition> {
        val result = mutableMapOf<String, TemplateDefinition>()
        guiJson.getAsJsonObject("templates")?.let { templatesObj ->
            for ((name, element) in templatesObj.entrySet()) {
                val templateJson = element.asJsonObject
                val inputs = mutableMapOf<String, TemplateInput>()
                templateJson.getAsJsonObject("inputs")?.let { inputsObj ->
                    for ((inputName, inputEl) in inputsObj.entrySet()) {
                        val inputObj = inputEl.asJsonObject
                        inputs[inputName] = TemplateInput(
                            type = inputObj.get("type")?.asString ?: "string",
                            default = inputObj.get("default")?.asString
                        )
                    }
                }
                result[name] = TemplateDefinition(
                    name = name,
                    namespace = "",
                    inputs = inputs,
                    extends = templateJson.get("extends")?.asString,
                    body = templateJson
                )
            }
        }
        return result
    }

    fun parseWidget(id: String, json: JsonObject): WidgetDefinition {
        val typeName = json.get("type")?.asString ?: "panel"
        val type = WidgetType.fromId(typeName) ?: WidgetType.PANEL

        val properties = mutableMapOf<String, WidgetProperty>()
        val events = mutableMapOf<String, String>()
        val clientEvents = mutableMapOf<String, String>()
        var hoverOverrides = emptyMap<String, WidgetProperty>()
        var transitions = emptyMap<String, TransitionConfig>()
        var enterAnimation: AnimationConfig? = null
        var exitAnimation: AnimationConfig? = null
        val isStatic = json.get("static")?.asBoolean ?: false

        val reservedKeys = setOf("type", "controls", "item_template", "static", "hover", "transition", "enter", "exit", "inputs", "extends")
        for ((key, value) in json.entrySet()) {
            when {
                key.startsWith("on_") && key.endsWith("_client") -> clientEvents[key] = value.asString
                key.startsWith("on_") -> events[key] = value.asString
                key == "hover" && value.isJsonObject -> hoverOverrides = parsePropertyMap(value.asJsonObject)
                key == "transition" && value.isJsonObject -> transitions = parseTransitions(value.asJsonObject)
                key == "enter" -> enterAnimation = parseAnimationConfig(value)
                key == "exit" -> exitAnimation = parseAnimationConfig(value)
                key !in reservedKeys -> properties[key] = WidgetProperty(jsonToRaw(value))
            }
        }

        val children = mutableListOf<WidgetDefinition>()
        json.getAsJsonArray("controls")?.let { controls ->
            for (element in controls) {
                val controlObj = element.asJsonObject
                for ((key, value) in controlObj.entrySet()) {
                    children.add(parseWidget(key, value.asJsonObject))
                }
            }
        }

        json.getAsJsonObject("item_template")?.let { itemTemplate ->
            properties["_item_template"] = WidgetProperty(itemTemplate)
        }

        return WidgetDefinition(
            id = id, type = type, properties = properties, children = children,
            events = events, clientEvents = clientEvents, hoverOverrides = hoverOverrides,
            transitions = transitions, enterAnimation = enterAnimation, exitAnimation = exitAnimation,
            isStatic = isStatic
        )
    }

    private fun parsePropertyMap(json: JsonObject): Map<String, WidgetProperty> {
        return json.entrySet().associate { (key, value) -> key to WidgetProperty(jsonToRaw(value)) }
    }

    private fun parseTransitions(json: JsonObject): Map<String, TransitionConfig> {
        val result = mutableMapOf<String, TransitionConfig>()
        for ((key, value) in json.entrySet()) {
            result[key] = when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> TransitionConfig(duration = value.asFloat)
                value.isJsonObject -> {
                    val obj = value.asJsonObject
                    TransitionConfig(
                        duration = obj.get("duration")?.asFloat ?: 0.3f,
                        easing = obj.get("easing")?.asString ?: "ease_out"
                    )
                }
                else -> TransitionConfig(0.3f)
            }
        }
        return result
    }

    private fun parseAnimationConfig(value: JsonElement): AnimationConfig {
        return when {
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> AnimationConfig(type = value.asString)
            value.isJsonObject -> {
                val obj = value.asJsonObject
                AnimationConfig(
                    type = obj.get("type")?.asString ?: "fade_in",
                    duration = obj.get("duration")?.asFloat ?: 0.3f,
                    easing = obj.get("easing")?.asString ?: "ease_out"
                )
            }
            else -> AnimationConfig("fade_in")
        }
    }

    private fun jsonToRaw(element: JsonElement): Any {
        return when {
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isNumber -> prim.asNumber
                    prim.isBoolean -> prim.asBoolean
                    else -> prim.asString
                }
            }
            element.isJsonArray -> element.asJsonArray.map { jsonToRaw(it) }
            element.isJsonObject -> element.asJsonObject
            else -> element.toString()
        }
    }
}
