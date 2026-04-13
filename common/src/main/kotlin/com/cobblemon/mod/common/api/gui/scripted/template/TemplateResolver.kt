/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.template

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object TemplateResolver {

    fun resolve(root: JsonObject, localTemplates: Map<String, TemplateDefinition>, isClient: Boolean): JsonObject {
        return resolveNode(root, localTemplates, isClient)
    }

    private fun resolveNode(node: JsonObject, localTemplates: Map<String, TemplateDefinition>, isClient: Boolean): JsonObject {
        val result = node.deepCopy()

        val controls = result.getAsJsonArray("controls")
        if (controls != null) {
            val resolvedControls = JsonArray()
            for (element in controls) {
                val controlObj = element.asJsonObject
                for ((key, value) in controlObj.entrySet()) {
                    val resolvedWidget = resolveWidgetEntry(key, value.asJsonObject, localTemplates, isClient)
                    val wrapper = JsonObject()
                    val resolvedName = key.split("@")[0]
                    wrapper.add(resolvedName, resolvedWidget)
                    resolvedControls.add(wrapper)
                }
            }
            result.add("controls", resolvedControls)
        }

        result.getAsJsonObject("item_template")?.let { itemTemplate ->
            result.add("item_template", resolveNode(itemTemplate, localTemplates, isClient))
        }

        return result
    }

    private fun resolveWidgetEntry(key: String, overrides: JsonObject, localTemplates: Map<String, TemplateDefinition>, isClient: Boolean): JsonObject {
        val atIndex = key.indexOf('@')
        if (atIndex == -1) {
            return resolveNode(overrides, localTemplates, isClient)
        }

        val templateRef = key.substring(atIndex + 1)
        val template = findTemplate(templateRef, localTemplates, isClient)
            ?: return resolveNode(overrides, localTemplates, isClient)

        val resolved = resolveTemplateChain(template, localTemplates, isClient)
        val substituted = substituteInputs(resolved, template.inputs, overrides)
        val merged = mergeJson(substituted, overrides)

        merged.remove("inputs")
        merged.remove("extends")

        return resolveNode(merged, localTemplates, isClient)
    }

    private fun resolveTemplateChain(template: TemplateDefinition, localTemplates: Map<String, TemplateDefinition>, isClient: Boolean): JsonObject {
        val extendsRef = template.extends
        if (extendsRef != null) {
            val parent = findTemplate(extendsRef, localTemplates, isClient)
            if (parent != null) {
                val parentResolved = resolveTemplateChain(parent, localTemplates, isClient)
                return mergeJson(parentResolved, template.body)
            }
        }
        return template.body.deepCopy()
    }

    private fun findTemplate(ref: String, localTemplates: Map<String, TemplateDefinition>, isClient: Boolean): TemplateDefinition? {
        localTemplates[ref]?.let { return it }
        return if (isClient) {
            TemplateLibraryRegistry.getClientTemplate(ref)
        } else {
            TemplateLibraryRegistry.getTemplate(ref)
        }
    }

    private fun substituteInputs(json: JsonObject, inputs: Map<String, TemplateInput>, values: JsonObject): JsonObject {
        val jsonStr = json.toString()
        var result = jsonStr
        for ((inputName, inputDef) in inputs) {
            val value = values.get(inputName)?.asString ?: inputDef.default ?: ""
            result = result.replace("\"\$$inputName\"", "\"$value\"")
            result = result.replace("\$$inputName", value)
        }
        return JsonParser.parseString(result).asJsonObject
    }

    private fun mergeJson(base: JsonObject, overlay: JsonObject): JsonObject {
        val result = base.deepCopy()
        for ((key, value) in overlay.entrySet()) {
            if (key == "inputs") continue
            if (value.isJsonObject && result.has(key) && result.get(key).isJsonObject) {
                result.add(key, mergeJson(result.getAsJsonObject(key), value.asJsonObject))
            } else {
                result.add(key, value.deepCopy())
            }
        }
        return result
    }
}
