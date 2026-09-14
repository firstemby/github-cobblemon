/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.util.UUID
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.phys.Vec3

data class StadiumBattleConfig(
    val id: String,
    val dimension: ResourceLocation?,
    val origin: Vec3,
    val cameraOriginOffset: Vec3,
    val singles: StadiumBattleLayout?,
    val doubles: StadiumBattleLayout?,
    val cinematicScene: ResourceLocation? = null,
    val formats: Map<String, StadiumFormatConfig> = emptyMap(),
    val activeFormat: String = "",
    val cinematicSceneJson: String? = null,
    val arena: StadiumArena? = null,
    val spectatorAreas: List<StadiumSpectatorArea> = emptyList(),
    val displayName: Component = Component.literal(id)
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

    private var configs: Map<String, StadiumBattleConfig> = emptyMap()
    private val fileConfigs = mutableMapOf<String, StadiumBattleConfig>()
    private val dynamicConfigs = mutableMapOf<UUID, StadiumBattleConfig>()

    fun get(id: String): StadiumBattleConfig? = configs[id]
    fun ids(): Set<String> = configs.keys
    fun all(): Collection<StadiumBattleConfig> = configs.values

    fun clearDynamic() {
        dynamicConfigs.clear()
        refresh()
    }

    fun registerDynamic(stadiumUuid: UUID, config: StadiumBattleConfig) {
        dynamicConfigs[stadiumUuid] = config
        refresh()
    }

    fun hasDynamic(stadiumUuid: UUID): Boolean = stadiumUuid in dynamicConfigs

    fun unregisterDynamic(stadiumUuid: UUID, expected: StadiumBattleConfig) {
        if (dynamicConfigs[stadiumUuid] !== expected) return
        if (dynamicConfigs.remove(stadiumUuid) != null) refresh()
    }

    private fun refresh() {
        configs = fileConfigs + dynamicConfigs.values.associateBy { it.id }
    }

    override fun reload(manager: ResourceManager) {
        fileConfigs.clear()

        val resources = manager.listResources("stadium_battles") { path ->
            path.path.endsWith(".json")
        }

        for ((identifier, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val reader = InputStreamReader(stream, Charsets.UTF_8)
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

        refresh()

        Cobblemon.LOGGER.info("[StadiumBattleRegistry] Loaded {} stadium battle configs ({} dynamic)", configs.size, dynamicConfigs.size)
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
    }

    private fun parseAndStore(element: JsonElement, identifier: ResourceLocation) {
        try {
            val config = parseConfig(element.asJsonObject)
            if (fileConfigs.put(config.id, config) != null) {
                Cobblemon.LOGGER.warn("[StadiumBattleRegistry] Duplicate stadium id '{}' encountered while reading {}. Overwriting previous definition.", config.id, identifier)
            }
            Cobblemon.LOGGER.debug("[StadiumBattleRegistry] Loaded stadium '{}' from {}", config.id, identifier)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[StadiumBattleRegistry] Error parsing stadium config in {}: {}", identifier, e.message)
            Cobblemon.LOGGER.debug("[StadiumBattleRegistry] Stacktrace", e)
        }
    }

    private fun parseConfig(obj: JsonObject): StadiumBattleConfig {
        val id = obj.get("id")?.asString
            ?: throw IllegalArgumentException("Missing 'id' for stadium battle config")
        require(id.isNotBlank() && id.length <= 128) { "Stadium id must contain 1 to 128 characters" }

        val dimension = obj.get("dimension")?.asString?.let { parseResourceLocation(it, "dimension") }
        val origin = obj.get("origin")?.let { parseVec3(it) } ?: Vec3.ZERO
        val cameraOriginOffset = obj.get("camera_origin_offset")?.let { parseVec3(it) }
            ?: Vec3.ZERO

        val singles = obj.get("singles")?.let { parseLayout(it.asJsonObject, "singles") }
        val doubles = obj.get("doubles")?.let { parseLayout(it.asJsonObject, "doubles") }

        if (singles == null && doubles == null) {
            throw IllegalArgumentException("Stadium '$id' must define at least one layout ('singles' or 'doubles').")
        }

        val arena = obj.get("arena")?.asJsonObject?.let { value ->
            StadiumArena(relativeVector(value.getAsJsonObject("center"), origin),
                requiredDouble(value, "width"), requiredDouble(value, "depth")).also {
                require(it.valid(origin)) { "Invalid stadium arena" }
            }
        }
        if (arena != null) {
            val placements = listOfNotNull(singles, doubles).flatMap { layout ->
                layout.side1.actors + layout.side1.pokemon + layout.side2.actors + layout.side2.pokemon
            }
            require(placements.all { arena.contains(origin.add(it.position)) }) { "Stadium placement is outside its arena" }
        }
        val spectatorAreas = obj.get("spectator_areas")?.asJsonArray?.let { areas ->
            require(areas.size() <= StadiumSpectatorArea.MAX_AREAS) { "Too many stadium spectator areas" }
            areas.map { entry ->
                val area = entry.asJsonObject
                StadiumSpectatorArea(relativeVector(area.getAsJsonObject("min"), origin),
                    relativeVector(area.getAsJsonObject("max"), origin)).also {
                    require(it.valid(origin)) { "Invalid stadium spectator area" }
                }
            }
        }.orEmpty()
        require(spectatorAreas.isEmpty() || arena != null) { "Stadium spectator areas require an arena" }
        val formats = obj.get("formats")?.asJsonObject?.let { values ->
            require(values.size() <= StadiumConfiguration.MAX_FORMATS) { "Too many stadium formats" }
            values.entrySet().associate { (key, value) ->
                require(key.matches(Regex("[a-z0-9_]{1,40}"))) { "Invalid stadium format id '$key'" }
                key to parseFormat(value.asJsonObject)
            }
        }.orEmpty()
        val activeFormat = optionalString(obj, "active_format").orEmpty()
        require(if (formats.isEmpty()) activeFormat.isEmpty() else activeFormat in formats) {
            "Stadium active_format must name a configured format"
        }

        return StadiumBattleConfig(
            id = id,
            dimension = dimension,
            origin = origin,
            cinematicScene = obj.get("cinematic_scene")?.asString?.let { parseResourceLocation(it, "cinematic_scene") },
            cameraOriginOffset = cameraOriginOffset,
            singles = singles,
            doubles = doubles,
            arena = arena,
            spectatorAreas = spectatorAreas,
            formats = formats,
            activeFormat = activeFormat,
        )
    }

    private fun parseFormat(obj: JsonObject): StadiumFormatConfig {
        val fields = setOf("format_name", "battle_type", "clauses", "level_cap", "team_preview", "pokemon_limit",
            "move_timer", "match_timer_minutes", "banned_pokemon", "banned_moves", "banned_abilities", "banned_items",
            "allowed_gimmicks", "allow_held_items", "unique_held_items")
        require(obj.keySet().all { it in fields }) { "Unknown stadium format option" }
        val battleType = optionalString(obj, "battle_type") ?: error("Stadium format requires battle_type")
        require(battleType in setOf("singles", "doubles")) { "Unsupported stadium battle_type '$battleType'" }
        val defaults = StadiumConfiguration.template(battleType == "doubles")
        return defaults.copy(
            formatName = optionalString(obj, "format_name") ?: defaults.formatName,
            clauses = stringSet(obj, "clauses", defaults.clauses),
            levelCap = integer(obj, "level_cap", defaults.levelCap),
            teamPreview = boolean(obj, "team_preview", defaults.teamPreview),
            pokemonLimit = integer(obj, "pokemon_limit", defaults.pokemonLimit),
            moveTimer = integer(obj, "move_timer", defaults.moveTimer),
            matchTimerMinutes = integer(obj, "match_timer_minutes", defaults.matchTimerMinutes),
            bannedPokemon = stringSet(obj, "banned_pokemon", defaults.bannedPokemon),
            bannedMoves = stringSet(obj, "banned_moves", defaults.bannedMoves),
            bannedAbilities = stringSet(obj, "banned_abilities", defaults.bannedAbilities),
            bannedItems = stringSet(obj, "banned_items", defaults.bannedItems),
            allowedGimmicks = stringSet(obj, "allowed_gimmicks", defaults.allowedGimmicks),
            allowHeldItems = boolean(obj, "allow_held_items", defaults.allowHeldItems),
            uniqueHeldItems = boolean(obj, "unique_held_items", defaults.uniqueHeldItems)
        ).also { require(StadiumConfiguration.formatErrors(it).isEmpty()) { "Invalid stadium format options" } }
    }

    private fun optionalString(obj: JsonObject, key: String): String? {
        val value = obj.get(key) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Expected string '$key'" }
        return value.asString
    }

    private fun requiredDouble(obj: JsonObject, key: String): Double {
        val value = obj.get(key) ?: error("Missing number '$key'")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Expected number '$key'" }
        return value.asDouble.also { require(it.isFinite()) { "Number '$key' must be finite" } }
    }

    private fun integer(obj: JsonObject, key: String, default: Int): Int {
        val value = obj.get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Expected integer '$key'" }
        return value.asBigDecimal.toBigIntegerExact().intValueExact()
    }

    private fun boolean(obj: JsonObject, key: String, default: Boolean): Boolean {
        val value = obj.get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Expected boolean '$key'" }
        return value.asBoolean
    }

    private fun stringSet(obj: JsonObject, key: String, default: Set<String>): MutableSet<String> {
        val value = obj.get(key) ?: return default.toMutableSet()
        require(value.isJsonArray && value.asJsonArray.size() <= 256) { "Expected bounded string array '$key'" }
        val entries = value.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected strings in '$key'" }
            it.asString
        }
        require(entries.toSet().size == entries.size) { "Duplicate entries in '$key'" }
        return entries.toMutableSet()
    }

    private fun relativeVector(obj: JsonObject, origin: Vec3) = origin.add(
        requiredDouble(obj, "x"), requiredDouble(obj, "y"), requiredDouble(obj, "z"))

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
                else -> null
            }
            val yaw = obj.get("yaw")?.asFloat
            val pitch = obj.get("pitch")?.asFloat
            require(yaw?.isFinite() != false && pitch?.isFinite() != false) { "Placement '$context[$index]' rotation must be finite" }
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
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Stadium coordinates must be finite" }
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
