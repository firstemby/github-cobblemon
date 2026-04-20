/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.molangui.container.CloseItemsBehavior
import com.cobblemon.mod.common.api.molangui.container.CollectionConfig
import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

object MolangUiManifestRegistry : DataRegistry {
    private val logger = LoggerFactory.getLogger("MolangUiManifestRegistry")
    override val id = cobblemonResource("molang_ui_manifests")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<MolangUiManifestRegistry>()

    private val gson = Gson()
    private val manifests = mutableMapOf<ResourceLocation, MolangUiManifest>()
    private const val DIRECTORY = "molang_ui"

    override fun reload(manager: ResourceManager) {
        manifests.clear()
        val resources = manager.listResources(DIRECTORY) { it.path.endsWith(".json") }

        for ((location, resource) in resources) {
            try {
                val json = resource.openAsReader().use { gson.fromJson(it, JsonObject::class.java) }
                val guiId = ResourceLocation.fromNamespaceAndPath(
                    location.namespace,
                    location.path.removePrefix("$DIRECTORY/").removeSuffix(".json")
                )
                manifests[guiId] = parseManifest(guiId, json)
            } catch (e: Exception) {
                logger.error("Failed to load Molang UI manifest: $location", e)
            }
        }
        logger.info("Loaded ${manifests.size} Molang UI manifests")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // No sync needed — manifests are server-only
    }

    fun getManifest(guiId: ResourceLocation): MolangUiManifest? = manifests[guiId]
    fun getExpectedVersion(guiId: ResourceLocation): String? = manifests[guiId]?.version
    fun getAllManifests(): Map<ResourceLocation, MolangUiManifest> = manifests.toMap()

    private fun parseManifest(guiId: ResourceLocation, json: JsonObject): MolangUiManifest {
        val version = json.get("version")?.asString ?: "0.0.0"

        val parameters = mutableMapOf<String, ManifestParameter>()
        json.getAsJsonObject("parameters")?.entrySet()?.forEach { (key, value) ->
            val obj = value.asJsonObject
            parameters[key] = ManifestParameter(
                type = obj.get("type")?.asString ?: "string",
                required = obj.get("required")?.asBoolean ?: true,
                default = obj.get("default")?.asString
            )
        }

        val actions = mutableMapOf<String, ManifestAction>()
        json.getAsJsonObject("actions")?.entrySet()?.forEach { (key, value) ->
            val obj = value.asJsonObject
            actions[key] = ManifestAction(
                handler = obj.get("handler")?.asString?.let { ResourceLocation.tryParse(it) },
                params = obj.getAsJsonArray("params")?.map { it.asString } ?: emptyList()
            )
        }

        val onOpen = json.get("on_open")?.asString?.let { ResourceLocation.tryParse(it) }

        val collections = mutableMapOf<String, CollectionConfig>()
        if (json.has("collections")) {
            val collectionsJson = json.getAsJsonObject("collections")
            for ((name, element) in collectionsJson.entrySet()) {
                val obj = element.asJsonObject
                collections[name] = CollectionConfig(
                    size = if (obj.has("size")) obj.get("size").asInt else 0,
                    allowedItems = if (obj.has("allowed_items")) obj.get("allowed_items").asString else null,
                    takeOnly = if (obj.has("take_only")) obj.get("take_only").asBoolean else false,
                    source = if (obj.has("source")) obj.get("source").asString else "session",
                    slots = if (obj.has("slots")) obj.getAsJsonArray("slots").map { it.asInt } else null
                )
            }
        }

        val shiftClickRules = mutableListOf<ShiftClickRule>()
        if (json.has("shift_click_rules")) {
            for (element in json.getAsJsonArray("shift_click_rules")) {
                val obj = element.asJsonObject
                shiftClickRules.add(ShiftClickRule(
                    from = obj.get("from").asString.split(",").map { it.trim() },
                    to = obj.get("to").asString.split(",").map { it.trim() }
                ))
            }
        }

        val closeItemsBehavior = if (json.has("on_close_items"))
            CloseItemsBehavior.fromId(json.get("on_close_items").asString)
        else CloseItemsBehavior.DROP

        return MolangUiManifest(guiId, version, parameters, actions, onOpen, collections, shiftClickRules, closeItemsBehavior)
    }
}

data class MolangUiManifest(
    val guiId: ResourceLocation,
    val version: String,
    val parameters: Map<String, ManifestParameter> = emptyMap(),
    val actions: Map<String, ManifestAction> = emptyMap(),
    val onOpen: ResourceLocation? = null,
    val collections: Map<String, CollectionConfig> = emptyMap(),
    val shiftClickRules: List<ShiftClickRule> = emptyList(),
    val closeItemsBehavior: CloseItemsBehavior = CloseItemsBehavior.DROP
)

data class ManifestParameter(
    val type: String = "string",
    val required: Boolean = true,
    val default: String? = null
)

data class ManifestAction(
    val handler: ResourceLocation? = null,
    val params: List<String> = emptyList()
)
