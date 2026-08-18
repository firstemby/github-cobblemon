/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.template

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

object TemplateResolver {
    fun resolve(root: JsonObject, localTemplates: Map<String, TemplateDefinition>): JsonObject =
        resolveNode(root, localTemplates, emptyList())

    private fun resolveNode(
        node: JsonObject,
        localTemplates: Map<String, TemplateDefinition>,
        chain: List<String>
    ): JsonObject {
        val result = node.deepCopy()
        result.getAsJsonArray("controls")?.let { controls ->
            val resolvedControls = JsonArray()
            controls.forEach { element ->
                val wrapper = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: throw IllegalArgumentException("control entries must be objects")
                require(wrapper.size() == 1) { "control entries must contain exactly one widget" }
                val (key, value) = wrapper.entrySet().single()
                val output = JsonObject()
                output.add(key.substringBefore('@'), resolveWidgetEntry(key, value.asJsonObject, localTemplates, chain))
                resolvedControls.add(output)
            }
            result.add("controls", resolvedControls)
        }
        result.getAsJsonObject("item_template")?.let {
            result.add("item_template", resolveNode(it, localTemplates, chain))
        }
        return result
    }

    private fun resolveWidgetEntry(
        key: String,
        overrides: JsonObject,
        localTemplates: Map<String, TemplateDefinition>,
        chain: List<String>
    ): JsonObject {
        if ('@' !in key) return resolveNode(overrides, localTemplates, chain)
        val templateRef = key.substringAfter('@')
        val template = findTemplate(templateRef, localTemplates)
            ?: throw IllegalArgumentException("missing template '$templateRef'")
        val templateBody = resolveTemplateChain(templateRef, template, localTemplates, chain)
        val values = template.inputs.mapValues { (name, input) ->
            val supplied = overrides.get(name)
            val value = supplied ?: input.default
                ?: throw IllegalArgumentException("missing required template input '$name' for '$templateRef'")
            require(input.type.accepts(value)) {
                "template input '$name' for '$templateRef' must be ${input.type.id}"
            }
            value.deepCopy()
        }
        val substituted = substitute(templateBody, values).asJsonObject
        val nonInputs = overrides.deepCopy().also { obj -> template.inputs.keys.forEach(obj::remove) }
        return resolveNode(mergeJson(substituted, nonInputs), localTemplates, chain)
    }

    private fun resolveTemplateChain(
        ref: String,
        template: TemplateDefinition,
        localTemplates: Map<String, TemplateDefinition>,
        chain: List<String>
    ): JsonObject {
        require(ref !in chain) { "template inheritance cycle: ${(chain + ref).joinToString(" -> ")}" }
        val nextChain = chain + ref
        val parentRef = template.extends ?: return template.body.deepCopy()
        val parent = findTemplate(parentRef, localTemplates)
            ?: throw IllegalArgumentException("missing parent template '$parentRef' referenced by '$ref'")
        return mergeJson(resolveTemplateChain(parentRef, parent, localTemplates, nextChain), template.body)
    }

    private fun findTemplate(ref: String, localTemplates: Map<String, TemplateDefinition>) =
        localTemplates[ref] ?: TemplateLibraryRegistry.getTemplate(ref)

    private fun substitute(element: JsonElement, values: Map<String, JsonElement>): JsonElement {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val text = element.asString
            if (text.startsWith('$') && text.length > 1 && text.indexOf('$', 1) == -1) {
                return values[text.substring(1)]?.deepCopy()
                    ?: throw IllegalArgumentException("unknown template input reference '$text'")
            }
            return JsonPrimitive(text)
        }
        if (element.isJsonArray) return JsonArray().also { out -> element.asJsonArray.forEach { out.add(substitute(it, values)) } }
        if (element.isJsonObject) return JsonObject().also { out ->
            element.asJsonObject.entrySet().forEach { (key, value) -> out.add(key, substitute(value, values)) }
        }
        return element.deepCopy()
    }

    private fun mergeJson(base: JsonObject, overlay: JsonObject): JsonObject {
        val result = base.deepCopy()
        overlay.entrySet().forEach { (key, value) ->
            if (key == "inputs" || key == "extends") return@forEach
            val existing = result.get(key)
            result.add(key, if (value.isJsonObject && existing?.isJsonObject == true) {
                mergeJson(existing.asJsonObject, value.asJsonObject)
            } else value.deepCopy())
        }
        result.remove("inputs")
        result.remove("extends")
        return result
    }
}
