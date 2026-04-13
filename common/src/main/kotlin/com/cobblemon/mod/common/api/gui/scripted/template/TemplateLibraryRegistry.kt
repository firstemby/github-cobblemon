/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.template

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

object TemplateLibraryRegistry {
    private val gson = Gson()
    private val libraries: MutableMap<ResourceLocation, Map<String, TemplateDefinition>> = mutableMapOf()
    private val clientLibraries: MutableMap<ResourceLocation, Map<String, TemplateDefinition>> = mutableMapOf()

    private const val DIRECTORY = "scripted_gui_templates"

    fun reload(manager: ResourceManager) {
        libraries.clear()
        val resources = manager.listResources(DIRECTORY) { it.path.endsWith(".json") }
        for ((location, resource) in resources) {
            try {
                val json = resource.openAsReader().use { gson.fromJson(it, JsonObject::class.java) }
                val namespace = location.namespace
                val name = location.path.removePrefix("$DIRECTORY/").removeSuffix(".json")
                val libraryId = ResourceLocation.fromNamespaceAndPath(namespace, name)
                val templates = parseTemplates(json, namespace)
                libraries[libraryId] = templates
            } catch (e: Exception) {
                // Log warning and continue
            }
        }
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

    fun getClientTemplate(qualifiedName: String): TemplateDefinition? {
        val dotIndex = qualifiedName.indexOf('.')
        if (dotIndex == -1) return null
        val libraryName = qualifiedName.substring(0, dotIndex)
        val templateName = qualifiedName.substring(dotIndex + 1)
        for ((libId, templates) in clientLibraries) {
            if (libId.path == libraryName || libId.toString() == libraryName) {
                templates[templateName]?.let { return it }
            }
        }
        return null
    }

    fun setClientLibraries(entries: Map<ResourceLocation, Map<String, TemplateDefinition>>) {
        clientLibraries.clear()
        clientLibraries.putAll(entries)
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
                        type = inputObj.get("type")?.asString ?: "string",
                        default = inputObj.get("default")?.asString
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
