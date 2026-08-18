/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.molangui.container.CollectionConfig
import com.cobblemon.mod.common.api.molangui.container.CollectionSource
import com.cobblemon.mod.common.api.molangui.container.CloseItemsPolicy
import com.cobblemon.mod.common.api.molangui.container.ItemFilterEntry
import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiCompatibilityRequestPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import com.cobblemon.mod.common.Cobblemon
import java.util.concurrent.atomic.AtomicLong

object MolangUiContractRegistry : DataRegistry {
    override val id = cobblemonResource("molang_ui_contracts")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<MolangUiContractRegistry>()
    private val contracts = linkedMapOf<ResourceLocation, MolangUiContract>()
    private val parseFailures = AtomicLong()
    private const val DIRECTORY = "molang_ui"

    override fun reload(manager: ResourceManager) {
        val next = linkedMapOf<ResourceLocation, MolangUiContract>()
        manager.listResources(DIRECTORY) { it.path.endsWith(".json") }.forEach { (location, resource) ->
            val id = ResourceLocation.fromNamespaceAndPath(
                location.namespace,
                location.path.removePrefix("$DIRECTORY/").removeSuffix(".json")
            )
            try {
                val json = resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
                next[id] = parse(id, json)
            } catch (error: Exception) {
                parseFailures.incrementAndGet()
                Cobblemon.LOGGER.error("Rejected Molang UI contract '$location': ${error.message}")
            }
        }
        require(next.size <= MolangUiPacketLimits.MAX_CONTRACTS) {
            "Molang UI contract count exceeds ${MolangUiPacketLimits.MAX_CONTRACTS}"
        }
        contracts.clear()
        contracts.putAll(next)
        Cobblemon.LOGGER.info("Loaded ${contracts.size} validated Molang UI contracts")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        MolangUiCompatibilityRequestPacket(versions()).sendToPlayer(player)
    }

    internal fun parse(id: ResourceLocation, json: JsonObject): MolangUiContract {
        val formatVersion = json.int("format_version")
        require(formatVersion == MOLANG_UI_FORMAT_VERSION) { "unsupported format_version $formatVersion" }
        val contractVersion = json.string("contract_version")
        MolangUiPacketLimits.requireIdentifier(contractVersion, "contract_version")
        val mode = GuiMode.fromString(json.string("mode"))
            ?: throw IllegalArgumentException("unknown mode '${json.get("mode").asString}'")
        val parameters = parseParameters(json.getAsJsonObject("parameters"))
        val actions = parseActions(json.getAsJsonObject("actions"))
        val collections = parseCollections(json.getAsJsonObject("collections"))
        val shiftRules = parseShiftRules(json.getAsJsonArray("shift_click_rules"), collections.keys)
        val closePolicy = json.get("on_close_items")?.asString?.let { raw ->
            CloseItemsPolicy.fromId(raw) ?: throw IllegalArgumentException("unknown on_close_items policy '$raw'")
        } ?: CloseItemsPolicy.RETURN
        return MolangUiContract(
            id, formatVersion, contractVersion, mode, parameters, actions,
            json.get("on_open")?.asString?.let { ResourceLocation.tryParse(it) ?: error("invalid on_open script id") },
            collections, shiftRules, closePolicy,
            json.get("blocks_input")?.asBoolean ?: false,
            json.get("exclusive")?.asBoolean ?: false
        )
    }

    private fun parseParameters(json: JsonObject?): Map<String, ParameterDefinition> =
        json?.entrySet()?.associate { (name, element) ->
            MolangUiPacketLimits.requireIdentifier(name, "parameter name")
            val obj = element.asJsonObject
            val type = ParameterType.fromId(obj.string("type"))
                ?: throw IllegalArgumentException("unknown type for parameter '$name'")
            val default = obj.get("default")?.let { parseDefault(type, it, "parameters.$name.default") }
            require(default == null || default is ParameterDefault.Molang || (default as ParameterDefault.Literal).value.type == type) {
                "default type mismatch for parameter '$name'"
            }
            name to ParameterDefinition(type, obj.get("required")?.asBoolean ?: default == null, default)
        }?.toMap() ?: emptyMap()

    private fun parseDefault(type: ParameterType, element: JsonElement, path: String): ParameterDefault {
        if (element.isJsonObject && element.asJsonObject.has("molang")) {
            require(element.asJsonObject.size() == 1) { "expression '$path' contains unknown fields" }
            return ParameterDefault.Molang(element.asJsonObject.get("molang").asString)
        }
        val value = when (type) {
            ParameterType.STRING -> ParameterValue.StringValue(element.asString)
            ParameterType.NUMBER -> ParameterValue.NumberValue(element.asDouble)
            ParameterType.BOOLEAN -> ParameterValue.BooleanValue(element.asBoolean)
            ParameterType.VEC3 -> {
                require(element.isJsonArray && element.asJsonArray.size() == 3) { "'$path' must be a three-number array" }
                ParameterValue.Vec3Value(element.asJsonArray[0].asDouble, element.asJsonArray[1].asDouble, element.asJsonArray[2].asDouble)
            }
        }
        return ParameterDefault.Literal(value)
    }

    private fun parseActions(json: JsonObject?): Map<ActionKey, ActionDefinition> {
        val result = json?.entrySet()?.associate { (name, element) ->
            val key = ActionKey(name)
            val obj = element.asJsonObject
            val handler = obj.get("handler")?.asString?.let { ResourceLocation.tryParse(it) ?: error("invalid handler for action '$name'") }
            val params = obj.getAsJsonObject("parameters")?.entrySet()?.associate { (paramName, typeElement) ->
                MolangUiPacketLimits.requireIdentifier(paramName, "action parameter name")
                paramName to (ParameterType.fromId(typeElement.asString)
                    ?: throw IllegalArgumentException("unknown type for action parameter '$name.$paramName'"))
            }?.toMap() ?: emptyMap()
            require(params.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS) { "action '$name' has too many parameters" }
            key to ActionDefinition(handler, params)
        }?.toMap() ?: emptyMap()
        require(result.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS) { "contract has too many actions" }
        return result
    }

    private fun parseCollections(json: JsonObject?): Map<String, CollectionConfig> =
        json?.entrySet()?.associate { (name, element) ->
            MolangUiPacketLimits.requireIdentifier(name, "collection name")
            val obj = element.asJsonObject
            val source = CollectionSource.fromId(obj.get("source")?.asString ?: "session")
                ?: throw IllegalArgumentException("unknown source for collection '$name'")
            val filters = obj.getAsJsonArray("allowed_items")?.map { raw ->
                val filter = ItemFilterEntry.parse(raw.asString)
                    ?: throw IllegalArgumentException("invalid item filter '${raw.asString}' in '$name'")
                if (filter is ItemFilterEntry.Item) require(BuiltInRegistries.ITEM.containsKey(filter.id)) {
                    "unknown item '${filter.id}' in collection '$name'"
                }
                filter
            }?.toList() ?: emptyList()
            val layoutObj = obj.getAsJsonObject("layout") ?: throw IllegalArgumentException("collection '$name' is missing layout")
            val offset = layoutObj.pair("offset", 0, 0)
            val grid = layoutObj.pair("grid_dimensions", 1, 1)
            val slotSize = layoutObj.pair("slot_size", 18, 18)
            val spacing = layoutObj.pair("slot_spacing", 0, 0)
            val layout = SlotLayout(offset.first, offset.second, grid.first, grid.second, slotSize.first, slotSize.second, spacing.first, spacing.second)
            name to CollectionConfig(
                obj.int("size"), filters, obj.get("take_only")?.asBoolean ?: false, source,
                obj.getAsJsonArray("slots")?.map { it.asInt }?.toList(), layout
            )
        }?.toMap() ?: emptyMap()

    private fun parseShiftRules(array: com.google.gson.JsonArray?, collections: Set<String>): List<ShiftClickRule> =
        array?.map { element ->
            val obj = element.asJsonObject
            val from = obj.stringList("from")
            val to = obj.stringList("to")
            val valid = collections + ShiftClickRule.PLAYER_SIDE_NAMES
            require(from.isNotEmpty() && to.isNotEmpty() && (from + to).all { it in valid }) { "shift-click rule references an unknown collection" }
            ShiftClickRule(from, to)
        }?.toList() ?: emptyList()

    fun getContract(id: ResourceLocation): MolangUiContract? = contracts[id]
    fun getAllContracts(): Map<ResourceLocation, MolangUiContract> = contracts.toMap()
    fun versions(): Map<ResourceLocation, String> = contracts.mapValues { it.value.contractVersion }
    fun parseFailureCount(): Long = parseFailures.get()
}

private fun JsonObject.string(name: String): String = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    ?: throw IllegalArgumentException("missing string '$name'")
private fun JsonObject.int(name: String): Int = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    ?: throw IllegalArgumentException("missing integer '$name'")
private fun JsonObject.pair(name: String, first: Int, second: Int): Pair<Int, Int> {
    val value = getAsJsonArray(name) ?: return first to second
    require(value.size() == 2) { "'$name' must have two entries" }
    return value[0].asInt to value[1].asInt
}
private fun JsonObject.stringList(name: String): List<String> = getAsJsonArray(name)?.map { it.asString }?.toList()
    ?: throw IllegalArgumentException("missing array '$name'")
