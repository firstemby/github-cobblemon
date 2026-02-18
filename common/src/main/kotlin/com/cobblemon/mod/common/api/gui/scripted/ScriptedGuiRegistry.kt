/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.ScriptedGuiSyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader

/**
 * Registry for loading scripted GUI definitions from data packs.
 *
 * Loads paired .json + .js files from `data/<namespace>/scripted_guis/`.
 * The JSON file contains metadata (mode, parameters, blocksInput).
 * The JS file contains the rendering/interaction script.
 *
 * Syncs all definitions to clients on join.
 */
object ScriptedGuiRegistry : DataRegistry {
    override val id = cobblemonResource("scripted_guis")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<ScriptedGuiRegistry>()

    private val gson = GsonBuilder().create()
    private val definitions = mutableMapOf<ResourceLocation, ScriptedGuiDefinition>()

    /** Client-side copy populated by sync packet. */
    val clientDefinitions = mutableMapOf<ResourceLocation, ScriptedGuiDefinition>()

    override fun reload(manager: ResourceManager) {
        definitions.clear()

        // Collect all JSON definitions
        val jsonResources = manager.listResources("scripted_guis") { path ->
            path.path.endsWith(".json")
        }

        // Collect all JS scripts
        val scriptResources = manager.listResources("scripted_guis") { path ->
            path.path.endsWith(".js")
        }

        // Build a map of script resource location -> script source
        val scriptSources = mutableMapOf<ResourceLocation, String>()
        for ((identifier, resource) in scriptResources) {
            try {
                resource.open().use { stream ->
                    scriptSources[identifier] = stream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[ScriptedGuiRegistry] Error loading script from $identifier", e)
            }
        }

        for ((identifier, resource) in jsonResources) {
            try {
                resource.open().use { stream ->
                    val json = gson.fromJson(InputStreamReader(stream), JsonObject::class.java)

                    // Build the GUI resource location (namespace:name without extension)
                    val path = identifier.path
                        .removePrefix("scripted_guis/")
                        .removeSuffix(".json")
                    val guiId = ResourceLocation.fromNamespaceAndPath(identifier.namespace, path)

                    // Find matching JS script
                    val scriptPath = identifier.path.removeSuffix(".json") + ".js"
                    val scriptId = ResourceLocation.fromNamespaceAndPath(identifier.namespace, scriptPath)
                    val scriptSource = scriptSources[scriptId]

                    if (scriptSource == null) {
                        Cobblemon.LOGGER.warn("[ScriptedGuiRegistry] No script found for GUI '$guiId' (expected $scriptId)")
                    }

                    // Parse mode
                    val mode = json.get("mode")?.asString?.let { GuiMode.fromString(it) } ?: GuiMode.OVERLAY

                    // Parse parameters
                    val parameters = mutableMapOf<String, ScriptedGuiParameter>()
                    json.getAsJsonObject("parameters")?.entrySet()?.forEach { (name, element) ->
                        val paramObj = element.asJsonObject
                        parameters[name] = ScriptedGuiParameter(
                            type = paramObj.get("type")?.asString ?: "string",
                            default = paramObj.get("default")?.asString
                        )
                    }

                    // Parse blocksInput
                    val blocksInput = json.get("blocksInput")?.asBoolean ?: false

                    // Parse exclusive (suppresses all other overlays when active)
                    val exclusive = json.get("exclusive")?.asBoolean ?: false

                    val definition = ScriptedGuiDefinition(
                        guiId = guiId,
                        mode = mode,
                        parameters = parameters,
                        blocksInput = blocksInput,
                        exclusive = exclusive,
                        scriptSource = scriptSource ?: ""
                    )

                    if (definitions.containsKey(guiId)) {
                        Cobblemon.LOGGER.warn("[ScriptedGuiRegistry] Duplicate GUI '$guiId', overwriting")
                    }

                    definitions[guiId] = definition
                    Cobblemon.LOGGER.debug("[ScriptedGuiRegistry] Loaded GUI '$guiId' (mode=$mode, params=${parameters.size})")
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[ScriptedGuiRegistry] Error loading GUI from $identifier", e)
            }
        }

        Cobblemon.LOGGER.info("[ScriptedGuiRegistry] Loaded ${definitions.size} scripted GUIs")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        player.sendPacket(ScriptedGuiSyncPacket(definitions.entries))
    }

    fun getDefinition(guiId: ResourceLocation): ScriptedGuiDefinition? = definitions[guiId]

    /**
     * Gets a definition for client-side use.
     * Checks the synced client definitions first, then falls back to server-side
     * definitions (for integrated server / single-player where sync is skipped).
     */
    fun getClientDefinition(guiId: ResourceLocation): ScriptedGuiDefinition? =
        clientDefinitions[guiId] ?: definitions[guiId]

    fun getAllDefinitions(): Collection<ScriptedGuiDefinition> = definitions.values
}
