/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.template

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import com.cobblemon.mod.common.Cobblemon

object TemplateLibraryRegistry {
    private val gson = Gson()
    private val libraries: MutableMap<ResourceLocation, Map<String, TemplateDefinition>> = mutableMapOf()

    private const val DIRECTORY = "molang_ui/templates"

    fun reload(manager: ResourceManager) {
        val next = mutableMapOf<ResourceLocation, Map<String, TemplateDefinition>>()
        val resources = manager.listResources(DIRECTORY) { it.path.endsWith(".json") }
        for ((location, resource) in resources) {
            try {
                val json = resource.openAsReader().use { gson.fromJson(it, JsonObject::class.java) }
                val namespace = location.namespace
                val name = location.path.removePrefix("$DIRECTORY/").removeSuffix(".json")
                val libraryId = ResourceLocation.fromNamespaceAndPath(namespace, name)
                val templates = parseTemplates(json, namespace)
                next[libraryId] = templates
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("Rejected Molang UI template library '$location': ${e.message}")
            }
        }
        libraries.clear()
        libraries.putAll(next)
    }

    fun getTemplate(qualifiedName: String): TemplateDefinition? {
        val dotIndex = qualifiedName.indexOf('.')
        if (dotIndex == -1) return null
        val libraryName = qualifiedName.substring(0, dotIndex)
        val templateName = qualifiedName.substring(dotIndex + 1)
        for ((libId, templates) in libraries) {
            if (libId.path == libraryName || libId.toString() == libraryName) {
                templates[templateName]?.let { return it }
            }
        }
        return null
    }

    private fun parseTemplates(json: JsonObject, namespace: String): Map<String, TemplateDefinition> {
        val result = mutableMapOf<String, TemplateDefinition>()
        val templatesObj = json.getAsJsonObject("templates") ?: return result
        for ((name, element) in templatesObj.entrySet()) {
            val templateJson = element.asJsonObject
            val inputs = mutableMapOf<String, TemplateInput>()
            templateJson.getAsJsonObject("inputs")?.let { inputsObj ->
                for ((inputName, inputEl) in inputsObj.entrySet()) {
                    val inputObj = inputEl.asJsonObject
                    inputs[inputName] = TemplateInput(
                        type = TemplateInputType.fromId(inputObj.get("type")?.asString ?: "string")
                            ?: throw IllegalArgumentException("unknown template input type for '$inputName'"),
                        default = inputObj.get("default")?.deepCopy()
                    )
                }
            }
            val extends = templateJson.get("extends")?.asString
            result[name] = TemplateDefinition(
                name = name,
                namespace = namespace,
                inputs = inputs,
                extends = extends,
                body = templateJson
            )
        }
        return result
    }
}

data class TemplateDefinition(
    val name: String,
    val namespace: String,
    val inputs: Map<String, TemplateInput> = emptyMap(),
    val extends: String? = null,
    val body: JsonObject
)

data class TemplateInput(
    val type: TemplateInputType = TemplateInputType.STRING,
    val default: JsonElement? = null
)

enum class TemplateInputType(val id: String) {
    STRING("string"), NUMBER("number"), BOOLEAN("boolean"), EXPRESSION("expression"), OBJECT("object"), ARRAY("array");

    fun accepts(value: JsonElement): Boolean = when (this) {
        STRING -> value.isJsonPrimitive && value.asJsonPrimitive.isString
        NUMBER -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber
        BOOLEAN -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
        EXPRESSION -> value.isJsonObject && value.asJsonObject.size() == 1 && value.asJsonObject.get("molang")?.isJsonPrimitive == true
        OBJECT -> value.isJsonObject
        ARRAY -> value.isJsonArray
    }

    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } }
}
