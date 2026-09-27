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
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMovesetBuilders
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
import net.minecraft.resources.ResourceLocation
import kotlin.random.Random

object PokemonMoLangFunctions : AbstractMoLangFunctionHolder<Pokemon>() {
    override fun Pokemon.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val pokemon = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["id"] = { StringValue(pokemon.uuid.toString()) }
        functions["nickname"] = { StringValue(pokemon.nickname.toString()) }
        functions["level"] = { DoubleValue(pokemon.level.toDouble()) }
        functions["max_hp"] = { DoubleValue(pokemon.maxHealth.toDouble()) }
        functions["current_hp"] = { DoubleValue(pokemon.currentHealth.toDouble()) }
        functions["friendship"] = { DoubleValue(pokemon.friendship.toDouble()) }
        functions["max_fullness"] = { DoubleValue(pokemon.getMaxFullness().toDouble()) }
        functions["fullness"] = { DoubleValue(pokemon.currentFullness.toDouble()) }
        functions["lose_fullness"] = { params ->
            val amount = params.getDouble(0)
            pokemon.loseFullness(amount.toInt())
        }
        functions["feed_pokemon"] = { params ->
            val amount = params.getDouble(0)
            val playSound = params.getBooleanOrNull(1) ?: true
            pokemon.feedPokemon(amount.toInt(), playSound)
        }
        functions["behaviour"] = { pokemon.form.behaviour.struct }
        functions["behavior"] = { pokemon.form.behaviour.struct } // Inferior
        functions["pokeball"] = { StringValue(pokemon.caughtBall.toString()) }
        functions["ability"] = { StringValue(pokemon.ability.name) }
        functions["has_learned"] = hasLearned@{ params ->
            val moveName = params.getString(0)
            val move = pokemon.allAccessibleMoves.find { it.name == moveName }
            if (move != null) return@hasLearned DoubleValue.ONE
            DoubleValue.ZERO
        }
        functions["moveset"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, move) in pokemon.moveSet.withIndex()) {
                struct.addFunction(index.toString()) { move.struct }
            }
            struct
        }
        functions["evs"] = {
            val struct = QueryStruct(hashMapOf())
            for (stat in Stats.PERMANENT) {
                struct.addFunction(stat.showdownId) { DoubleValue(pokemon.evs.getOrDefault(stat).toDouble()) }
            }
            struct
        }
        functions["ivs"] = {
            val struct = QueryStruct(hashMapOf())
            for (stat in Stats.PERMANENT) {
                struct.addFunction(stat.showdownId) { DoubleValue(pokemon.ivs.getOrDefault(stat).toDouble()) }
            }
            struct
        }
        functions["hyper_trained_ivs"] = {
            val struct = QueryStruct(hashMapOf())
            for (stat in Stats.PERMANENT) {
                struct.addFunction(stat.showdownId) { DoubleValue(pokemon.ivs.hyperTrainedIVs[stat] ?: -1.0) }
            }
            struct
        }
        functions["ride_boosts"] = {
            val struct = QueryStruct(hashMapOf())
            for (stat in RidingStat.entries) {
                struct.addFunction(stat.name.lowercase()) { DoubleValue(pokemon.getRideBoost(stat)) }
            }
            struct
        }
        functions["natdex_number"] = {
            DoubleValue(pokemon.species.nationalPokedexNumber.toDouble())
        }
        functions["types"] = {
            pokemon.form.types
                .map { it.showdownId }
                .asArrayValue(::StringValue)
        }
        functions["gender_ratio"] = {
            DoubleValue(pokemon.form.maleRatio.toDouble())
        }
        functions["ev_yield"] = {
            val struct = QueryStruct(hashMapOf())
            for (stat in Stats.PERMANENT) {
                struct.addFunction(stat.showdownId) { DoubleValue(pokemon.form.evYield[stat]?.toDouble()) }
            }
            struct
        }
        functions["base_stats"] = {
            val struct = QueryStruct(hashMapOf())
            for (stat in Stats.PERMANENT) {
                struct.addFunction(stat.showdownId) { DoubleValue(pokemon.form.baseStats[stat]?.toDouble()) }
            }
            struct
        }
        functions["catch_rate"] = {
            DoubleValue(pokemon.form.catchRate.toDouble())
        }
        functions["base_experience_yield"] = {
            DoubleValue(pokemon.form.baseExperienceYield.toDouble())
        }
        functions["drops"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, drop) in pokemon.form.drops.entries
                .withIndex()) {
                struct.addFunction(index.toString()) { drop }
            }
            struct
        }
        functions["tm_learnset"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, move) in pokemon.form.moves.tmMoves
                .withIndex()) {
                struct.addFunction(index.toString()) { move.struct }
            }
            struct
        }
        functions["egg_learnset"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, move) in pokemon.form.moves.eggMoves
                .withIndex()) {
                struct.addFunction(index.toString()) { move.struct }
            }
            struct
        }
        functions["tutor_learnset"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, move) in pokemon.form.moves.tutorMoves
                .withIndex()) {
                struct.addFunction(index.toString()) { move.struct }
            }
            struct
        }
        functions["level_learnset"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, move) in pokemon.form.moves.levelUpMoves) {
                struct.addFunction(index.toString()) { move }
            }
            struct
        }
        functions["ability_pool"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, ability) in pokemon.form.abilities.withIndex()) {
                struct.addFunction(index.toString()) { StringValue(ability.toString()) }
            }
            struct
        }
        functions["egg_groups"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, group) in pokemon.form.eggGroups.withIndex()) {
                struct.addFunction(index.toString()) { StringValue(group.toString()) }
            }
            struct
        }
        functions["egg_cycles"] = {
            DoubleValue(pokemon.species.eggCycles.toDouble())
        }
        functions["labels"] = {
            val struct = QueryStruct(hashMapOf())
            for ((index, label) in pokemon.form.labels.withIndex()) {
                struct.addFunction(index.toString()) { StringValue(label) }
            }
            struct
        }
        functions["aspects"] = {
            val aspects = pokemon.aspects
            aspects.asArrayValue { StringValue(it) }
        }
        functions["form_aspects"] = {
            val aspects = pokemon.form.aspects
            aspects.asArrayValue { StringValue(it) }
        }
        functions["form_name"] = {
            StringValue(pokemon.form.name)
        }
        // Yes, this is the lazy call for a single hardcoded pre-evolution.
        // Lol. Lmao, even.
        // TO-DO: Subscribe to [PokemonSpecies.observable].
        functions["pre_evolution"] = { pokemon.species.preEvolution ?: DoubleValue.ZERO }
        functions["nature"] = { StringValue(pokemon.nature.toString()) }
        functions["is_wild"] = { DoubleValue(pokemon.entity?.let { it.ownerUUID == null } == true) }
        functions["is_shiny"] = { DoubleValue(pokemon.shiny) }
        functions["is_in_party"] = { DoubleValue(pokemon.storeCoordinates.get()?.store is PartyStore) }
        functions["species"] = { pokemon.species.struct }
        functions["form"] = { StringValue(pokemon.form.name) }
        functions["weight"] = { DoubleValue(pokemon.species.weight.toDouble()) }
        functions["matches"] = { params -> DoubleValue(params.getString(0).toProperties().matches(pokemon)) }
        functions["apply"] = { params ->
            params.getString(0).toProperties().apply(pokemon)
            DoubleValue.ONE
        }
        functions["owner"] = { pokemon.getOwnerPlayer()?.asMoLangValue() ?: DoubleValue.ZERO }
        functions["held_item"] = { pokemon.heldItem().asMoLangValue(server()!!.registryAccess()) }
        functions["remove_held_item"] = { pokemon.removeHeldItem() }
        functions["add_aspects"] = { params ->
            for (aspect in params.params) pokemon.forcedAspects += aspect.asString()
            pokemon.updateAspects()
        }
        functions["remove_aspects"] = { params ->
            for (aspect in params.params) pokemon.forcedAspects -= aspect.asString()
            pokemon.updateAspects()
        }
        functions["cosmetic_item"] = {
            pokemon.cosmeticItem().asMoLangValue(server()!!.registryAccess())
        }
        functions["remove_cosmetic_item"] = { pokemon.removeCosmeticItem() }
        functions["marks"] = { pokemon.marks.asArrayValue { StringValue(it.identifier.toString()) } }
        functions["has_mark"] = { params ->
            var hasMark = false
            val identifier = params.getString(0).asIdentifierDefaultingNamespace()
            val mark = Marks.getByIdentifier(identifier)
            if (mark != null) {
                hasMark = pokemon.marks.contains(mark)
            }

            DoubleValue(hasMark)
        }
        functions["remove_marks"] = { params ->
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
        functions["add_marks"] = { params ->
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
        functions["add_marks_with_chance"] = { params ->
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
        functions["add_potential_marks"] = { params ->
            for (param in params.params) {
                val identifier = param.asString().asIdentifierDefaultingNamespace()
                val mark = Marks.getByIdentifier(identifier)
                if (mark != null) pokemon.addPotentialMark(mark)
            }
        }
        functions["apply_potential_marks"] = { DoubleValue(pokemon.applyPotentialMarks()) }
        functions["hyper_train_iv"] = hyperTrainIv@{ params ->
            val statId = params.getString(0)
            val stat = Stats.getStat(statId)
            val value = params.getIntOrNull(1) ?: IVs.MAX_VALUE

            if (Stats.PERMANENT.contains(stat)) {
                pokemon.hyperTrainIV(stat, value)
                return@hyperTrainIv DoubleValue.ONE
            } else {
                Cobblemon.LOGGER.error("Unknown or non-permanent stat: $stat")
                return@hyperTrainIv DoubleValue.ZERO
            }
        }
        functions["add_exp"] = { params ->
            val exp = params.getDouble(0).toInt()
            pokemon.addExperience(SidemodExperienceSource("molang"), exp)
            DoubleValue.ONE
        }
        functions["set_iv"] = setIv@{ params ->
            val statId = params.getString(0)
            val stat = Stats.getStat(statId)
            val value = params.getIntOrNull(1)?.coerceIn(0, IVs.MAX_VALUE) ?: IVs.MAX_VALUE

            if (Stats.PERMANENT.contains(stat)) {
                pokemon.setIV(stat, value)
                return@setIv DoubleValue.ONE
            } else {
                return@setIv DoubleValue.ZERO
            }
        }
        functions["set_ev"] = setEv@{ params ->
            val statId = params.getString(0)
            val stat = Stats.getStat(statId)
            val value = (params.getIntOrNull(1) ?: 0).coerceIn(0, 255)

            if (Stats.PERMANENT.contains(stat)) {
                pokemon.setEV(stat, value)
                return@setEv DoubleValue.ONE
            } else {
                return@setEv DoubleValue.ZERO
            }
        }
        functions["set_ride_boost"] = setRideBoost@{ params ->
            val statName = params.getString(0).uppercase()
            val stat =
                RidingStat.entries.find { it.name.equals(statName, ignoreCase = true) }
                    ?: return@setRideBoost DoubleValue.ZERO
            val value = params.getDoubleOrNull(1)?.toFloat() ?: return@setRideBoost DoubleValue.ZERO

            pokemon.setRideBoost(stat, value)
            DoubleValue.ONE
        }
        functions["add_ride_boost"] = addRideBoost@{ params ->
            val statName = params.getString(0).uppercase()
            val stat =
                RidingStat.entries.find { it.name.equals(statName, ignoreCase = true) }
                    ?: return@addRideBoost DoubleValue.ZERO
            val value = params.getDoubleOrNull(1)?.toFloat() ?: return@addRideBoost DoubleValue.ZERO

            DoubleValue(if (pokemon.addRideBoost(stat, value)) 1.0 else 0.0)
        }
        functions["initialize_moveset"] = initializeMoveset@{ params ->
            val param = params.get<MoValue>(0) ?: StringValue(pokemon.form.defaultWildMovesetBuilder.toString())
            if (param is DoubleValue) {
                pokemon.initializeMoveset(param == DoubleValue.ONE)
            } else {
                val movesetBuilderId = ResourceLocation.parse(param.asString())
                val movesetBuilder =
                    CobblemonMovesetBuilders.movesetBuilders[movesetBuilderId]
                        ?: run {
                            Cobblemon.LOGGER.error(
                                "Tried initializing moveset from moveset builder ${param.asString()} but it does not exist.",
                            )
                            return@initializeMoveset DoubleValue.ZERO
                        }
                pokemon.initializeMovesetFrom(movesetBuilder)
            }
            DoubleValue.ONE
        }
        functions["validate_moveset"] = { params ->
            val includeLegacy = params.getBooleanOrNull(0) ?: true
            pokemon.validateMoveset(includeLegacy)
            DoubleValue.ONE
        }
        functions["teach_learnable_moves"] = { params ->
            val includeLegacy = params.getBooleanOrNull(0) ?: true
            pokemon.teachLearnableMoves(includeLegacy)
            DoubleValue.ONE
        }
        functions["teach_move"] = teachMove@{ params ->
            val moveName = params.getString(0)
            val moveTemplate = Moves.getByName(moveName) ?: return@teachMove DoubleValue.ZERO
            val bypass = params.getBooleanOrNull(1) ?: false

            val canLearn = bypass || LearnsetQuery.ANY.canLearn(moveTemplate, pokemon.form.moves)
            if (!canLearn) {
                return@teachMove DoubleValue.ZERO
            }

            val alreadyKnows =
                pokemon.moveSet.getMoves().any { it.template == moveTemplate } ||
                    pokemon.benchedMoves.any { it.moveTemplate == moveTemplate }
            if (alreadyKnows) {
                return@teachMove DoubleValue.ZERO
            }

            if (pokemon.moveSet.hasSpace()) {
                pokemon.moveSet.add(moveTemplate.create())
            } else {
                pokemon.benchedMoves.add(BenchedMove(moveTemplate, 0))
            }

            DoubleValue.ONE
        }
        functions["can_learn_move"] = canLearnMove@{ params ->
            val moveName = params.getString(0)
            val moveTemplate = Moves.getByName(moveName) ?: return@canLearnMove DoubleValue.ZERO
            val includeLegacy = params.getBooleanOrNull(1) ?: true

            val canLearn =
                if (includeLegacy) {
                    LearnsetQuery.ANY.canLearn(moveTemplate, pokemon.form.moves)
                } else {
                    LearnsetQuery.LEGAL.canLearn(moveTemplate, pokemon.form.moves)
                }

            DoubleValue(if (canLearn) 1.0 else 0.0)
        }
        functions["unlearn_move"] = unlearnMove@{ params ->
            val moveName = params.getString(0)
            val moveTemplate = Moves.getByName(moveName)
            if (moveTemplate != null) {
                pokemon.unlearnMove(moveTemplate)
                return@unlearnMove DoubleValue.ONE
            }

            DoubleValue.ZERO
        }
        functions["can_evolve"] = { DoubleValue(pokemon.evolutions.any()) }
        functions["force_evolve"] = forceEvolve@{ params ->
            val idx = params.getInt(0)
            val evolution =
                if (idx >= 0) pokemon.evolutions.elementAtOrNull(idx) else return@forceEvolve DoubleValue.ZERO
            evolution?.forceEvolve(pokemon)
            StringValue(evolution.toString())
        }

        functions["entity"] = { pokemon.entity?.asMoLangValue() ?: DoubleValue.ZERO }
        functions["is_alpha"] = { DoubleValue(pokemon.isAlpha) }
        functions["size_category"] = { StringValue(pokemon.getSizeCategory().name) }

        return functions
    }
}
