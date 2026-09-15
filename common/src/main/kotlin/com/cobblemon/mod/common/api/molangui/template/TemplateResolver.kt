/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.template

import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

object TemplateResolver {
    fun resolve(root: JsonObject, localTemplates: Map<String, TemplateDefinition>): JsonObject =
        Resolution(localTemplates).resolveNode(root, emptyList(), 1)

    private class Resolution(private val localTemplates: Map<String, TemplateDefinition>) {
        private var nodes = 0
        private var values = 0

        fun resolveNode(node: JsonObject, chain: List<String>, depth: Int): JsonObject {
            require(depth <= WidgetTree.MAX_DEPTH) { "template expansion exceeds maximum widget depth ${WidgetTree.MAX_DEPTH}" }
            require(++nodes <= WidgetTree.MAX_NODES) { "template expansion exceeds maximum widget count ${WidgetTree.MAX_NODES}" }
            val result = JsonObject()
            node.entrySet().filter { it.key != "controls" && it.key != "item_template" }.forEach { (key, value) ->
                result.add(key, copy(value))
            }
            node.getAsJsonArray("controls")?.let { controls ->
                val resolvedControls = JsonArray()
                controls.forEach { element ->
                    val wrapper = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                        ?: throw IllegalArgumentException("control entries must be objects")
                    require(wrapper.size() == 1) { "control entries must contain exactly one widget" }
                    val (key, value) = wrapper.entrySet().single()
                    val output = JsonObject()
                    require(value.isJsonObject) { "widget '$key' must be an object" }
                    output.add(key.substringBefore('@'), resolveWidgetEntry(key, value.asJsonObject, chain, depth + 1))
                    resolvedControls.add(output)
                }
                result.add("controls", resolvedControls)
            }
            node.getAsJsonObject("item_template")?.let {
                result.add("item_template", resolveNode(it, chain, depth + 1))
            }
            return result
        }

        private fun resolveWidgetEntry(
            key: String,
            overrides: JsonObject,
            chain: List<String>,
            depth: Int
        ): JsonObject {
            if ('@' !in key) return resolveNode(overrides, chain, depth)
            val templateRef = key.substringAfter('@')
            require(templateRef !in chain) { "template expansion cycle: ${(chain + templateRef).joinToString(" -> ")}" }
            require(depth <= WidgetTree.MAX_DEPTH) { "template expansion exceeds maximum widget depth ${WidgetTree.MAX_DEPTH}" }
            val template = findTemplate(templateRef)
                ?: throw IllegalArgumentException("missing template '$templateRef'")
            val inherited = resolveTemplateChain(templateRef, template, emptyList())
            val inputs = inherited.inputs.mapValues { (name, input) ->
                val supplied = overrides.get(name)
                val value = supplied ?: input.default
                    ?: throw IllegalArgumentException("missing required template input '$name' for '$templateRef'")
                require(input.type.accepts(value)) {
                    "template input '$name' for '$templateRef' must be ${input.type.id}"
                }
                value
            }
            val substituted = copy(inherited.body, inputs).asJsonObject
            val nonInputs = JsonObject().also { obj ->
                overrides.entrySet().filter { it.key !in inherited.inputs }.forEach { (name, value) -> obj.add(name, value) }
            }
            return resolveNode(mergeJson(substituted, nonInputs), chain + templateRef, depth)
        }

        private fun resolveTemplateChain(
            ref: String,
            template: TemplateDefinition,
            chain: List<String>
        ): TemplateDefinition {
            require(ref !in chain) { "template inheritance cycle: ${(chain + ref).joinToString(" -> ")}" }
            require(chain.size < WidgetTree.MAX_DEPTH) { "template inheritance exceeds maximum depth ${WidgetTree.MAX_DEPTH}" }
            val nextChain = chain + ref
            val body = JsonObject().also { obj ->
                template.body.entrySet().filter { it.key != "inputs" && it.key != "extends" }
                    .forEach { (name, value) -> obj.add(name, value) }
            }
            val parentRef = template.extends ?: return template.copy(body = body)
            val parent = findTemplate(parentRef)
                ?: throw IllegalArgumentException("missing parent template '$parentRef' referenced by '$ref'")
            val inherited = resolveTemplateChain(parentRef, parent, nextChain)
            return template.copy(inputs = inherited.inputs + template.inputs, body = mergeJson(inherited.body, body))
        }

        private fun findTemplate(ref: String) =
            localTemplates[ref] ?: TemplateLibraryRegistry.getTemplate(ref)

        private fun copy(element: JsonElement, inputs: Map<String, JsonElement>? = null, depth: Int = 0): JsonElement {
            require(depth <= 128) { "template JSON exceeds maximum depth 128" }
            require(++values <= 1_048_576) { "template expansion exceeds maximum JSON work" }
            if (inputs != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                val text = element.asString
                if (text.startsWith('$') && text.length > 1 && text.indexOf('$', 1) == -1) {
                    val supplied = inputs[text.substring(1)]
                        ?: throw IllegalArgumentException("unknown template input reference '$text'")
                    return copy(supplied, depth = depth + 1)
                }
                return JsonPrimitive(text)
            }
            if (element.isJsonArray) return JsonArray().also { out -> element.asJsonArray.forEach { out.add(copy(it, inputs, depth + 1)) } }
            if (element.isJsonObject) return JsonObject().also { out ->
                element.asJsonObject.entrySet().forEach { (key, value) ->
                    out.add(key, copy(value, inputs, depth + 1))
                }
            }
            return element.deepCopy()
        }

        private fun mergeJson(base: JsonObject, overlay: JsonObject): JsonObject {
            val result = copy(base).asJsonObject
            overlay.entrySet().forEach { (key, value) ->
                val existing = result.get(key)
                result.add(key, if (value.isJsonObject && existing?.isJsonObject == true) {
                    mergeJson(existing.asJsonObject, value.asJsonObject)
                } else copy(value))
            }
            return result
        }
    }
}
