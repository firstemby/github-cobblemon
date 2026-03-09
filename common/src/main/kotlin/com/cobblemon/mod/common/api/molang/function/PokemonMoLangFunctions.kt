/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.mark.Marks
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getIntOrNull
import com.cobblemon.mod.common.util.server
import com.cobblemon.mod.common.util.toProperties
import kotlin.random.Random

object PokemonMoLangFunctions: AbstractMoLangFunctionHolder<Pokemon> {
    override val holder: MutableList<(Pokemon) -> MutableMap<String, (MoParams) -> Any>> = mutableListOf(
        { pokemon ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map["id"] = { StringValue(pokemon.uuid.toString()) }
            map["nickname"] = { StringValue(pokemon.nickname.toString()) }
            map["level"] = { DoubleValue(pokemon.level.toDouble()) }
            map["max_hp"] = { DoubleValue(pokemon.maxHealth.toDouble()) }
            map["current_hp"] = { DoubleValue(pokemon.currentHealth.toDouble()) }
            map["friendship"] = { DoubleValue(pokemon.friendship.toDouble()) }
            map["max_fullness"] = { DoubleValue(pokemon.getMaxFullness().toDouble()) }
            map["fullness"] = { DoubleValue(pokemon.currentFullness.toDouble()) }
            map["lose_fullness"] = { params ->
                val amount = params.getDouble(0)
                pokemon.loseFullness(amount.toInt())
            }
            map["feed_pokemon"] = { params ->
                val amount = params.getDouble(0)
                val playSound = params.getBooleanOrNull(1) ?: true
                pokemon.feedPokemon(amount.toInt(), playSound)
            }
            map["behaviour"] = { pokemon.form.behaviour.struct }
            map["behavior"] = { pokemon.form.behaviour.struct } // Inferior
            map["pokeball"] = { StringValue(pokemon.caughtBall.toString()) }
            map["ability"] = { StringValue(pokemon.ability.name) }
            map["has_learned"] = put@{ params ->
                val moveName = params.getString(0)
                val move = pokemon.allAccessibleMoves.find { it.name == moveName }
                if (move != null) {
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["moveset"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, move) in pokemon.moveSet.withIndex()) {
                    struct.addFunction(index.toString()) { move.struct }
                }
                struct
            }
            map["evs"] = {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.evs.getOrDefault(stat).toDouble()) }
                }
                struct
            }
            map["ivs"] = {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.ivs.getOrDefault(stat).toDouble()) }
                }
                struct
            }
            map["hyper_trained_ivs"] = {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.ivs.hyperTrainedIVs[stat] ?: -1.0) }
                }
                struct
            }
            map["ride_boosts"] = {
                val struct = QueryStruct(hashMapOf())
                for (stat in RidingStat.entries) {
                    struct.addFunction(stat.name.lowercase()) { DoubleValue(pokemon.getRideBoost(stat)) }
                }
                struct
            }
            map["natdex_number"] = {
                DoubleValue(pokemon.species.nationalPokedexNumber.toDouble())
            }
            map["types"] = {
                pokemon.form.types.map { it.toString() }.asArrayValue(::StringValue)
            }
            map["gender_ratio"] = {
                DoubleValue(pokemon.form.maleRatio.toDouble())
            }
            map["ev_yield"] = {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.form.evYield[stat]?.toDouble()) }
                }
                struct
            }
            map["base_stats"] = {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.form.baseStats[stat]?.toDouble()) }
                }
                struct
            }
            map["catch_rate"] = {
                DoubleValue(pokemon.form.catchRate.toDouble())
            }
            map["base_experience_yield"] = {
                DoubleValue(pokemon.form.baseExperienceYield.toDouble())
            }
            map["drops"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, drop) in pokemon.form.drops.entries.withIndex()) {
                    struct.addFunction(index.toString()) { drop }
                }
                struct
            }
            map["tm_learnset"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, move) in pokemon.form.moves.tmMoves.withIndex()) {
                    struct.addFunction(index.toString()) { move.struct }
                }
                struct
            }
            map["egg_learnset"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, move) in pokemon.form.moves.eggMoves.withIndex()) {
                    struct.addFunction(index.toString()) { move.struct }
                }
                struct
            }
            map["tutor_learnset"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, move) in pokemon.form.moves.tutorMoves.withIndex()) {
                    struct.addFunction(index.toString()) { move.struct }
                }
                struct
            }
            map["level_learnset"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, move) in pokemon.form.moves.levelUpMoves) {
                    struct.addFunction(index.toString()) { move }
                }
                struct
            }
            map["ability_pool"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, ability) in pokemon.form.abilities.withIndex()) {
                    struct.addFunction(index.toString()) { StringValue(ability.toString()) }
                }
                struct
            }
            map["egg_groups"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, group) in pokemon.form.eggGroups.withIndex()) {
                    struct.addFunction(index.toString()) { StringValue(group.toString()) }
                }
                struct
            }
            map["egg_cycles"] = {
                DoubleValue(pokemon.species.eggCycles.toDouble())
            }
            map["labels"] = {
                val struct = QueryStruct(hashMapOf())
                for ((index, label) in pokemon.form.labels.withIndex()) {
                    struct.addFunction(index.toString()) { StringValue(label) }
                }
                struct
            }
            map["aspects"] = put@{
                val aspects = pokemon.aspects
                return@put aspects.asArrayValue { StringValue(it) }
            }
            map["form_aspects"] = put@{
                val aspects = pokemon.form.aspects
                return@put aspects.asArrayValue { StringValue(it) }
            }
            map["form_name"] = {
                StringValue(pokemon.form.name)
            }
            // Yes, this is the lazy call for a single hardcoded pre-evolution.
            // Lol. Lmao, even.
            // TO-DO: Subscribe to [PokemonSpecies.observable].
            map["prevolution"] = put@{
                val prevolution = pokemon.species.preEvolution ?: return@put DoubleValue.ZERO
                return@put prevolution
            }
            map["nature"] = { StringValue(pokemon.nature.toString()) }
            map["is_wild"] = { DoubleValue(pokemon.entity?.let { it.ownerUUID == null } == true) }
            map["is_shiny"] = { DoubleValue(pokemon.shiny) }
            map["is_in_party"] = { DoubleValue(pokemon.storeCoordinates.get()?.store is PartyStore) }
            map["species"] = { pokemon.species.struct }
            map["form"] = { StringValue(pokemon.form.name) }
            map["weight"] = { DoubleValue(pokemon.species.weight.toDouble()) }
            map["matches"] = { params -> DoubleValue(params.getString(0).toProperties().matches(pokemon)) }
            map["apply"] = { params ->
                params.getString(0).toProperties().apply(pokemon)
                DoubleValue.ONE
            }
            map["owner"] = { pokemon.getOwnerPlayer()?.asMoLangValue() ?: DoubleValue.ZERO }
            map["held_item"] = { pokemon.heldItem().asMoLangValue(server()!!.registryAccess()) }
            map["remove_held_item"] = { _ ->
                pokemon.removeHeldItem()
            }
            map["add_aspects"] = { params ->
                for (aspect in params.params) pokemon.forcedAspects += aspect.asString()
                pokemon.updateAspects()
            }
            map["remove_aspects"] = { params ->
                for (aspect in params.params) pokemon.forcedAspects -= aspect.asString()
                pokemon.updateAspects()
            }
            map["cosmetic_item"] = {
                pokemon.cosmeticItem().asMoLangValue(server()!!.registryAccess())
            }
            map["remove_cosmetic_item"] = { _ ->
                pokemon.removeCosmeticItem()
            }
            map["marks"] = { _ -> pokemon.marks.asArrayValue { StringValue(it.identifier.toString()) } }
            map["has_mark"] = { params ->
                var hasMark = false
                val identifier = params.getString(0).asIdentifierDefaultingNamespace()
                val mark = Marks.getByIdentifier(identifier)
                if (mark != null) {
                    hasMark = pokemon.marks.contains(mark)
                }

                DoubleValue(hasMark)
            }
            map["remove_marks"] = { params ->
                var removedMark = false
                for (param in params.params) {
                    val identifier = param.asString().asIdentifierDefaultingNamespace()
                    val mark = Marks.getByIdentifier(identifier)
                    if (mark != null) {
                        pokemon.exchangeMark(mark, false)
                        removedMark = true
                    }
                }

                DoubleValue(removedMark)
            }
            map["add_marks"] = { params ->
                var appliedMark = false
                for (param in params.params) {
                    val identifier = param.asString().asIdentifierDefaultingNamespace()
                    val mark = Marks.getByIdentifier(identifier)
                    if (mark != null) {
                        pokemon.exchangeMark(mark, true)
                        appliedMark = true
                    }
                }

                DoubleValue(appliedMark)
            }
            map["add_marks_with_chance"] = { params ->
                var appliedMark = false
                for (param in params.params) {
                    val identifier = param.asString().asIdentifierDefaultingNamespace()
                    val mark = Marks.getByIdentifier(identifier)

                    mark?.let {
                        val probability = it.chance.coerceIn(0F, 1F) * 100
                        val randomValue = Random.nextDouble(0.0, 100.0)
                        if (randomValue < probability) {
                            pokemon.exchangeMark(it, true)
                            appliedMark = true
                        }
                    }
                }

                DoubleValue(appliedMark)
            }
            map["add_potential_marks"] = { params ->
                for (param in params.params) {
                    val identifier = param.asString().asIdentifierDefaultingNamespace()
                    val mark = Marks.getByIdentifier(identifier)
                    if (mark != null) pokemon.addPotentialMark(mark)
                }
            }
            map["apply_potential_marks"] = put@{
                return@put DoubleValue(pokemon.applyPotentialMarks())
            }
            map["hyper_train_iv"] = put@{ params ->
                val statId = params.getString(0)
                val stat = Stats.getStat(statId)
                val value = params.getIntOrNull(1) ?: IVs.MAX_VALUE

                if (Stats.PERMANENT.contains(stat)) {
                    pokemon.hyperTrainIV(stat, value)
                    return@put DoubleValue.ONE
                } else {
                    Cobblemon.LOGGER.error("Unknown or non-permanent stat: ${stat.toString()}")
                    return@put DoubleValue.ZERO
                }
            }
            map["add_exp"] = put@{ params ->
                val exp = params.getDouble(0).toInt()
                pokemon.addExperience(SidemodExperienceSource("molang"), exp)
                return@put DoubleValue.ONE
            }
            map["set_iv"] = put@{ params ->
                val statId = params.getString(0)
                val stat = Stats.getStat(statId)
                val value = params.getIntOrNull(1)?.coerceIn(0, IVs.MAX_VALUE) ?: IVs.MAX_VALUE

                if (Stats.PERMANENT.contains(stat)) {
                    pokemon.setIV(stat, value)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["set_ev"] = put@{ params ->
                val statId = params.getString(0)
                val stat = Stats.getStat(statId)
                val value = (params.getIntOrNull(1) ?: 0).coerceIn(0, 255)

                if (Stats.PERMANENT.contains(stat)) {
                    pokemon.setEV(stat, value)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["set_ride_boost"] = put@{ params ->
                val statName = params.getString(0).uppercase()
                val stat = RidingStat.entries.find { it.name.equals(statName, ignoreCase = true) }
                    ?: return@put DoubleValue.ZERO
                val value = params.getDoubleOrNull(1)?.toFloat() ?: return@put DoubleValue.ZERO

                pokemon.setRideBoost(stat, value)
                return@put DoubleValue.ONE
            }
            map["add_ride_boost"] = put@{ params ->
                val statName = params.getString(0).uppercase()
                val stat = RidingStat.entries.find { it.name.equals(statName, ignoreCase = true) }
                    ?: return@put DoubleValue.ZERO
                val value = params.getDoubleOrNull(1)?.toFloat() ?: return@put DoubleValue.ZERO

                return@put DoubleValue(if (pokemon.addRideBoost(stat, value)) 1.0 else 0.0)
            }
            map["initialize_moveset"] = put@{ params ->
                val preferLatest = params.getBooleanOrNull(0) ?: true
                pokemon.initializeMoveset(preferLatest)
                return@put DoubleValue.ONE
            }
            map["validate_moveset"] = put@{ params ->
                val includeLegacy = params.getBooleanOrNull(0) ?: true
                pokemon.validateMoveset(includeLegacy)
                return@put DoubleValue.ONE
            }
            map["teach_learnable_moves"] = put@{ params ->
                val includeLegacy = params.getBooleanOrNull(0) ?: true
                pokemon.teachLearnableMoves(includeLegacy)
                return@put DoubleValue.ONE
            }
            map["teach_move"] = put@{ params ->
                val moveName = params.getString(0)
                val moveTemplate = Moves.getByName(moveName) ?: return@put DoubleValue.ZERO
                val bypass = params.getBooleanOrNull(1) ?: false

                val canLearn = bypass || LearnsetQuery.ANY.canLearn(moveTemplate, pokemon.form.moves)
                if (!canLearn) {
                    return@put DoubleValue.ZERO
                }

                val alreadyKnows = pokemon.moveSet.getMoves().any { it.template == moveTemplate } ||
                    pokemon.benchedMoves.any { it.moveTemplate == moveTemplate }
                if (alreadyKnows) {
                    return@put DoubleValue.ZERO
                }

                if (pokemon.moveSet.hasSpace()) {
                    pokemon.moveSet.add(moveTemplate.create())
                } else {
                    pokemon.benchedMoves.add(BenchedMove(moveTemplate, 0))
                }

                DoubleValue.ONE
            }
            map["can_learn_move"] = put@{ params ->
                val moveName = params.getString(0)
                val moveTemplate = Moves.getByName(moveName) ?: return@put DoubleValue.ZERO
                val includeLegacy = params.getBooleanOrNull(1) ?: true

                val canLearn = if (includeLegacy) {
                    LearnsetQuery.ANY.canLearn(moveTemplate, pokemon.form.moves)
                } else {
                    LearnsetQuery.LEGAL.canLearn(moveTemplate, pokemon.form.moves)
                }
                return@put DoubleValue(if (canLearn) 1.0 else 0.0)
            }
            map["unlearn_move"] = put@{ params ->
                val moveName = params.getString(0)
                val moveTemplate = Moves.getByName(moveName)
                if (moveTemplate != null) {
                    pokemon.unlearnMove(moveTemplate)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["can_evolve"] = { _ ->
                DoubleValue(pokemon.evolutions.any())
            }
            map["force_evolve"] = put@{ params ->
                val idx = params.getInt(0)
                val evolution = if (idx >= 0) pokemon.evolutions.elementAtOrNull(idx) else return@put DoubleValue.ZERO
                evolution?.forceEvolve(pokemon)
                StringValue(evolution.toString())
            }
            map
        }
    )
}
