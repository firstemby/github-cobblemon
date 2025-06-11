/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.breeding

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.abilities.Ability
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.pokemon.*
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.ResourceLocation

class EggPokemon(
    val ivs: IVs,
    val nature: Nature,
    val species: Species,
    val formData: FormData,
    val ability: Ability,
    val moveSet: MoveSet,
    val isShiny: Boolean,
    val gender: Gender,
    val pokeball: PokeBall
) {
    fun generatePokemon(): Pokemon {
        val result = Pokemon()
        ivs.forEach {
            result.setIV(it.key, it.value)
        }
        result.species = species
        result.gender = gender
        result.nature = nature
        result.moveSet.clear()
        result.moveSet.copyFrom(moveSet)
        result.shiny = isShiny
        result.caughtBall = pokeball
        result.form = formData
        result.ability = ability
        return result

    }
    fun toNbt(): CompoundTag {
        val result = CompoundTag()
        val ivNbt = IVs.CODEC.encodeStart(NbtOps.INSTANCE, ivs)
            .result()
            .orElseThrow { IllegalStateException("Failed to encode IVs") }

        result.put(DataKeys.POKEMON_IVS, ivNbt)
        result.putString(DataKeys.POKEMON_NATURE, nature.name.toString())
        result.putString(DataKeys.POKEMON_SPECIES_IDENTIFIER, species.resourceIdentifier.toString())
        result.putString(DataKeys.POKEMON_FORM_ID, formData.formOnlyShowdownId())
        val abilityNBT = ability.saveToNBT(CompoundTag())
        result.put(DataKeys.POKEMON_ABILITY, abilityNBT)
        result.put(DataKeys.POKEMON_MOVESET, moveSet.getNBT())
        result.putBoolean(DataKeys.POKEMON_SHINY, isShiny)
        result.putString(DataKeys.POKEMON_GENDER, gender.name)
        result.putString(DataKeys.POKEMON_CAUGHT_BALL, pokeball.name.toString())
        return result
    }

    companion object {
        // I heard you like casts so I put some casts in your casts
        fun fromNBT(nbt: CompoundTag): EggPokemon {
            val species = PokemonSpecies.getByIdentifier(ResourceLocation.tryParse(nbt.getString(DataKeys.POKEMON_SPECIES_IDENTIFIER))!!)!!
            val abilityNBT = nbt.getCompound(DataKeys.POKEMON_ABILITY) ?: CompoundTag()
            val abilityName = abilityNBT.getString(DataKeys.POKEMON_ABILITY_NAME).takeIf { it.isNotEmpty() } ?: "runaway"
            val moveSet = MoveSet()
            val pokeball = nbt.getString(DataKeys.POKEMON_CAUGHT_BALL)
            val ivs = IVs.CODEC.decode(NbtOps.INSTANCE, nbt.get(DataKeys.POKEMON_IVS))
                .result()
                ?.get()
                ?.first
                ?: throw IllegalStateException("Failed to decode IVs")

            return EggPokemon(
                ivs,
                Natures.getNature(ResourceLocation.tryParse(nbt.getString(DataKeys.POKEMON_NATURE))!!)!!,
                species,
                species.forms.firstOrNull { it.formOnlyShowdownId() == nbt.getString(DataKeys.POKEMON_FORM_ID) } ?: species.standardForm,
                Abilities.getOrException(abilityName).create(abilityNBT),
                moveSet.loadFromNBT(nbt),
                nbt.getBoolean(DataKeys.POKEMON_SHINY),
                Gender.valueOf(nbt.getString(DataKeys.POKEMON_GENDER)),
                PokeBalls.getPokeBall(ResourceLocation.tryParse(pokeball)!!) ?: PokeBalls.POKE_BALL
            )
        }

    }
}
