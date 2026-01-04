/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.phys.Vec3

data class StadiumBattleConfig(
    val id: String,
    val dimension: ResourceLocation?,
    val origin: Vec3,
    val cameraTimeline: ResourceLocation?,
    val cameraOriginOffset: Vec3,
    val singles: StadiumBattleLayout?,
    val doubles: StadiumBattleLayout?
) {
    fun layoutFor(format: StadiumBattleFormat): StadiumBattleLayout? = when (format) {
        StadiumBattleFormat.SINGLES -> singles
        StadiumBattleFormat.DOUBLES -> doubles
    }
}

enum class StadiumBattleFormat {
    SINGLES,
    DOUBLES;

    companion object {
        fun fromId(id: String): StadiumBattleFormat? = when (id.lowercase()) {
            "single", "singles", "1v1" -> SINGLES
            "double", "doubles", "duo", "2v2" -> DOUBLES
            else -> null
        }
    }
}

data class StadiumBattleLayout(
    val side1: StadiumBattleSide,
    val side2: StadiumBattleSide
)

data class StadiumBattleSide(
    val actors: List<StadiumPlacement>,
    val pokemon: List<StadiumPlacement>
)

data class StadiumPlacement(
    val position: Vec3,
    val lookAt: Vec3?,
    val yaw: Float?,
    val pitch: Float?
)

object StadiumBattleRegistry : DataRegistry {
    override val id = cobblemonResource("stadium_battles")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<StadiumBattleRegistry>()

    private val configs = mutableMapOf<String, StadiumBattleConfig>()

    fun get(id: String): StadiumBattleConfig? = configs[id]
    fun ids(): Set<String> = configs.keys
    fun all(): Collection<StadiumBattleConfig> = configs.values

    override fun reload(manager: ResourceManager) {
        configs.clear()

        val resources = manager.listResources("stadium_battles") { path ->
            path.path.endsWith(".json")
        }

        for ((identifier, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val reader = InputStreamReader(stream)
                    val element = JsonParser.parseReader(reader)
                    when {
                        element.isJsonArray -> element.asJsonArray.forEach { entry ->
                            parseAndStore(entry, identifier)
                        }
                        element.isJsonObject -> parseAndStore(element, identifier)
                        else -> Cobblemon.LOGGER.error("[StadiumBattleRegistry] Invalid JSON structure in {}", identifier)
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[StadiumBattleRegistry] Failed loading stadium battle config {}: {}", identifier, e.message)
                Cobblemon.LOGGER.debug("[StadiumBattleRegistry] Stacktrace", e)
            }
        }

        Cobblemon.LOGGER.info("[StadiumBattleRegistry] Loaded {} stadium battle configs", configs.size)
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // Server-side only data.
    }

    private fun parseAndStore(element: JsonElement, identifier: ResourceLocation) {
        try {
            val config = parseConfig(element.asJsonObject)
            if (configs.put(config.id, config) != null) {
                Cobblemon.LOGGER.warn("[StadiumBattleRegistry] Duplicate stadium id '{}' encountered while reading {}. Overwriting previous definition.", config.id, identifier)
            }
            Cobblemon.LOGGER.debug("[StadiumBattleRegistry] Loaded stadium '{}' from {}", config.id, identifier)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[StadiumBattleRegistry] Error parsing stadium config in {}: {}", identifier, e.message)
            Cobblemon.LOGGER.debug("[StadiumBattleRegistry] Stacktrace", e)
        }
    }

    private fun parseConfig(obj: JsonObject): StadiumBattleConfig {
        val id = obj.get("id")?.asString ?: obj.get("stadiumId")?.asString
            ?: throw IllegalArgumentException("Missing 'id' for stadium battle config")

        val dimension = obj.get("dimension")?.asString?.let { parseResourceLocation(it, "dimension") }
        val origin = obj.get("origin")?.let { parseVec3(it) } ?: Vec3.ZERO
        val cameraTimeline = obj.get("camera_timeline")?.asString?.let { parseResourceLocation(it, "camera_timeline") }
            ?: obj.get("cameraTimeline")?.asString?.let { parseResourceLocation(it, "cameraTimeline") }
        val cameraOriginOffset = obj.get("camera_origin_offset")?.let { parseVec3(it) }
            ?: obj.get("cameraOriginOffset")?.let { parseVec3(it) }
            ?: Vec3.ZERO

        val singles = obj.get("singles")?.takeIf { it.isJsonObject }?.let { parseLayout(it.asJsonObject, "singles") }
        val doubles = obj.get("doubles")?.takeIf { it.isJsonObject }?.let { parseLayout(it.asJsonObject, "doubles") }
            ?: obj.get("duo")?.takeIf { it.isJsonObject }?.let { parseLayout(it.asJsonObject, "duo") }

        if (singles == null && doubles == null) {
            throw IllegalArgumentException("Stadium '$id' must define at least one layout ('singles' or 'doubles').")
        }

        return StadiumBattleConfig(
            id = id,
            dimension = dimension,
            origin = origin,
            cameraTimeline = cameraTimeline,
            cameraOriginOffset = cameraOriginOffset,
            singles = singles,
            doubles = doubles
        )
    }

    private fun parseLayout(obj: JsonObject, context: String): StadiumBattleLayout {
        val side1 = obj.get("side1")?.asJsonObject ?: throw IllegalArgumentException("Missing '$context.side1'")
        val side2 = obj.get("side2")?.asJsonObject ?: throw IllegalArgumentException("Missing '$context.side2'")
        return StadiumBattleLayout(
            side1 = parseSide(side1, "$context.side1"),
            side2 = parseSide(side2, "$context.side2")
        )
    }

    private fun parseSide(obj: JsonObject, context: String): StadiumBattleSide {
        val actors = obj.get("actors")?.let { parsePlacementArray(it, "$context.actors") }
            ?: throw IllegalArgumentException("Missing '$context.actors' array")
        val pokemon = obj.get("pokemon")?.let { parsePlacementArray(it, "$context.pokemon") }
            ?: throw IllegalArgumentException("Missing '$context.pokemon' array")
        return StadiumBattleSide(actors = actors, pokemon = pokemon)
    }

    private fun parsePlacementArray(element: JsonElement, context: String): List<StadiumPlacement> {
        if (!element.isJsonArray) {
            throw IllegalArgumentException("Expected '$context' to be an array")
        }
        return element.asJsonArray.mapIndexed { index, entry ->
            val obj = entry.asJsonObject
            val position = obj.get("position")?.let { parseVec3(it) }
                ?: throw IllegalArgumentException("Placement '$context[$index]' missing 'position'")
            val lookAt = when {
                obj.has("look_at") -> obj.get("look_at")?.takeUnless { it.isJsonNull }?.let { parseVec3(it) }
                obj.has("lookAt") -> obj.get("lookAt")?.takeUnless { it.isJsonNull }?.let { parseVec3(it) }
                else -> null
            }
            val yaw = obj.get("yaw")?.asFloat
            val pitch = obj.get("pitch")?.asFloat
            StadiumPlacement(position = position, lookAt = lookAt, yaw = yaw, pitch = pitch)
        }
    }

    private fun parseVec3(element: JsonElement): Vec3 {
        if (element.isJsonNull) {
            throw IllegalArgumentException("Vector element cannot be null")
        }
        val obj = element.asJsonObject
        val x = obj.get("x")?.asDouble ?: 0.0
        val y = obj.get("y")?.asDouble ?: 0.0
        val z = obj.get("z")?.asDouble ?: 0.0
        return Vec3(x, y, z)
    }

    private fun parseResourceLocation(raw: String, field: String): ResourceLocation {
        return try {
            ResourceLocation.parse(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid resource location '$raw' for '$field'", e)
        }
    }
}

