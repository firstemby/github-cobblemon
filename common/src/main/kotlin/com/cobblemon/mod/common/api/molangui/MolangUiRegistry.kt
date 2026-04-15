/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molangui.template.TemplateLibraryRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader

/**
 * Registry for loading Molang UI definitions from resource packs (assets/).
 *
 * Loads .json files from `assets/<namespace>/molang_ui/`.
 * Each JSON file contains the full GUI definition including metadata,
 * variables, imports, and the declarative widget tree.
 *
 * Reloaded client-side via CobblemonClient.reloadCodedAssets().
 */
object MolangUiRegistry {

    private val gson = GsonBuilder().create()
    private val definitions = mutableMapOf<ResourceLocation, MolangUiDefinition>()

    private const val DIRECTORY = "molang_ui"

    fun reload(manager: ResourceManager) {
        // Load template libraries first so they are available for resolution
        TemplateLibraryRegistry.reload(manager)

        definitions.clear()

        // Collect all JSON definitions, excluding the templates subdirectory
        val jsonResources = manager.listResources(DIRECTORY) { path ->
            path.path.endsWith(".json") && !path.path.contains("/templates/")
        }

        for ((identifier, resource) in jsonResources) {
            try {
                resource.open().use { stream ->
                    val json = gson.fromJson(InputStreamReader(stream), JsonObject::class.java)

                    // Build the GUI resource location (namespace:name without extension)
                    val path = identifier.path
                        .removePrefix("$DIRECTORY/")
                        .removeSuffix(".json")
                    val guiId = ResourceLocation.fromNamespaceAndPath(identifier.namespace, path)

                    // Parse version
                    val version = json.get("version")?.asString ?: "0.0.0"

                    // Parse mode
                    val mode = json.get("mode")?.asString?.let { GuiMode.fromString(it) } ?: GuiMode.OVERLAY

                    // Parse parameters
                    val parameters = mutableMapOf<String, MolangUiParameter>()
                    json.getAsJsonObject("parameters")?.entrySet()?.forEach { (name, element) ->
                        val paramObj = element.asJsonObject
                        parameters[name] = MolangUiParameter(
                            type = paramObj.get("type")?.asString ?: "string",
                            default = paramObj.get("default")?.asString
                        )
                    }

                    // Parse blocksInput
                    val blocksInput = json.get("blocksInput")?.asBoolean ?: false

                    // Parse exclusive (suppresses all other overlays when active)
                    val exclusive = json.get("exclusive")?.asBoolean ?: false

                    // Parse variables (client-side Molang variables with defaults)
                    val variables = mutableMapOf<String, Double>()
                    json.getAsJsonObject("variables")?.entrySet()?.forEach { (name, element) ->
                        variables[name] = element.asDouble
                    }

                    // Parse imports (template library references)
                    val imports = mutableListOf<String>()
                    json.getAsJsonArray("imports")?.forEach { element ->
                        imports.add(element.asString)
                    }

                    val definition = MolangUiDefinition(
                        guiId = guiId,
                        version = version,
                        mode = mode,
                        parameters = parameters,
                        blocksInput = blocksInput,
                        exclusive = exclusive,
                        rootJson = json,
                        variables = variables,
                        imports = imports
                    )

                    if (definitions.containsKey(guiId)) {
                        Cobblemon.LOGGER.warn("[MolangUiRegistry] Duplicate GUI '$guiId', overwriting")
                    }

                    definitions[guiId] = definition
                    Cobblemon.LOGGER.debug("[MolangUiRegistry] Loaded GUI '$guiId' (mode=$mode, version=$version, params=${parameters.size})")
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[MolangUiRegistry] Error loading GUI from $identifier", e)
            }
        }

        Cobblemon.LOGGER.info("[MolangUiRegistry] Loaded ${definitions.size} molang UIs")
    }

    fun getDefinition(guiId: ResourceLocation): MolangUiDefinition? = definitions[guiId]
    fun getAllDefinitions(): Map<ResourceLocation, MolangUiDefinition> = definitions.toMap()
    fun getVersionMap(): Map<ResourceLocation, String> = definitions.mapValues { it.value.version }
}
