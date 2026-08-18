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
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.atomic.AtomicLong

object MolangUiRegistry {
    private const val DIRECTORY = "molang_ui"
    private val definitions = linkedMapOf<ResourceLocation, MolangUiDefinition>()
    private val parseFailures = AtomicLong()

    fun reload(manager: ResourceManager) {
        TemplateLibraryRegistry.reload(manager)
        val next = linkedMapOf<ResourceLocation, MolangUiDefinition>()
        manager.listResources(DIRECTORY) { it.path.endsWith(".json") && !it.path.contains("/templates/") }
            .forEach { (identifier, resource) ->
                val guiId = ResourceLocation.fromNamespaceAndPath(
                    identifier.namespace,
                    identifier.path.removePrefix("$DIRECTORY/").removeSuffix(".json")
                )
                try {
                    val json = resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
                    next[guiId] = parse(guiId, json)
                } catch (error: Exception) {
                    parseFailures.incrementAndGet()
                    Cobblemon.LOGGER.error("Rejected Molang UI view '$identifier': ${error.message}")
                }
            }
        require(next.size <= MolangUiPacketLimits.MAX_CONTRACTS) { "too many Molang UI views" }
        definitions.clear()
        definitions.putAll(next)
        Cobblemon.LOGGER.info("Loaded ${definitions.size} validated Molang UI views")
    }

    internal fun parse(guiId: ResourceLocation, json: JsonObject): MolangUiDefinition {
        val formatVersion = json.requireInt("format_version")
        require(formatVersion == MOLANG_UI_FORMAT_VERSION) { "unsupported format_version $formatVersion" }
        val contractVersion = json.requireString("contract_version")
        require(contractVersion.length in 1..MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH) { "invalid contract_version" }
        val mode = GuiMode.fromString(json.requireString("mode"))
            ?: throw IllegalArgumentException("unknown mode '${json.get("mode").asString}'")
        val parameters = json.getAsJsonObject("parameters")?.entrySet()?.associate { (name, value) ->
            MolangUiPacketLimits.requireIdentifier(name, "parameter name")
            val type = ParameterType.fromId(value.asString)
                ?: throw IllegalArgumentException("unknown parameter type for '$name'")
            name to type
        }?.toMap() ?: emptyMap()
        val actions = json.getAsJsonArray("actions")?.map { ActionKey(it.asString) }?.toSet() ?: emptySet()
        require(actions.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS) { "too many declared actions" }
        val variables = json.getAsJsonObject("variables")?.entrySet()?.associate { (name, value) ->
            MolangUiPacketLimits.requireIdentifier(name, "variable name")
            val number = value.asDouble
            require(number.isFinite()) { "variable '$name' is not finite" }
            name to number
        }?.toMap() ?: emptyMap()
        val imports = json.getAsJsonArray("imports")?.map { it.asString }?.toList() ?: emptyList()
        val root = WidgetTree.parse(json)
        val referencedActions = linkedSetOf<ActionKey>().also { collectActions(root, it) }
        val undeclared = referencedActions - actions
        require(undeclared.isEmpty()) { "widgets reference undeclared actions: ${undeclared.joinToString { it.value }}" }
        return MolangUiDefinition(
            guiId, formatVersion, contractVersion, mode, parameters, actions,
            json.get("blocks_input")?.asBoolean ?: false,
            json.get("exclusive")?.asBoolean ?: false,
            root, variables, imports
        )
    }

    private fun collectActions(node: WidgetDefinition, output: MutableSet<ActionKey>) {
        node.events.values.forEach { output += it.action }
        node.children.forEach { collectActions(it, output) }
        node.itemTemplate?.let { collectActions(it, output) }
    }

    fun getDefinition(guiId: ResourceLocation): MolangUiDefinition? = definitions[guiId]
    fun getAllDefinitions(): Map<ResourceLocation, MolangUiDefinition> = definitions.toMap()
    fun getVersionMap(): Map<ResourceLocation, String> = definitions.mapValues { it.value.contractVersion }
    fun parseFailureCount(): Long = parseFailures.get()
}

private fun JsonObject.requireString(name: String): String = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    ?: throw IllegalArgumentException("missing string '$name'")

private fun JsonObject.requireInt(name: String): Int = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    ?: throw IllegalArgumentException("missing integer '$name'")
