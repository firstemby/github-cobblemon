/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.util.Locale

object StadiumConfiguration {
    const val MAX_FORMATS = 16
    const val MAX_DISTANCE = 96.0
    val layoutIds = listOf("singles", "doubles", "custom")
    const val MAX_LAYOUT_SLOTS = 5
    val gimmicks = setOf("mega_evolution", "z_moves", "dynamax", "terastallization")
    val clauses = setOf("species_clause", "sleep_clause", "evasion_clause", "ohko_clause", "moody_clause", "endless_battle_clause")

    fun template(doubles: Boolean = false): StadiumFormatConfig = StadiumFormatConfig(
        formatName = "",
        battleTypeName = if (doubles) "doubles" else "singles",
        clauses = mutableSetOf(), levelCap = -1, teamPreview = false, pokemonLimit = 6,
        moveTimer = -1, matchTimerMinutes = -1,
        bannedPokemon = mutableSetOf(), bannedMoves = mutableSetOf(), bannedAbilities = mutableSetOf(),
        bannedItems = mutableSetOf(), allowedGimmicks = gimmicks.toMutableSet()
    )

    fun copy(config: StadiumFormatConfig) = config.copy(
        clauses = config.clauses.toMutableSet(), bannedPokemon = config.bannedPokemon.toMutableSet(),
        bannedMoves = config.bannedMoves.toMutableSet(), bannedAbilities = config.bannedAbilities.toMutableSet(),
        bannedItems = config.bannedItems.toMutableSet(), allowedGimmicks = config.allowedGimmicks.toMutableSet()
    )

    fun errors(data: StadiumBlockData, origin: Vec3): List<Component> = buildList {
        if ((data.name.isNotEmpty() && data.name.isBlank()) || data.name.length > 48) add(lang("stadium.validation.name"))
        if (data.formatConfigs.size > MAX_FORMATS) add(lang("stadium.validation.formats_limit", MAX_FORMATS))
        if (data.formatConfigs.keys.any { !it.matches(Regex("[a-z0-9_]{1,40}")) }) add(lang("stadium.validation.format_id"))
        data.formatConfigs.values.forEach { addAll(formatErrors(it)) }
        if (data.activeFormat.isNotEmpty() && data.activeFormat !in data.formatConfigs) add(lang("stadium.validation.active_format"))
        if (data.spectatorAreas.size > StadiumSpectatorArea.MAX_AREAS || data.spectatorAreas.any { !it.valid(origin) }) {
            add(lang("stadium.validation.bleachers"))
        }
        if (data.arena?.valid(origin) == false) add(lang("stadium.validation.arena"))
        if (data.additionalLayouts.keys.any { it != "custom" } || data.cinematicLayout !in layoutIds) add(lang("stadium.validation.layout_id"))
        layoutIds.forEach { id ->
            val layout = data.layout(id) ?: return@forEach
            if (layout.trainerSlots !in 1..MAX_LAYOUT_SLOTS || layout.pokemonSlots !in 1..MAX_LAYOUT_SLOTS) add(lang("stadium.validation.slots"))
            listOf(layout.side1, layout.side2).forEach { side ->
                if (side.actors.size > data.slots(id, "actors") || side.pokemon.size > data.slots(id, "pokemon")) add(lang("stadium.validation.excess_placements", id))
                (side.actors + side.pokemon).forEach { placement ->
                    if (data.arena?.contains(placement.position) == false) add(lang("stadium.validation.outside_arena"))
                    if (!validPosition(placement.position, origin) ||
                        placement.lookAt?.let { !validPosition(it, origin) } == true ||
                        placement.yaw?.isFinite() == false || placement.pitch?.isFinite() == false) {
                        add(lang("stadium.validation.placement", id))
                    }
                }
            }
        }
        if (!validPosition(data.cameraOriginOffset, Vec3.ZERO)) add(lang("stadium.validation.camera_offset", MAX_DISTANCE))
    }.distinct()

    fun formatErrors(config: StadiumFormatConfig): List<Component> = buildList {
        if ((config.formatName.isNotEmpty() && config.formatName.isBlank()) || config.formatName.length > 40) add(lang("stadium.validation.format_name"))
        if (config.battleTypeName !in setOf("singles", "doubles")) add(lang("stadium.validation.battle_type"))
        val minimum = if (config.battleTypeName == "doubles") 2 else 1
        if (config.pokemonLimit !in minimum..6) add(lang("stadium.validation.party_limit", minimum))
        if (config.levelCap != -1 && config.levelCap !in 1..100) add(lang("stadium.validation.level_cap"))
        if (config.moveTimer != -1 && config.moveTimer !in 10..600) add(lang("stadium.validation.turn_timer"))
        if (config.matchTimerMinutes != -1 && config.matchTimerMinutes !in 1..180) add(lang("stadium.validation.match_timer"))
        if (!clauses.containsAll(config.clauses)) add(lang("stadium.validation.clause"))
        if (!gimmicks.containsAll(config.allowedGimmicks)) add(lang("stadium.validation.mechanic"))
        listOf(config.bannedPokemon, config.bannedMoves, config.bannedAbilities, config.bannedItems).forEach { entries ->
            if (entries.size > 256 || entries.any { !it.matches(Regex("[a-z0-9_-]{1,80}")) }) add(lang("stadium.validation.ban_list"))
        }
    }

    fun engineRequirements(config: StadiumFormatConfig): List<Component> = buildList {
        if (config.clauses.any { it != "species_clause" }) add(lang("stadium.validation.simulation_required"))
    }

    fun readiness(data: StadiumBlockData): List<Component> = buildList {
        val format = data.formatConfigs[data.activeFormat]
        if (format == null) {
            add(lang("stadium.validation.choose_format"))
            return@buildList
        }
        val layout = if (format.battleTypeName == "doubles") data.doublesLayout else data.singlesLayout
        val slots = if (format.battleTypeName == "doubles") 2 else 1
        if (layout == null || listOf(layout.side1, layout.side2).any { it.actors.size != 1 || it.pokemon.size != slots }) {
            add(lang("stadium.validation.place_actors", slots, format.battleTypeName))
        }
        if (layout != null) {
            val placements = layout.side1.actors + layout.side1.pokemon + layout.side2.actors + layout.side2.pokemon
            if (placements.indices.any { a -> (a + 1 until placements.size).any { b -> placements[a].position.distanceToSqr(placements[b].position) < 0.25 } }) {
                add(lang("stadium.validation.overlapping_placements"))
            }
        }
        addAll(engineRequirements(format))
    }

    fun validPosition(position: Vec3, origin: Vec3): Boolean =
        position.x.isFinite() && position.y.isFinite() && position.z.isFinite() &&
            position.distanceToSqr(origin) <= MAX_DISTANCE * MAX_DISTANCE

    fun normalizeId(value: String): String = value.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }

    fun toBattleLayout(layout: StadiumBlockLayout, origin: Vec3): StadiumBattleLayout {
        fun side(source: StadiumBlockSide): StadiumBattleSide {
            fun placement(value: StadiumBlockPlacement) = StadiumPlacement(value.position.subtract(origin), value.lookAt?.subtract(origin), value.yaw, value.pitch)
            return StadiumBattleSide(source.actors.map(::placement), source.pokemon.map(::placement))
        }
        return StadiumBattleLayout(side(layout.side1), side(layout.side2))
    }
}
