/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.google.gson.Gson
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object StadiumShowdownProtocol {
    const val VERSION = 1
    private val gson = Gson()
    private val errorCodes = setOf(
        "malformed_request", "unsupported_protocol", "unsupported_field", "malformed_format",
        "unsupported_rule", "unsupported_game_type", "unknown_id", "malformed_sides",
        "malformed_side", "malformed_team", "party_size", "duplicate_uuid",
        "fainted_starter", "banned_pokemon", "banned_move", "banned_ability",
        "banned_item", "species_clause", "maximum_level", "held_items_disabled",
        "unique_held_items", "insufficient_healthy", "validation_failed", "battle_failed",
        "battle_unavailable", "malformed_choice", "stale_choice", "invalid_choice",
        "malformed_timeout", "stale_timeout", "invalid_battle_id", "battle_exists",
        "startup_failed", "invalid_response", "unsupported_engine", "engine_unavailable",
        "invalid_party",
    )
    private fun fieldLabel(field: String) = lang("stadium.engine.field.${when {
        field.startsWith("format.") -> "format"
        field.startsWith("sides.") -> "sides"
        else -> when (field) {
            "request" -> "request"
            "protocolVersion" -> "protocol_version"
            "battle" -> "battle"
            "battleId" -> "battle_id"
            "scope" -> "scope"
            "expiredSides" -> "expired_sides"
            "choice" -> "choice"
            "rqid" -> "request_id"
            "sides" -> "sides"
            "name" -> "name"
            "team" -> "team"
            "uuid" -> "pokemon_uuid"
            "status" -> "status"
            "nature" -> "nature"
            "hpType" -> "hidden_power_type"
            "teraType" -> "tera_type"
            "currentHealth" -> "current_health"
            "movesInfo" -> "move_data"
            "pokemon" -> "pokemon"
            "moves" -> "moves"
            "abilities" -> "abilities"
            "items" -> "items"
            "level" -> "level"
            else -> "unknown"
        }
    }}")
    data class Side(val id: String, val name: String, val team: String)
    data class Error(val code: String, val field: String, val message: String, val side: String? = null,
        val pokemonUuid: String? = null, val value: String? = null) {
        val userMessage: Component get() {
            var text: Component = lang("stadium.engine.${code.takeIf { it in errorCodes } ?: "rejected"}")
            if (value != null) text = lang("stadium.engine.value", text, value)
            if (this.field.isNotEmpty()) text = lang("stadium.engine.field", fieldLabel(this.field), text)
            if (side != null) text = lang("stadium.engine.side", side.removePrefix("p"), text)
            return text
        }
    }
    data class Result(val ok: Boolean, val errors: List<Error> = emptyList()) {
        val userMessage: Component get() = errors.firstOrNull()?.userMessage ?: lang("stadium.engine.rejected")
        val summary get() = errors.firstOrNull()?.let { "${it.side?.let { side -> "$side: " }.orEmpty()}${it.message}" } ?: "Showdown rejected this stadium battle."
    }
    data class Capabilities(val features: Set<String> = emptySet(), val gameTypes: Set<String> = emptySet(), val clauses: Set<String> = emptySet()) {
        fun supports(config: StadiumFormatConfig) = features.containsAll(setOf("team-validation", "structured-startup", "mechanic-restrictions")) &&
            (!config.teamPreview || "team-preview" in features) &&
            ((config.moveTimer == -1 && config.matchTimerMinutes == -1) || "timeout-resolution" in features) &&
            config.battleTypeName in gameTypes && clauses.containsAll(config.clauses)
    }

    fun failure(code: String, message: String) = Result(false, listOf(Error(code, "engine", message)))

    fun result(json: String): Result = runCatching {
        require(json.length <= 131072)
        val root = JsonParser.parseString(json).asJsonObject
        require(root["protocolVersion"].toString() == VERSION.toString() && root["ok"].asJsonPrimitive.isBoolean)
        val errors = root.getAsJsonArray("errors").map { element ->
            val error = element.asJsonObject
            Error(error["code"].asString.take(80), error["field"].asString.take(80), error["message"].asString.take(256),
                error.get("side")?.takeUnless { it.isJsonNull }?.asString?.take(8),
                error.get("pokemonUuid")?.takeUnless { it.isJsonNull }?.asString?.take(36),
                error.get("value")?.takeUnless { it.isJsonNull }?.asString?.take(80))
        }
        val ok = root["ok"].asBoolean
        require((ok && errors.isEmpty()) || (!ok && errors.isNotEmpty()))
        Result(ok, errors)
    }.getOrElse { failure("invalid_response", "Showdown returned an invalid stadium response.") }

    fun capabilities(json: String): Capabilities = runCatching {
        require(json.length <= 16384)
        val root = JsonParser.parseString(json).asJsonObject
        require(root["protocolVersion"].toString() == VERSION.toString())
        fun values(name: String) = root.getAsJsonArray(name).map { it.asString }.toSet()
        Capabilities(values("features"), values("gameTypes"), values("clauses"))
    }.getOrDefault(Capabilities())

    fun request(config: StadiumFormatConfig, sides: List<Side>): String {
        val format = JsonObject().apply {
            addProperty("id", "stadium_local")
            addProperty("gameType", config.battleTypeName)
            addProperty("maxPartySize", config.pokemonLimit)
            addProperty("maxLevel", config.levelCap.coerceAtLeast(0))
            addProperty("levelPolicy", "reject")
            addProperty("teamPreview", config.teamPreview)
            addProperty("allowHeldItems", config.allowHeldItems)
            addProperty("uniqueHeldItems", config.uniqueHeldItems)
            add("allowedMechanics", gson.toJsonTree(config.allowedGimmicks))
            add("clauses", gson.toJsonTree(config.clauses))
            add("bans", gson.toJsonTree(mapOf("pokemon" to config.bannedPokemon, "moves" to config.bannedMoves,
                "abilities" to config.bannedAbilities, "items" to config.bannedItems).mapValues { (_, ids) ->
                    ids.map(StadiumConfiguration::normalizeId).distinct()
                }))
        }
        return gson.toJson(JsonObject().apply {
            addProperty("protocolVersion", VERSION)
            add("format", format)
            add("sides", gson.toJsonTree(sides))
        })
    }

    fun validate(service: ShowdownService, config: StadiumFormatConfig, request: String): Result {
        if ((StadiumConfiguration.formatErrors(config) + StadiumConfiguration.engineRequirements(config)).isNotEmpty()) {
            return failure("unsupported_rule", "This format contains invalid or unsupported stadium settings.")
        }
        return runCatching {
            if (!service.getStadiumCapabilities().supports(config)) failure("unsupported_engine", "The connected Showdown engine does not support this stadium format.")
            else service.validateStadiumBattle(request)
        }.getOrElse { failure("engine_unavailable", "The stadium engine is unavailable. No battle was started.") }
    }
}
